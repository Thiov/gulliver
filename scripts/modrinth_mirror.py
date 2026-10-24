#!/usr/bin/env python3
"""Mirror CurseForge projects to Modrinth.

For every project in mirror/projects.json: create the Modrinth project as a
DRAFT if it doesn't exist yet (title, summary, README as description, icon
from the newest jar, license, links), then upload the newest CurseForge file
of every (loader, Minecraft version) pair that Modrinth doesn't have yet.
Drafts stay private until you press "Submit for review" on Modrinth.

Usage: modrinth_mirror.py [--manifest mirror/projects.json] [--only <slug>] [--dry-run]
Needs MODRINTH_TOKEN in the environment (scopes: create/write projects and
versions). CurseForge is only read through its public website API.
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from publish import MR_API, UA, multipart, request  # noqa: E402

CF_WEB = "https://www.curseforge.com/api/v1/mods"
CF_PAGE = "https://www.curseforge.com/minecraft/mc-mods"
LOADERS = {"Fabric": "fabric", "NeoForge": "neoforge", "Forge": "forge", "Quilt": "quilt"}
MC_RE = re.compile(r"^\d+\.\d+(\.\d+)?$")
CUT_RE = re.compile(r"^#{1,3}\s*(build|building|development|compiling|for developers)\b", re.I | re.M)
RELEASE_TYPES = {1: "release", 2: "beta", 3: "alpha"}
ICON_LIMIT = 256 * 1024


def get_bytes(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (" + UA + ")"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def mc_key(v):
    return [int(p) for p in v.split(".")]


def curseforge_files(project_id):
    files, page = [], 0
    while True:
        data = json.loads(get_bytes(f"{CF_WEB}/{project_id}/files?pageIndex={page}&pageSize=50"
                                    f"&sort=dateCreated&sortDescending=true"))
        files += data["data"]
        if len(files) >= data["pagination"]["totalCount"] or not data["data"]:
            return files
        page += 1


def select_files(project, files):
    """Newest approved file per (loaders, Minecraft versions) pair."""
    newest = {}
    for f in files:
        if f.get("status") != 4 or any(re.search(p, f["fileName"]) for p in project["exclude"]):
            continue
        loaders = sorted({LOADERS[g] for g in f["gameVersions"] if g in LOADERS})
        mcs = sorted({g for g in f["gameVersions"] if MC_RE.match(g)}, key=mc_key)
        if not loaders or not mcs:
            continue
        # NeoForge jars for 1.20.2+ don't run on Forge; CurseForge files are sometimes tagged both.
        if "neoforge" in loaders and "forge" in loaders and mc_key(mcs[0]) > mc_key("1.20.1"):
            loaders.remove("forge" if "neoforge" in f["fileName"].lower() else "neoforge")
        key = (tuple(loaders), tuple(mcs))
        if key not in newest or f["dateCreated"] > newest[key]["dateCreated"]:
            newest[key] = {**f, "loaders": loaders, "mcs": mcs}
    return sorted(newest.values(), key=lambda f: f["dateCreated"])


def readme_body(repo):
    text = get_bytes(f"https://raw.githubusercontent.com/{repo}/HEAD/README.md").decode("utf-8")
    m = CUT_RE.search(text)
    return (text[:m.start()] if m else text).strip()


def jar_icon(jar):
    """The mod icon named in the jar's metadata, shrunk under Modrinth's 256 KiB limit."""
    with zipfile.ZipFile(io.BytesIO(jar)) as z:
        names = set(z.namelist())
        path = None
        if "fabric.mod.json" in names:
            icon = json.loads(z.read("fabric.mod.json")).get("icon")
            path = icon if isinstance(icon, str) else (max(icon.items(), key=lambda kv: int(kv[0]))[1] if icon else None)
        for toml in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if path is None and toml in names:
                m = re.search(r'^\s*(?:logoFile|iconFile)\s*=\s*"([^"]+)"', z.read(toml).decode("utf-8"), re.M)
                path = m.group(1) if m else None
        if not path or path not in names:
            # Not declared (some NeoForge builds): fall back to the usual spots.
            path = next((n for n in sorted(names) if re.fullmatch(r"assets/[^/]+/icon\.png", n)),
                        "pack.png" if "pack.png" in names else None)
            if path is None:
                return None
        data = z.read(path)
    if len(data) <= ICON_LIMIT:
        return data
    try:
        from PIL import Image
    except ImportError:
        print("  icon over 256 KiB and Pillow is missing: project created without an icon")
        return None
    img = Image.open(io.BytesIO(data))
    for size in (512, 384, 256, 128):
        out = io.BytesIO()
        img.copy().resize((size, size), Image.LANCZOS).save(out, "PNG", optimize=True)
        if out.tell() <= ICON_LIMIT:
            return out.getvalue()
    return None


class Modrinth:
    def __init__(self, token, dry_run):
        self.h = {"Authorization": token}
        self.dry_run = dry_run
        self.ids = {}
        _, tags = request("GET", f"{MR_API}/tag/game_version")
        self.games = {t["version"] for t in tags}
        _, self.user = request("GET", f"{MR_API}/user", self.h)

    def project(self, slug):
        try:
            _, p = request("GET", f"{MR_API}/project/{slug}", self.h, raise_on={404})
            return p
        except LookupError:
            return None

    def dep_id(self, slug):
        if slug not in self.ids:
            p = self.project(slug)
            self.ids[slug] = p["id"] if p else None
            if not p:
                print(f"  dependency {slug} not found on Modrinth, left out")
        return self.ids[slug]


def mirror(mr, project):
    slug = project["slug"]
    print(f"== {project['title']} ({slug})")
    files = select_files(project, curseforge_files(project["curseforge_id"]))
    print(f"  {len(files)} CurseForge files to mirror")
    existing = mr.project(slug)
    if existing:
        _, members = request("GET", f"{MR_API}/project/{existing['id']}/members", mr.h)
        if mr.user["id"] not in {m["user"]["id"] for m in members}:
            raise SystemExit(f"  the slug {slug} belongs to someone else's Modrinth project; pick another slug")
        project_id = existing["id"]
        _, versions = request("GET", f"{MR_API}/project/{project_id}/version", mr.h)
        have = {v["version_number"] for v in versions}
        print(f"  project exists ({existing['status']}), {len(have)} versions")
    else:
        have = set()
        jar = get_bytes(f"{CF_WEB}/{project['curseforge_id']}/files/{files[-1]['id']}/download") if files else None
        icon = jar_icon(jar) if jar else None
        data = {
            "slug": slug, "title": project["title"], "description": project["summary"],
            "categories": project["categories"], "additional_categories": [],
            "client_side": "required", "server_side": "required",
            "body": readme_body(project["body_from"]),
            "license_id": project["license_id"],
            "source_url": project["source_url"], "issues_url": project["issues_url"],
            "project_type": "mod", "initial_versions": [], "is_draft": True,
        }
        if mr.dry_run:
            print(f"  (dry run) would create draft project, icon {'yes' if icon else 'no'}, "
                  f"description {len(data['body'])} chars")
            project_id = None
        else:
            parts = {"icon": ("icon.png", icon, "image/png")} if icon else {}
            body, ctype = multipart({"data": json.dumps(data)}, parts)
            _, created = request("POST", f"{MR_API}/project", {**mr.h, "Content-Type": ctype}, body)
            project_id = created["id"]
            print(f"  created draft project {project_id}")
    newest_mc = {}
    for f in files:
        for loader in f["loaders"]:
            if loader not in newest_mc or mc_key(f["mcs"][-1]) > mc_key(newest_mc[loader]):
                newest_mc[loader] = f["mcs"][-1]
    for f in files:
        number = f["fileName"][:-4] if f["fileName"].endswith(".jar") else f["fileName"]
        if f["mcs"][-1] not in number:
            number += "+" + f["mcs"][-1]  # e.g. two "…-fabric.jar" files for different versions
        if number in have:
            continue
        games = [g for g in f["mcs"] if g in mr.games]
        if not games:
            print(f"  {f['fileName']}: Modrinth knows none of {f['mcs']}, skipped")
            continue
        deps = []
        for loader in f["loaders"]:
            for dep_slug, kind in project["dependencies"].get(loader, []):
                dep = mr.dep_id(dep_slug)
                if dep and dep not in {d["project_id"] for d in deps}:
                    deps.append({"project_id": dep, "dependency_type": kind})
        if mr.dry_run:
            print(f"  (dry run) {number}: {f['loaders']} {games}")
            continue
        jar = get_bytes(f"{CF_WEB}/{project['curseforge_id']}/files/{f['id']}/download")
        data = {
            "name": number, "version_number": number,
            "changelog": f"Same file as on CurseForge: {CF_PAGE}/{slug}/files/{f['id']}",
            "dependencies": deps, "game_versions": games,
            "version_type": RELEASE_TYPES.get(f.get("releaseType"), "release"),
            "loaders": f["loaders"],
            "featured": any(newest_mc.get(l) == f["mcs"][-1] for l in f["loaders"]),
            "project_id": project_id, "file_parts": ["file"], "primary_file": "file",
        }
        body, ctype = multipart({"data": json.dumps(data)}, {"file": (f["fileName"], jar, "application/java-archive")})
        request("POST", f"{MR_API}/version", {**mr.h, "Content-Type": ctype}, body)
        print(f"  uploaded {number}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--manifest", default=os.path.join(os.path.dirname(__file__), "..", "mirror", "projects.json"))
    p.add_argument("--only", help="mirror just this slug")
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        raise SystemExit("MODRINTH_TOKEN is not set")
    with open(args.manifest, encoding="utf-8") as fh:
        projects = json.load(fh)["projects"]
    mr = Modrinth(token, args.dry_run)
    print(f"Modrinth user: {mr.user['username']}")
    for project in projects:
        if args.only and project["slug"] != args.only:
            continue
        mirror(mr, project)


if __name__ == "__main__":
    sys.exit(main())
