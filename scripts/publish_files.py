#!/usr/bin/env python3
"""Upload jars of one of the mods in mirror/projects.json to CurseForge and Modrinth.

The jars come from a directory (in CI: the assets of the mod's GitHub
releases). Loader and Minecraft version are read from each file name
(`...-<loader>-<minecraft>.jar`); dependencies, environment and project ids
come from mirror/projects.json.

Usage: publish_files.py --project <modrinth slug> --dir <jar dir> [--changelog text] [--dry-run]
Tokens: CURSEFORGE_TOKEN, MODRINTH_TOKEN (a platform without one is skipped).
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from publish import CF_API, MR_API, env_token, multipart, request, set_environment  # noqa: E402

NAME_RE = re.compile(r"-(?P<loader>fabric|neoforge|forge)-(?P<mc>\d+\.\d+(?:\.\d+)?)\.jar$")
LOADER_NAMES = {"fabric": "Fabric", "neoforge": "NeoForge", "forge": "Forge"}


def short_number(stem):
    """Modrinth caps version numbers at 32 characters: drop the mod id in front."""
    return stem if len(stem) <= 32 else re.sub(r"^[A-Za-z_]+-", "", stem, count=1)[-32:]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--project", required=True, help="Modrinth slug from mirror/projects.json")
    p.add_argument("--dir", required=True)
    p.add_argument("--changelog", default="")
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    manifest = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mirror", "projects.json")
    with open(manifest, encoding="utf-8") as fh:
        project = next(pr for pr in json.load(fh)["projects"] if pr["slug"] == args.project)
    files = []
    for name in sorted(os.listdir(args.dir)):
        m = NAME_RE.search(name)
        if m:
            files.append({"path": os.path.join(args.dir, name), "name": name, "stem": name[:-4],
                          "loader": m["loader"], "mc": m["mc"]})
    if not files:
        raise SystemExit(f"no *-<loader>-<mc>.jar files in {args.dir}")
    changelog = args.changelog or "New builds: " + ", ".join(f"{LOADER_NAMES[f['loader']]} {f['mc']}" for f in files)
    print(f"{project['title']}: {len(files)} files")
    skipped = []

    cf_token = env_token("CURSEFORGE_TOKEN")
    if cf_token:
        h = {"X-Api-Token": cf_token}
        _, types = request("GET", f"{CF_API}/game/version-types", h)
        kinds = {t["id"]: ("mc" if t["slug"].startswith("minecraft-") else t["slug"]) for t in types}
        _, versions = request("GET", f"{CF_API}/game/versions", h)
        ids = {}
        for v in versions:
            ids.setdefault((kinds.get(v["gameVersionTypeID"]), v["name"]), v["id"])
        env = [ids[("environment", n)] for n in ("Client", "Server") if ("environment", n) in ids]
        for f in files:
            mc_id = ids.get(("mc", f["mc"]))
            loader_id = ids.get(("modloader", LOADER_NAMES[f["loader"]]))
            if mc_id is None or loader_id is None:
                print(f"CurseForge: doesn't know {f['mc']} / {f['loader']}, {f['name']} NOT uploaded")
                skipped.append("CurseForge " + f["name"])
                continue
            meta = {"changelog": changelog, "changelogType": "markdown", "displayName": f["stem"],
                    "gameVersions": [mc_id, loader_id] + env, "releaseType": "release"}
            deps = project.get("dependencies", {}).get(f["loader"], [])
            if deps:
                meta["relations"] = {"projects": [
                    {"slug": slug, "type": "requiredDependency" if kind == "required" else "optionalDependency"}
                    for slug, kind in deps]}
            if args.dry_run:
                print(f"CurseForge (dry run): {f['name']} -> {f['loader']} {f['mc']}")
                continue
            with open(f["path"], "rb") as fh:
                body, ctype = multipart({"metadata": json.dumps(meta)}, {"file": (f["name"], fh.read(), "application/java-archive")})
            _, res = request("POST", f"{CF_API}/projects/{project['curseforge_id']}/upload-file",
                             {**h, "Content-Type": ctype}, body)
            print(f"CurseForge: uploaded {f['name']} (file id {res.get('id')})")
    else:
        print("CurseForge: no CURSEFORGE_TOKEN, skipped")

    mr_token = env_token("MODRINTH_TOKEN")
    if mr_token:
        h = {"Authorization": mr_token}
        _, mr_project = request("GET", f"{MR_API}/project/{project['slug']}", h)
        _, tags = request("GET", f"{MR_API}/tag/game_version")
        known = {t["version"] for t in tags}
        _, existing = request("GET", f"{MR_API}/project/{mr_project['id']}/version", h)
        have = {v["version_number"] for v in existing}
        dep_ids = {}
        for f in files:
            number = short_number(f["stem"])
            if number in have:
                print(f"Modrinth: {number} already there, skipped")
                continue
            if f["mc"] not in known:
                print(f"Modrinth: doesn't know {f['mc']}, {f['name']} NOT uploaded")
                skipped.append("Modrinth " + f["name"])
                continue
            deps = []
            for slug, kind in project.get("dependencies", {}).get(f["loader"], []):
                if slug not in dep_ids:
                    try:
                        _, d = request("GET", f"{MR_API}/project/{slug}", raise_on={404})
                        dep_ids[slug] = d["id"]
                    except LookupError:
                        dep_ids[slug] = None
                if dep_ids[slug]:
                    deps.append({"project_id": dep_ids[slug], "dependency_type": kind})
            data = {"name": f["stem"][:64], "version_number": number, "changelog": changelog,
                    "dependencies": deps, "game_versions": [f["mc"]], "version_type": "release",
                    "loaders": [f["loader"]], "featured": False, "project_id": mr_project["id"],
                    "file_parts": ["file"], "primary_file": "file"}
            if args.dry_run:
                print(f"Modrinth (dry run): {number} -> {f['loader']} {f['mc']}")
                continue
            with open(f["path"], "rb") as fh:
                body, ctype = multipart({"data": json.dumps(data)}, {"file": (f["name"], fh.read(), "application/java-archive")})
            _, res = request("POST", f"{MR_API}/version", {**h, "Content-Type": ctype}, body)
            set_environment(h, res["id"], project.get("environment", "client_and_server"))
            print(f"Modrinth: uploaded {number}")
    else:
        print("Modrinth: no MODRINTH_TOKEN, skipped")

    if skipped:
        print("Not uploaded: " + ", ".join(skipped))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
