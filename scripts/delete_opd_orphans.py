#!/usr/bin/env python3
"""Delete all orphan knowledge entries (docId=41) from OPD project."""
import requests, sys

BASE = "http://localhost:8081/api"
ORPHAN_IDS = [
    157, 158, 159, 160, 161, 162, 163, 164, 165,
    143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156,
    132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142,
    120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131
]

deleted = 0
failed = 0
for eid in ORPHAN_IDS:
    try:
        r = requests.delete(f"{BASE}/knowledge-entries/{eid}", timeout=10)
        if r.status_code == 200:
            deleted += 1
            print(f"  ✓ Deleted entry {eid}")
        else:
            failed += 1
            print(f"  ✗ Entry {eid}: HTTP {r.status_code} {r.text[:100]}")
    except Exception as e:
        failed += 1
        print(f"  ✗ Entry {eid}: {e}")

print(f"\nDone: {deleted} deleted, {failed} failed")
sys.exit(0 if failed == 0 else 1)
