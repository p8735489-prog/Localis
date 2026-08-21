#!/usr/bin/env python3
"""Static audit for the GitHub Actions APK publication pipeline."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
p = root / '.github/workflows/android-release.yml'
s = p.read_text(encoding='utf-8')
errors=[]
required=[
    'actions/checkout@v5',
    'actions/setup-java@v5',
    'android-actions/setup-android@v3',
    'actions/upload-artifact@v4',
    'softprops/action-gh-release@v2',
    'assembleDebug',
    'assembleRelease',
    'fail_on_unmatched_files: true',
    'gh release view',
]
for x in required:
    if x not in s: errors.append(f'missing workflow feature: {x}')
if 'files: ${{' not in s: errors.append('release action has no dynamic APK files input')
if "tag_name: ${{ startsWith(github.ref, 'refs/tags/v')" not in s: errors.append('release tag strategy missing')
if 'permissions:\n  contents: write' not in s: errors.append('contents: write permission missing')
if errors:
    print('RELEASE WORKFLOW AUDIT FAILED')
    print('\n'.join(' - '+e for e in errors)); sys.exit(1)
print('RELEASE WORKFLOW AUDIT PASSED')
