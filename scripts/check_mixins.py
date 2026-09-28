#!/usr/bin/env python3
"""Static check of Gulliver's mixin targets against a Minecraft jar.

For every mixin class in a preprocessed source tree, resolve the @Mixin
target class(es) and make sure each `method = "..."` exists in that class
(by name, or by exact descriptor when one is given), and that each
@At(value = "INVOKE", target = "...") names a method that exists.

Usage: check_mixins.py <preprocessed-java-dir> <minecraft.jar> [more.jar...]
Exit code 1 when something is missing. Uses the JDK's javap.
"""
import os
import re
import subprocess
import sys

JAVAP = os.environ.get("JAVAP", "javap")

src, jars = sys.argv[1], sys.argv[2:]
cp = os.pathsep.join(jars)
cache = {}


def members(fqcn):
    """Return set of (name, descriptor) for methods and set of field names."""
    if fqcn in cache:
        return cache[fqcn]
    out = subprocess.run([JAVAP, "-p", "-s", "-classpath", cp, fqcn],
                         capture_output=True, text=True)
    methods, fields = set(), set()
    if out.returncode != 0:
        cache[fqcn] = None
        return None
    lines = out.stdout.splitlines()
    for i, line in enumerate(lines):
        line = line.strip()
        if not line.startswith("descriptor:"):
            continue
        desc = line.split(":", 1)[1].strip()
        decl = lines[i - 1].strip().rstrip(";")
        m = re.search(r"([A-Za-z0-9_$<>]+)\(", decl)
        if m:
            name = m.group(1)
            if "(" in decl and not re.search(r"\bnew\b", decl):
                # constructors appear as the class name
                simple = fqcn.rsplit(".", 1)[-1]
                if name == simple or name == fqcn:
                    name = "<init>"
            methods.add((name, desc))
        else:
            fname = decl.split()[-1]
            fields.add(fname)
    cache[fqcn] = (methods, fields)
    return cache[fqcn]


def resolve(simple, imports, pkg):
    if "." in simple:
        return simple
    for imp in imports:
        if imp.endswith("." + simple):
            return imp
    return None


problems = []
checked = 0
for root, _, files in os.walk(os.path.join(src, "gulliver", "mixin")):
    for f in files:
        if not f.endswith(".java"):
            continue
        path = os.path.join(root, f)
        text = open(path, encoding="utf-8").read()
        # drop commented-out (inactive) lines
        live = "\n".join(l for l in text.splitlines() if not l.strip().startswith("//"))
        m = re.search(r"@Mixin\((?:value\s*=\s*)?\{?([^)}]*)\}?\)", live)
        if not m:
            continue
        imports = re.findall(r"^import\s+([\w.]+);", live, re.M)
        pkg = re.search(r"^package\s+([\w.]+);", live, re.M).group(1)
        targets = []
        for t in m.group(1).split(","):
            t = t.strip()
            if not t.endswith(".class"):
                continue
            cls = resolve(t[:-6], imports, pkg)
            if cls is None:
                problems.append(f"{f}: cannot resolve mixin target {t}")
                continue
            targets.append(cls)
        for cls in targets:
            info = members(cls)
            if info is None:
                problems.append(f"{f}: target class {cls} not found")
                continue
            methods, fields = info
            for meth in re.findall(r'method\s*=\s*"([^"]+)"', live):
                checked += 1
                if "(" in meth:
                    name, desc = meth.split("(", 1)
                    desc = "(" + desc
                    if (name, desc) not in methods:
                        cands = sorted(d for n, d in methods if n == name)
                        problems.append(f"{f}: {cls}.{name}{desc} missing; have {cands or 'none'}")
                else:
                    if not any(n == meth for n, _ in methods):
                        problems.append(f"{f}: {cls}.{meth} missing")
            for tgt in re.findall(r'target\s*=\s*"(L[^;]+;[^"(]+\([^"]*)"', live):
                owner, rest = tgt[1:].split(";", 1)
                name, desc = rest.split("(", 1)
                oc = owner.replace("/", ".")
                oi = members(oc)
                checked += 1
                if oi is None:
                    problems.append(f"{f}: INVOKE owner {oc} not found")
                    continue
                if (name, "(" + desc) not in oi[0]:
                    # the method may be inherited; accept if any class in chain has it
                    problems.append(f"{f}: INVOKE {oc}.{name}({desc} not declared (may be inherited)")
            for sh in re.findall(r'@Shadow[^;{]*?\s([A-Za-z_$][\w$]*)\s*\(', live):
                checked += 1
                if not any(n == sh for n, _ in methods):
                    problems.append(f"{f}: @Shadow method {cls}.{sh} missing")
            for sh in re.findall(r'@Shadow(?:\s+@\w+)*\s+(?:(?:public|protected|private|final|static)\s+)*[\w.<>\[\]]+\s+([A-Za-z_$][\w$]*)\s*;', live):
                checked += 1
                if sh not in fields:
                    problems.append(f"{f}: @Shadow field {cls}.{sh} missing")
            for acc in re.findall(r'@Accessor\("([^"]+)"\)', live):
                checked += 1
                if acc not in fields:
                    problems.append(f"{f}: accessor field {cls}.{acc} missing")

print(f"checked {checked} mixin references")
for p in problems:
    print("  " + p)
sys.exit(1 if any("not declared" not in p for p in problems) else 0)
