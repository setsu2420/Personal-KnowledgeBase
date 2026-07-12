import urllib.request, json, sys

# Query all OPD entries
url = 'http://localhost:8080/api/knowledge-entries?projectId=12&page=1&pageSize=500'
try:
    resp = urllib.request.urlopen(url, timeout=30)
    data = json.loads(resp.read())
except Exception as e:
    print(f"ERROR querying entries: {e}")
    sys.exit(1)

items = data.get('items', [])
print(f"Total entries in OPD (projectId=12): {len(items)}")
print()

# Query all documents to check which have backing docs
doc_url = 'http://localhost:8080/api/documents?page=1&pageSize=500'
try:
    doc_resp = urllib.request.urlopen(doc_url, timeout=30)
    doc_data = json.loads(doc_resp.read())
    doc_items = doc_data.get('items', [])
except Exception as e:
    print(f"ERROR querying documents: {e}")
    sys.exit(1)

doc_ids = set(d['id'] for d in doc_items)
print(f"Total documents in system: {len(doc_ids)}")

# Find entries without backing documents (orphans)
orphans = []
for e in items:
    did = e.get('documentId')
    eid = e.get('id')
    title = e.get('title', '?')
    if did is None or did not in doc_ids:
        orphans.append(e)
        print(f"  ORPHAN: ID={eid}  docId={did}  title={title}")

if not orphans:
    print("\n✅ No orphan entries found — all entries have valid backing documents.")
else:
    print(f"\n⚠️  Found {len(orphans)} orphan entries without valid backing documents.")
    print(f"Orphan IDs: {[o['id'] for o in orphans]}")
