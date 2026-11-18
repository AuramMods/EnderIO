#!/usr/bin/env python3
"""Compare the actual assembly outputs of two independent Ender IO builds."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

MODULES = ("endercore", "enderio", "enderio-modded-conduits")


def artifacts(root):
    result = {}
    for module in MODULES:
        directory = root / module / "build/libs"
        jars = sorted(directory.glob("*.jar"))
        if not jars:
            raise FileNotFoundError(f"No assembled jars in {directory}")
        for jar in jars:
            result[str(jar.relative_to(root))] = jar
    return result


def explain_difference(left, right, prefix=""):
    """Locate payload changes recursively when raw archive bytes differ."""
    with zipfile.ZipFile(io.BytesIO(left)) as a, zipfile.ZipFile(io.BytesIO(right)) as b:
        entries_a = a.namelist()
        entries_b = b.namelist()
        differences = []
        if entries_a != entries_b:
            differences.append(prefix + "archive entry order or inventory")
        for name in sorted(set(entries_a) & set(entries_b)):
            value_a, value_b = a.read(name), b.read(name)
            if value_a == value_b:
                continue
            if name.endswith(".jar"):
                differences.extend(explain_difference(value_a, value_b, prefix + name + "!/"))
            else:
                differences.append(prefix + name)
        return differences or [prefix + "ZIP metadata or compression"]


def compare(left_root, right_root):
    left, right = artifacts(left_root), artifacts(right_root)
    rows = []
    for name in sorted(set(left) | set(right)):
        a = left[name].read_bytes() if name in left else None
        b = right[name].read_bytes() if name in right else None
        equal = a == b
        if equal:
            differences = []
        elif a is None or b is None:
            differences = ["Missing from " + ("left" if a is None else "right") + " build"]
        else:
            differences = explain_difference(a, b)
        rows.append({
            "artifact": name,
            "leftSha256": hashlib.sha256(a).hexdigest() if a is not None else None,
            "rightSha256": hashlib.sha256(b).hexdigest() if b is not None else None,
            "equal": equal,
            "differentEntries": differences,
        })
    return {"equal": all(row["equal"] for row in rows), "artifacts": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left", type=Path, help="Root of the first built checkout")
    parser.add_argument("right", type=Path, help="Root of the second built checkout")
    args = parser.parse_args()
    result = compare(args.left, args.right)
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["equal"] else 1)


if __name__ == "__main__":
    main()
