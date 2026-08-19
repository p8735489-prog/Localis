#!/usr/bin/env python3
"""Fail if any Android locale is missing a string key from values/strings.xml."""
import pathlib, xml.etree.ElementTree as ET, sys
root = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/res"
def keys(path):
    tree=ET.parse(path)
    return {e.attrib["name"] for e in tree.getroot().findall("string") if "name" in e.attrib}
base=keys(root/"values/strings.xml")
failed=False
for path in sorted(root.glob("values-*/strings.xml")):
    missing=base-keys(path)
    if missing:
        failed=True
        print(f"{path.parent.name}: {len(missing)} missing")
        for k in sorted(missing): print("  -", k)
    else:
        print(f"{path.parent.name}: OK ({len(base)} keys)")
sys.exit(1 if failed else 0)
