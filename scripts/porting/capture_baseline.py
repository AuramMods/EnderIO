#!/usr/bin/env python3
"""Capture the fixed port baseline; this is an inventory, never a build gate.

Declarations and test methods are source discoveries, not runtime registrations
or executed tests. Compare them with runtime registries/reports as the port runs.
The output is immutable reference material; record progress in the port tracker.
"""

import io
import json
import re
import subprocess
import tarfile
from pathlib import Path

BASELINE = "4c482da69e76f2b46bfeeac64ef0ac277c318eaa"
ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs/porting/1.20.1/baseline.json"


def main():
    archive = subprocess.check_output(["git", "archive", BASELINE], cwd=ROOT)
    declarations = []
    tests = []
    resources = {}
    content_sources = []
    with tarfile.open(fileobj=io.BytesIO(archive)) as tree:
        for member in tree:
            if not member.isfile():
                continue
            path = member.name
            if path.endswith(".java"):
                text = tree.extractfile(member).read().decode()
                if "/src/main/java/" in path and any(
                    part in path for part in ("/content/", "/compat/", "/modules/")
                ) and not path.endswith("package-info.java"):
                    content_sources.append(path)
                if "/init/" in path or "/common/modules/" in path:
                    pattern = r"^[ \t]*public static final ([^\n=]+?)\s+([A-Z][A-Z0-9_]*)\s*="
                    for match in re.finditer(pattern, text, re.MULTILINE):
                        declarations.append({
                            "source": path,
                            "line": text[:match.start()].count("\n") + 1,
                            "symbol": match.group(2),
                            "type": match.group(1),
                        })
                if "/src/test/" in path or "/src/gametest/" in path:
                    # Record public test methods and their annotations as a
                    # source inventory. Actual test discovery is a later check.
                    pattern = r"((?:\s*@[^\n]+\n)+)\s*public\s+(?:static\s+)?void\s+(\w+)\s*\("
                    for match in re.finditer(pattern, text):
                        if not re.search(r"@(GameTest|Test)\b", match.group(1)):
                            continue
                        tests.append({
                            "source": path,
                            "line": text[:match.start()].count("\n") + 1,
                            "method": match.group(2),
                            "annotations": match.group(1).strip(),
                        })
            if "/src/generated/resources/" in path or "/src/main/resources/" in path:
                relative = path.split("/resources/", 1)[1]
                if relative.startswith("data/") and relative.endswith(".json"):
                    parts = relative.split("/")
                    group = f"{parts[1]}/{parts[2]}"
                    resources.setdefault(group, []).append("/".join(parts[3:])[:-5])
                if relative.startswith("assets/") and "/blockstates/" in relative:
                    parts = relative.split("/")
                    resources.setdefault(f"{parts[1]}/blockstates", []).append(parts[-1][:-5])
    result = {
        "baseline_commit": BASELINE,
        "scope_note": "Source inventory only. Declarations may create multiple registry entries; source tests are not executed tests. Reconcile with runtime registries and reports.",
        "registration_declarations": declarations,
        "content_and_integration_sources": sorted(content_sources),
        "test_methods": tests,
        "resource_ids": {key: sorted(set(values)) for key, values in sorted(resources.items())},
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {len(declarations)} declarations, "
          f"{len(content_sources)} content/integration sources, {len(tests)} test methods, "
          f"{sum(len(v) for v in result['resource_ids'].values())} data/blockstate IDs")


if __name__ == "__main__":
    main()
