#!/usr/bin/env python3
"""Static check of Gulliver's mixins against a Minecraft jar.

For every mixin class in a preprocessed source tree:
  - the @Mixin target classes exist;
  - every `method = ...` exists in the target (by name, or by exact
    descriptor when one is given);
  - INVOKE / FIELD targets exist on the owner or one of its supertypes;
  - @Shadow methods and fields and @Accessor fields exist;
  - handler signatures fit the target: @Inject arguments and
    CallbackInfo/CallbackInfoReturnable, @Redirect and @ModifyArg against
    the invoked method, @ModifyVariable(argsOnly) ordinals and indices,
    @ModifyConstant constants actually present in the target's bytecode.

Usage: check_mixins.py <preprocessed-java-dir> <minecraft.jar> [more.jar...]
Exit code 1 when something is wrong. Uses the JDK's javap (JAVAP env var).
"""
import os
import re
import struct
import subprocess
import sys

JAVAP = os.environ.get("JAVAP", "javap")

src, jars = sys.argv[1], sys.argv[2:]
cp = os.pathsep.join(jars)

PRIMS = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
         "int": "I", "long": "J", "float": "F", "double": "D"}
JAVA_LANG = {"Object", "String", "Boolean", "Integer", "Float", "Double", "Long",
             "Short", "Byte", "Character", "Void", "Iterable", "Runnable", "Class"}


class Info:
    def __init__(self):
        self.methods = set()   # (name, desc)
        self.static = set()    # (name, desc) of static methods
        self.fields = set()
        self.supers = []       # fqcn of superclass and interfaces


_cache = {}


def strip_generics(s):
    out, depth = [], 0
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def decl_name(decl, fqcn):
    """Method name in a javap declaration line, or None for a field."""
    if decl.startswith("static {}"):
        return "<clinit>"
    m = re.search(r"([A-Za-z0-9_$<>.]+)\(", decl)
    if not m:
        return None
    name = m.group(1)
    if name == fqcn or name == fqcn.rsplit(".", 1)[-1]:
        return "<init>"
    return name


def members(fqcn):
    if fqcn in _cache:
        return _cache[fqcn]
    out = subprocess.run([JAVAP, "-p", "-s", "-classpath", cp, fqcn],
                         capture_output=True, text=True)
    if out.returncode != 0:
        _cache[fqcn] = None
        return None
    info = Info()
    lines = out.stdout.splitlines()
    for line in lines:
        if re.search(r"\b(class|interface|enum|record)\s", line) and line.rstrip().endswith("{"):
            head = strip_generics(line)
            m = re.search(r"\bextends\s+([\w.$, ]+?)(?:\s+implements|\s*\{)", head)
            if m:
                info.supers += [s.strip() for s in m.group(1).split(",") if s.strip()]
            m = re.search(r"\bimplements\s+([\w.$, ]+?)\s*\{", head)
            if m:
                info.supers += [s.strip() for s in m.group(1).split(",") if s.strip()]
            break
    for i, line in enumerate(lines):
        line = line.strip()
        if not line.startswith("descriptor:"):
            continue
        desc = line.split(":", 1)[1].strip()
        decl = strip_generics(lines[i - 1].strip().rstrip(";"))
        name = decl_name(decl, fqcn)
        if name:
            info.methods.add((name, desc))
            if re.search(r"\bstatic\b", decl):
                info.static.add((name, desc))
        else:
            info.fields.add(decl.split()[-1])
    _cache[fqcn] = info
    return info


def find_method(owner, name, desc, seen=None):
    """(declaring class, is_static) for a method on owner or a supertype."""
    seen = seen if seen is not None else set()
    if owner in seen:
        return None
    seen.add(owner)
    info = members(owner)
    if info is None:
        return None
    if (name, desc) in info.methods:
        return owner, (name, desc) in info.static
    for sup in info.supers:
        r = find_method(sup, name, desc, seen)
        if r:
            return r
    return None


def find_field(owner, name, seen=None):
    seen = seen if seen is not None else set()
    if owner in seen:
        return False
    seen.add(owner)
    info = members(owner)
    if info is None:
        return False
    if name in info.fields:
        return True
    return any(find_field(s, name, seen) for s in info.supers)


_code_cache = {}


def method_code(fqcn):
    """{(name, desc): [instruction lines]} from javap -c."""
    if fqcn in _code_cache:
        return _code_cache[fqcn]
    out = subprocess.run([JAVAP, "-p", "-s", "-c", "-classpath", cp, fqcn],
                         capture_output=True, text=True)
    code, cur, prev = {}, None, ""
    for line in out.stdout.splitlines():
        s = line.strip()
        if s.startswith("descriptor:"):
            name = decl_name(strip_generics(prev.rstrip(";")), fqcn)
            cur = (name, s.split(":", 1)[1].strip()) if name else None
            if cur:
                code[cur] = []
        elif cur is not None and re.match(r"\d+:", s):
            code[cur].append(s)
        prev = s
    _code_cache[fqcn] = code
    return code


def f32(x):
    return struct.unpack("f", struct.pack("f", x))[0]


def has_constant(lines, kind, value):
    for ins in lines:
        op = ins.split(":", 1)[1].strip()
        if kind == "float":
            m = re.match(r"fconst_(\d)", op)
            if m and f32(float(m.group(1))) == f32(value):
                return True
            m = re.search(r"//\s*float\s+(\S+)", op)
            if m and f32(float(m.group(1).rstrip("fF"))) == f32(value):
                return True
        elif kind == "double":
            m = re.match(r"dconst_(\d)", op)
            if m and float(m.group(1)) == value:
                return True
            m = re.search(r"//\s*double\s+(\S+)", op)
            if m and float(m.group(1).rstrip("dD")) == value:
                return True
        elif kind == "int":
            m = re.match(r"iconst_(m?\d)", op)
            if m and int(m.group(1).replace("m", "-")) == value:
                return True
            m = re.match(r"[bs]ipush\s+(-?\d+)", op)
            if m and int(m.group(1)) == value:
                return True
            m = re.search(r"//\s*int\s+(-?\d+)", op)
            if m and int(m.group(1)) == value:
                return True
    return False


def split_desc(desc):
    """'(IF[Lx/Y;)V' -> (['I', 'F', '[Lx/Y;'], 'V')."""
    params, i = [], 1
    while desc[i] != ")":
        j = i
        while desc[j] == "[":
            j += 1
        if desc[j] == "L":
            j = desc.index(";", j)
        params.append(desc[i:j + 1])
        i = j + 1
    return params, desc[i + 1:]


def to_internal(fqcn):
    parts = fqcn.split(".")
    for k, p in enumerate(parts):
        if p[:1].isupper():
            return "/".join(parts[:k] + ["$".join(parts[k:])])
    return fqcn.replace(".", "/")


def resolve(simple, imports):
    simple = simple.strip()
    if "." in simple and simple[0].isupper():
        outer, inner = simple.split(".", 1)
        base = resolve(outer, imports)
        return base + "." + inner if base else None
    if "." in simple:
        return simple
    for imp in imports:
        if imp.endswith("." + simple):
            return imp
    if simple in JAVA_LANG:
        return "java.lang." + simple
    return None


def type_desc(t, imports):
    t = strip_generics(t).strip()
    dims = 0
    if t.endswith("..."):
        dims, t = 1, t[:-3].strip()
    while t.endswith("[]"):
        dims, t = dims + 1, t[:-2].strip()
    if t in PRIMS:
        return "[" * dims + PRIMS[t]
    fq = resolve(t, imports)
    if fq is None:
        return None
    return "[" * dims + "L" + to_internal(fq) + ";"


def balanced(text, start):
    """Index just past the parenthesis group opening at text[start]."""
    depth, i, instr = 0, start, False
    while i < len(text):
        ch = text[i]
        if instr:
            if ch == "\\":
                i += 1
            elif ch == '"':
                instr = False
        elif ch == '"':
            instr = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(text)


def split_top(s):
    out, depth, cur = [], 0, []
    for ch in s:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            out.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        out.append("".join(cur))
    return out


def handler_after(text, pos, imports):
    """Parse the method declared after an annotation ending at pos."""
    i = pos
    while True:
        m = re.match(r"\s*@[\w.]+", text[i:])
        if not m:
            break
        i += m.end()
        if text[i:i + 1] == "(":
            i = balanced(text, i)
    m = re.match(r"\s*((?:(?:private|public|protected|static|final)\s+)*)([\w.<>\[\], ?]+?)\s+([\w$]+)\s*\(", text[i:])
    if not m:
        return None
    ret = m.group(2)
    open_paren = i + m.end() - 1
    close = balanced(text, open_paren)
    params = []
    for p in split_top(text[open_paren + 1:close - 1]):
        p = re.sub(r"@[\w.]+(\s*\([^)]*\))?", "", p)
        p = re.sub(r"\bfinal\b", "", p).strip()
        if not p:
            continue
        m2 = re.match(r"(.*?)\s*([\w$]+)$", p, re.S)
        params.append(m2.group(1).strip() if m2 else p)
    return {"name": m.group(3), "ret": ret, "params": params,
            "descs": [type_desc(t, imports) for t in params],
            "ret_desc": type_desc(ret, imports)}


def method_specs(ann):
    m = re.search(r'\bmethod\s*=\s*(\{[^}]*\}|"[^"]*")', ann)
    return re.findall(r'"([^"]+)"', m.group(1)) if m else []


def candidates(info, spec):
    if "(" in spec:
        name, desc = spec.split("(", 1)
        return [(n, d) for n, d in info.methods if n == name and d == "(" + desc]
    return [(n, d) for n, d in info.methods if n == spec]


def arg_slots(desc, static):
    slots, i = [], 0 if static else 1
    for p in split_desc(desc)[0]:
        slots.append((i, p))
        i += 2 if p in ("J", "D") else 1
    return slots


problems = []
checked = 0
INJECTORS = r"@(Inject|Redirect|ModifyArg|ModifyVariable|ModifyConstant)\s*\("

for root, _, files in os.walk(os.path.join(src, "gulliver", "mixin")):
    for f in sorted(files):
        if not f.endswith(".java"):
            continue
        text = open(os.path.join(root, f), encoding="utf-8").read()
        live = "\n".join(l for l in text.splitlines() if not l.strip().startswith("//"))
        # fully qualified annotations (@org.spongepowered.asm.mixin.Shadow) read as short ones
        live = re.sub(r"@org\.spongepowered\.asm\.mixin\.(?:\w+\.)*(\w+)", r"@\1", live)
        m = re.search(r"@Mixin\((?:value\s*=\s*)?\{?([^)}]*)\}?\)", live)
        if not m:
            continue
        imports = re.findall(r"^import\s+([\w.]+);", live, re.M)
        targets = []
        for t in m.group(1).split(","):
            t = t.strip()
            if not t.endswith(".class"):
                continue
            cls = resolve(t[:-6], imports)
            if cls is None:
                problems.append(f"{f}: cannot resolve mixin target {t}")
                continue
            targets.append(to_internal(cls).replace("/", "."))

        def bad(msg):
            problems.append(f"{f}: {msg}")

        for cls in targets:
            info = members(cls)
            if info is None:
                bad(f"target class {cls} not found")
                continue
            if len(targets) == 1:
                for ann_m in re.finditer(INJECTORS, live):
                    ann = live[ann_m.start():balanced(live, ann_m.end() - 1)]
                    for spec in method_specs(ann):
                        checked += 1
                        if not candidates(info, spec):
                            have = sorted(d for n, d in info.methods if n == spec.split("(")[0])
                            bad(f"{cls}.{spec} missing; have {have or 'none'}")
            for sh in re.findall(r'@Shadow[^;{]*?\s([A-Za-z_$][\w$]*)\s*\(', live):
                checked += 1
                if not any(n == sh for n, _ in info.methods):
                    bad(f"@Shadow method {cls}.{sh} missing")
            for sh in re.findall(r'@Shadow(?:\s+@\w+)*\s+(?:(?:public|protected|private|final|static)\s+)*[\w.<>\[\]]+\s+([A-Za-z_$][\w$]*)\s*;', live):
                checked += 1
                if sh not in info.fields:
                    bad(f"@Shadow field {cls}.{sh} missing")
            for acc in re.findall(r'@Accessor\("([^"]+)"\)', live):
                checked += 1
                if acc not in info.fields:
                    bad(f"accessor field {cls}.{acc} missing")

        # multi-target mixins: each method must exist in at least one target
        if len(targets) > 1:
            for ann_m in re.finditer(INJECTORS, live):
                ann = live[ann_m.start():balanced(live, ann_m.end() - 1)]
                for spec in method_specs(ann):
                    checked += 1
                    if not any(members(c) and candidates(members(c), spec) for c in targets):
                        bad(f"{spec} missing in every target {targets}")

        # INVOKE / FIELD targets, anywhere in the file
        for tgt in re.findall(r'target\s*=\s*"(L[^;"]+;[^"(:]+\([^"]*)"', live):
            owner, rest = tgt[1:].split(";", 1)
            name, desc = rest.split("(", 1)
            checked += 1
            oc = owner.replace("/", ".")
            if members(oc) is None:
                bad(f"INVOKE owner {oc} not found")
            elif not find_method(oc, name, "(" + desc):
                bad(f"INVOKE {oc}.{name}({desc} not found on the owner or its supertypes")
        for tgt in re.findall(r'target\s*=\s*"(L[^;"]+;[\w$]+:[^"]+)"', live):
            owner, rest = tgt[1:].split(";", 1)
            name = rest.split(":", 1)[0]
            checked += 1
            if not find_field(owner.replace("/", "."), name):
                bad(f"FIELD {owner}.{name} not found")

        # handler signatures
        for ann_m in re.finditer(INJECTORS, live):
            kind = ann_m.group(1)
            end = balanced(live, ann_m.end() - 1)
            ann = live[ann_m.start():end]
            h = handler_after(live, end, imports)
            if h is None:
                bad(f"{kind} at offset {ann_m.start()}: cannot parse the handler")
                continue
            if None in h["descs"]:
                unresolved = [t for t, d in zip(h["params"], h["descs"]) if d is None]
                bad(f"{kind} {h['name']}: cannot resolve parameter types {unresolved}")
                continue
            specs = method_specs(ann)
            cands = []
            for c in targets:
                ci = members(c)
                if ci:
                    for s in specs:
                        cands += [(c, n, d, (n, d) in ci.static) for n, d in candidates(ci, s)]
            if not cands:
                continue  # already reported as missing
            checked += 1
            where = f"{kind} {h['name']}"

            if kind == "Inject":
                cb = next((k for k, t in enumerate(h["params"])
                           if strip_generics(t).split(".")[-1] in ("CallbackInfo", "CallbackInfoReturnable")), None)
                if cb is None:
                    bad(f"{where}: no CallbackInfo parameter")
                    continue
                pre = h["descs"][:cb]
                cir = strip_generics(h["params"][cb]).split(".")[-1] == "CallbackInfoReturnable"
                ok = False
                for c, n, d, st in cands:
                    params, r = split_desc(d)
                    if pre and pre != params:
                        continue
                    if (r != "V") != cir:
                        continue
                    ok = True
                if not ok:
                    bad(f"{where}: arguments {pre} + {'CallbackInfoReturnable' if cir else 'CallbackInfo'} "
                        f"fit none of {[d for _, _, d, _ in cands]}")

            elif kind in ("Redirect", "ModifyArg"):
                t = re.search(r'target\s*=\s*"L([^;"]+);([^"(:]+)(\([^"]*)"', ann)
                if not t:
                    continue
                owner, iname, idesc = t.group(1).replace("/", "."), t.group(2), t.group(3)
                found = find_method(owner, iname, idesc)
                if not found:
                    continue  # reported above
                iparams, iret = split_desc(idesc)
                if kind == "Redirect":
                    got = h["descs"] if found[1] else h["descs"][1:]
                    if not any(got == iparams or got == iparams + split_desc(d)[0] for _, _, d, _ in cands):
                        bad(f"{where}: arguments {got} are not {iname}{idesc} (+ the target's arguments)")
                    if h["ret_desc"] != iret:
                        bad(f"{where}: returns {h['ret_desc']}, {iname} returns {iret}")
                else:
                    im = re.search(r"\bindex\s*=\s*(\d+)", ann)
                    if im:
                        idx = int(im.group(1))
                        if idx >= len(iparams):
                            bad(f"{where}: index {idx} out of range for {iname}{idesc}")
                            continue
                        want = iparams[idx]
                    elif len(h["descs"]) == 1:
                        want = h["descs"][0]
                        if iparams.count(want) != 1:
                            bad(f"{where}: {want} is {'ambiguous' if iparams.count(want) else 'absent'} in {iname}{idesc}; give an index")
                            continue
                    else:
                        want = None
                    if want and h["descs"] not in ([want], iparams):
                        bad(f"{where}: parameters {h['descs']} do not fit argument {want} of {iname}{idesc}")
                    if want and h["ret_desc"] != want:
                        bad(f"{where}: returns {h['ret_desc']}, the argument is {want}")

            elif kind == "ModifyVariable":
                if not re.search(r"argsOnly\s*=\s*true", ann) or not h["descs"]:
                    continue
                var = h["descs"][0]
                om = re.search(r"\bordinal\s*=\s*(\d+)", ann)
                xm = re.search(r"\bindex\s*=\s*(\d+)", ann)
                ok = False
                for c, n, d, st in cands:
                    same = [s for s, p in arg_slots(d, st) if p == var]
                    if xm:
                        ok |= int(xm.group(1)) in same
                    elif om:
                        ok |= int(om.group(1)) < len(same)
                    else:
                        ok |= len(same) == 1
                if not ok:
                    how = f"at index {xm.group(1)}" if xm else f"ordinal {om.group(1)}" if om else "(unique)"
                    bad(f"{where}: no argument {var} {how} in {[d for _, _, d, _ in cands]}")

            elif kind == "ModifyConstant":
                for cm in re.finditer(r"(float|double|int)Value\s*=\s*(-?[\d.]+(?:[eE][+-]?\d+)?)[fFdD]?", ann):
                    ctype, val = cm.group(1), cm.group(2)
                    value = int(val) if ctype == "int" else float(val)
                    if not any(has_constant(method_code(c).get((n, d), []), ctype, value)
                               for c, n, d, _ in cands):
                        bad(f"{where}: {ctype} constant {val} not in {[n + d for _, n, d, _ in cands]}")

print(f"checked {checked} mixin references")
for p in problems:
    print("  " + p)
sys.exit(1 if problems else 0)
