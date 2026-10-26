#!/usr/bin/env python3
"""Upload a release's jars to CurseForge and Modrinth.

Every jar in the dist folder named `gulliver-<version>+<loader>-<minecraft>.jar`
becomes one file on CurseForge and one version on Modrinth, tagged with its
loader and the Minecraft versions it runs on. Jars that are byte-identical for
the same Minecraft version (the 1.20.1 Forge and NeoForge builds) go up once,
tagged with each of their loaders: CurseForge rejects a file identical to
another one. Game versions a platform does not know (yet) are dropped with a
warning instead of failing the upload.

Usage:
  publish.py --dist dist --version 1.2.0 --changelog notes.md [--dry-run]
             [--curseforge-project 1539752] [--modrinth-project gulliver-unbound]

Tokens come from the environment (CURSEFORGE_TOKEN, MODRINTH_TOKEN). A
platform without a token is skipped. --dry-run resolves everything (and
checks the tokens with read-only calls) but uploads nothing.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid

UA = "Thiov/gulliver release script (github.com/Thiov/gulliver)"
CF_API = "https://minecraft.curseforge.com/api"
MR_API = "https://api.modrinth.com/v2"
MR_API_V3 = "https://api.modrinth.com/v3"
# Gulliver runs on both sides. Modrinth needs this per version before review.
MR_ENVIRONMENT = "client_and_server"


def set_environment(headers, version_id, environment=MR_ENVIRONMENT):
    """Modrinth's per-version environment (v3 field, not in the v2 upload)."""
    request("PATCH", f"{MR_API_V3}/version/{version_id}", {**headers, "Content-Type": "application/json"},
            json.dumps({"environment": environment}).encode())

# Minecraft versions each target's jar runs on (see versions/*/*/gradle.properties).
GAME_VERSIONS = {
    "1.20.1": ["1.20.1"],
    "1.21.10": ["1.21.10"],
    "1.21.11": ["1.21.11"],
    "26.1.2": ["26.1", "26.1.1", "26.1.2"],
    "26.2": ["26.2"],
    "26.3": ["26.3"],
}
# The Fabric 26.1.2 jar also runs on 26.1 and 26.1.1; the others only on 26.1.2.
FABRIC_ONLY_EXTRA = {"26.1.2": {"26.1", "26.1.1"}}

LOADER_NAMES = {"fabric": "Fabric", "neoforge": "NeoForge", "forge": "Forge"}

# Modrinth project ids of dependencies (Fabric builds only).
MR_FABRIC_API = "P7dR8mSH"
MR_MOD_MENU = "mOgUt4GM"

# Files a platform couldn't take (unknown game versions); reported at the end.
SKIPPED = []

JAR_RE = re.compile(r"^gulliver-(?P<version>[^+]+)\+(?P<loader>fabric|neoforge|forge)-(?P<mc>[0-9.]+)\.jar$")


def request(method, url, headers=None, data=None, raise_on=()):
    """JSON request; HTTP errors exit, except codes in raise_on, which raise LookupError."""
    req = urllib.request.Request(url, data=data, method=method, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = r.read()
            return r.status, (json.loads(body) if body else None)
    except urllib.error.HTTPError as e:
        if e.code in raise_on:
            raise LookupError(e.code)
        detail = e.read().decode("utf-8", "replace")[:500]
        raise SystemExit(f"{method} {url} failed: HTTP {e.code}: {detail}")


def multipart(fields, files):
    """fields: {name: str}, files: {name: (filename, bytes, content_type)}."""
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n".encode()
        out += value.encode() + b"\r\n"
    for name, (filename, content, ctype) in files.items():
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n"
                f"Content-Type: {ctype}\r\n\r\n").encode()
        out += content + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def env_token(name):
    """A token from the environment, without the quotes or spaces a copy-paste can bring along."""
    value = os.environ.get(name, "").strip().strip("\"'").strip()
    return value or None


def jars(dist, version):
    found = []
    for name in sorted(os.listdir(dist)):
        m = JAR_RE.match(name)
        if not m:
            continue
        if m["version"] != version:
            raise SystemExit(f"{name}: version {m['version']} does not match --version {version}")
        mc, loader = m["mc"], m["loader"]
        if mc not in GAME_VERSIONS:
            raise SystemExit(f"{name}: no game-version mapping for Minecraft {mc}")
        games = [g for g in GAME_VERSIONS[mc]
                 if loader == "fabric" or g not in FABRIC_ONLY_EXTRA.get(mc, set())]
        path = os.path.join(dist, name)
        with open(path, "rb") as fh:
            sha1 = hashlib.sha1(fh.read()).hexdigest()
        found.append({"path": path, "name": name, "loader": loader, "loaders": [loader], "mc": mc,
                      "games": games, "sha1": sha1})
    if not found:
        raise SystemExit(f"no gulliver-{version}+*.jar files in {dist}")
    return merge_identical(found)


def merge_identical(files):
    """One upload per distinct jar. On 1.20.1 NeoForge was still the Forge fork, so
    its build is the Forge jar byte for byte; it goes up once with both loaders."""
    merged = {}
    for f in files:
        same = merged.get((f["mc"], f["sha1"]))
        if same:
            same["loaders"].append(f["loader"])
            print(f"{f['name']} is identical to {same['name']}: one upload for {', '.join(same['loaders'])}")
        else:
            merged[(f["mc"], f["sha1"])] = f
    return list(merged.values())


def curseforge(args, files, changelog):
    token = env_token("CURSEFORGE_TOKEN")
    if not token:
        print("CurseForge: no CURSEFORGE_TOKEN, skipped")
        return
    h = {"X-Api-Token": token}
    _, types = request("GET", f"{CF_API}/game/version-types", h)
    mc_types = {t["id"] for t in types if t["slug"].startswith("minecraft-")}
    loader_types = {t["id"] for t in types if t["slug"] == "modloader"}
    env_types = {t["id"] for t in types if t["slug"] == "environment"}
    _, versions = request("GET", f"{CF_API}/game/versions", h)
    by_name = {}
    for v in versions:
        if v["gameVersionTypeID"] in mc_types:
            by_name.setdefault(("mc", v["name"]), v["id"])
        elif v["gameVersionTypeID"] in loader_types:
            by_name.setdefault(("loader", v["name"]), v["id"])
        elif v["gameVersionTypeID"] in env_types:
            by_name.setdefault(("env", v["name"]), v["id"])
    # The mod runs on both sides; CurseForge wants at least one environment tag.
    environments = [by_name[("env", n)] for n in ("Client", "Server") if ("env", n) in by_name]
    for f in files:
        ids, missing = [], []
        for g in f["games"]:
            (ids.append(by_name[("mc", g)]) if ("mc", g) in by_name else missing.append(g))
        loader_ids = []
        for loader in f["loaders"]:
            loader_id = by_name.get(("loader", LOADER_NAMES[loader]))
            if loader_id is None:
                raise SystemExit(f"CurseForge does not list the loader {LOADER_NAMES[loader]}")
            loader_ids.append(loader_id)
        if not ids:
            print(f"CurseForge: knows none of {f['games']}, {f['name']} NOT uploaded")
            SKIPPED.append(f"CurseForge {f['name']}")
            continue
        if missing:
            print(f"CurseForge: {f['name']}: unknown game versions {missing}, left out")
        meta = {
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": f["name"][:-4],
            "gameVersions": ids + loader_ids + environments,
            "releaseType": args.release_type,
        }
        if f["loader"] == "fabric":
            meta["relations"] = {"projects": [
                {"slug": "fabric-api", "type": "requiredDependency"},
                {"slug": "modmenu", "type": "optionalDependency"},
            ]}
        if args.dry_run:
            print(f"CurseForge (dry run): {f['name']} -> games {f['games']} loaders {f['loaders']}")
            continue
        with open(f["path"], "rb") as fh:
            body, ctype = multipart({"metadata": json.dumps(meta)}, {"file": (f["name"], fh.read(), "application/java-archive")})
        _, res = request("POST", f"{CF_API}/projects/{args.curseforge_project}/upload-file",
                         {**h, "Content-Type": ctype}, body)
        print(f"CurseForge: uploaded {f['name']} (file id {res.get('id')})")


def modrinth(args, files, changelog):
    token = env_token("MODRINTH_TOKEN")
    if not token:
        print("Modrinth: no MODRINTH_TOKEN, skipped")
        return
    h = {"Authorization": token}
    try:
        _, project = request("GET", f"{MR_API}/project/{args.modrinth_project}", h, raise_on={404})
    except LookupError:
        msg = f"Modrinth project {args.modrinth_project} doesn't exist yet (create it with the modrinth-mirror workflow)"
        if args.dry_run:
            print(msg)
            return
        raise SystemExit(msg)
    project_id = project["id"]
    _, tags = request("GET", f"{MR_API}/tag/game_version")
    known = {t["version"] for t in tags}
    _, existing = request("GET", f"{MR_API}/project/{project_id}/version", h)
    existing_numbers = {v["version_number"] for v in existing}
    newest = max((f["mc"] for f in files), key=lambda v: [int(x) for x in v.split(".")])
    for f in files:
        games = [g for g in f["games"] if g in known]
        missing = [g for g in f["games"] if g not in known]
        if missing:
            print(f"Modrinth: {f['name']}: unknown game versions {missing}, left out")
        if not games:
            print(f"Modrinth: knows none of {f['games']}, {f['name']} NOT uploaded")
            SKIPPED.append(f"Modrinth {f['name']}")
            continue
        number = f"{args.version}+{f['loader']}-{f['mc']}"
        if number in existing_numbers:
            print(f"Modrinth: {number} already exists, skipped")
            continue
        data = {
            "name": f"Gulliver {args.version} ({'/'.join(LOADER_NAMES[loader] for loader in f['loaders'])} {f['mc']})",
            "version_number": number,
            "changelog": changelog,
            "dependencies": [],
            "game_versions": games,
            "version_type": args.release_type,
            "loaders": f["loaders"],
            "featured": f["mc"] == newest,
            "project_id": project_id,
            "file_parts": ["file"],
            "primary_file": "file",
        }
        if f["loader"] == "fabric":
            data["dependencies"] = [
                {"project_id": MR_FABRIC_API, "dependency_type": "required"},
                {"project_id": MR_MOD_MENU, "dependency_type": "optional"},
            ]
        if args.dry_run:
            print(f"Modrinth (dry run): {number} -> games {games} loaders {f['loaders']}")
            continue
        with open(f["path"], "rb") as fh:
            body, ctype = multipart({"data": json.dumps(data)}, {"file": (f["name"], fh.read(), "application/java-archive")})
        _, res = request("POST", f"{MR_API}/version", {**h, "Content-Type": ctype}, body)
        set_environment(h, res["id"])
        print(f"Modrinth: uploaded {number} (version id {res.get('id')})")


def check_tokens():
    """Describe the tokens' shape (never their value) and try one read-only call each."""
    ok = True
    cf = env_token("CURSEFORGE_TOKEN")
    if not cf:
        print("CURSEFORGE_TOKEN: not set")
        ok = False
    else:
        shape = re.fullmatch(r"[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}", cf) is not None
        print(f"CURSEFORGE_TOKEN: {len(cf)} characters, shaped like a CurseForge key (8-4-4-4-12 hex): "
              f"{'yes' if shape else 'NO'}{', starts like a Modrinth key' if cf.startswith('mrp_') else ''}")
        try:
            request("GET", f"{CF_API}/game/version-types", {"X-Api-Token": cf}, raise_on={400, 401, 403})
            print("  CurseForge accepts it")
        except LookupError as e:
            print(f"  CurseForge rejects it (HTTP {e.args[0]})")
            ok = False
    mr = env_token("MODRINTH_TOKEN")
    if not mr:
        print("MODRINTH_TOKEN: not set")
        ok = False
    else:
        print(f"MODRINTH_TOKEN: {len(mr)} characters, starts with mrp_: {'yes' if mr.startswith('mrp_') else 'NO'}")
        try:
            _, user = request("GET", f"{MR_API}/user", {"Authorization": mr}, raise_on={401, 403})
            print(f"  Modrinth accepts it (user {user['username']})")
        except LookupError as e:
            print(f"  Modrinth rejects it (HTTP {e.args[0]})")
            ok = False
    return 0 if ok else 1


def main():
    if "--check-tokens" in sys.argv:
        return check_tokens()
    p = argparse.ArgumentParser()
    p.add_argument("--dist", default="dist")
    p.add_argument("--version", required=True)
    p.add_argument("--changelog", required=True, help="markdown file with this release's notes")
    p.add_argument("--curseforge-project", default="1539752")
    p.add_argument("--modrinth-project", default="gulliver-unbound")
    p.add_argument("--release-type", default="release", choices=["release", "beta", "alpha"])
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    with open(args.changelog, encoding="utf-8") as fh:
        changelog = fh.read().strip()
    files = jars(args.dist, args.version)
    print(f"{len(files)} jars for Gulliver {args.version}")
    curseforge(args, files, changelog)
    modrinth(args, files, changelog)
    if SKIPPED:
        print("Not uploaded (platform doesn't know the Minecraft version yet): " + ", ".join(SKIPPED))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
