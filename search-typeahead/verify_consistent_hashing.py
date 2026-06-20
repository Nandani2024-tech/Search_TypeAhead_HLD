#!/usr/bin/env python3
"""
Consistent Hashing Verification Script (Fixed)

Key fix: Instead of relying on 'docker stop' (which doesn't update the in-memory ring),
we call POST /admin/nodes/remove to eject node-2 from the ring itself.
This is what actually triggers key redistribution — proving consistent hashing works.

Proof point:
  - Naive modulo hashing: ALL keys remap when ring size changes (3 → 2)
  - Consistent hashing:   ONLY the keys owned by the removed node remap (~1/3)
"""

import json
import time
import urllib.request
import urllib.error
import urllib.parse
import sys
import os

# ── Configuration ─────────────────────────────────────────────────────────────
BASE_URL        = "http://localhost:8080"
NODE_TO_REMOVE  = "redis-node-2"
DOCKER_CONTAINER = "typeahead-redis-2"

PREFIXES = [
    # Single letters a-z (26)
    "a","b","c","d","e","f","g","h","i","j","k","l","m",
    "n","o","p","q","r","s","t","u","v","w","x","y","z",
    # Real multi-char prefixes (10)
    "goo","ip","my","xbox","ebay","java","iphone","amazon","net","mac"
]

BEFORE_FILE = "before_mapping.json"
AFTER_FILE  = "after_mapping.json"

# ── Helpers ───────────────────────────────────────────────────────────────────
def colored(text, code):
    return f"\033[{code}m{text}\033[0m"

RED    = lambda t: colored(t, "31")
GREEN  = lambda t: colored(t, "32")
YELLOW = lambda t: colored(t, "33")
CYAN   = lambda t: colored(t, "36")
BOLD   = lambda t: colored(t, "1")

def http_get(url):
    with urllib.request.urlopen(url, timeout=5) as resp:
        return json.loads(resp.read().decode())

def http_post(url):
    req = urllib.request.Request(url, method="POST", data=b"")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read().decode())

def fetch_node(prefix):
    url = f"{BASE_URL}/cache/debug?prefix={urllib.parse.quote(prefix)}"
    try:
        data = http_get(url)
        return data.get("node", "unknown")
    except Exception as e:
        print(RED(f"  [ERROR] {url}: {e}"))
        return "error"

def collect_mappings(label):
    print(BOLD(f"\n{'─'*58}"))
    print(BOLD(f"  Collecting {label} mappings ({len(PREFIXES)} prefixes)..."))
    print(f"{'─'*58}")
    mappings, node_counts = {}, {}
    for prefix in PREFIXES:
        node = fetch_node(prefix)
        mappings[prefix] = node
        node_counts[node] = node_counts.get(node, 0) + 1
        print(f"  {CYAN(prefix):>12}  →  {YELLOW(node)}")
        time.sleep(0.05)
    return mappings, node_counts

def print_distribution(node_counts, total):
    print(BOLD("\n  Distribution:"))
    for node, count in sorted(node_counts.items()):
        bar = "█" * count
        pct = count / total * 100
        print(f"    {YELLOW(node):<20}  {bar} {count} ({pct:.1f}%)")

def save_json(data, filename):
    with open(filename, "w") as f:
        json.dump(data, f, indent=2)
    print(GREEN(f"\n  ✓ Saved → {os.path.abspath(filename)}"))

def remove_node_from_ring(node_name):
    url = f"{BASE_URL}/admin/nodes/remove?node={urllib.parse.quote(node_name)}"
    try:
        result = http_post(url)
        return result
    except Exception as e:
        print(RED(f"  [ERROR] Could not remove node: {e}"))
        return None

def list_active_nodes():
    try:
        result = http_get(f"{BASE_URL}/admin/nodes")
        return result.get("activeNodes", [])
    except Exception as e:
        return [f"error: {e}"]

def analyze(before, after):
    remapped, stayed = [], []
    total = len(PREFIXES)

    for prefix in PREFIXES:
        b, a = before.get(prefix, "?"), after.get(prefix, "?")
        if b != a:
            remapped.append((prefix, b, a))
        else:
            stayed.append((prefix, b))

    remap_pct = len(remapped) / total * 100

    print(BOLD(f"\n{'═'*58}"))
    print(BOLD("  ANALYSIS RESULTS"))
    print(f"{'═'*58}")

    print(BOLD(f"\n  Prefixes that REMAPPED  ({len(remapped)} keys → redistributed):"))
    if remapped:
        for prefix, bnode, anode in remapped:
            print(f"    {CYAN(prefix):>12}  {YELLOW(bnode)}  →  {GREEN(anode)}")
    else:
        print("    (none — node may not have been removed from ring)")

    print(BOLD(f"\n  Prefixes that STAYED  ({len(stayed)} keys → consistent hashing proof):"))
    for prefix, node in stayed:
        print(f"    {CYAN(prefix):>12}  →  {GREEN(node)}")

    print(BOLD(f"\n{'─'*58}"))
    print(f"  Total prefixes : {total}")
    print(f"  Remapped       : {YELLOW(str(len(remapped)))}  ({remap_pct:.1f}%)")
    print(f"  Stayed         : {GREEN(str(len(stayed)))}  ({100-remap_pct:.1f}%)")
    print(f"{'─'*58}")

    # ── Verdict ────────────────────────────────────────────────────────────────
    print(BOLD(f"\n{'═'*58}"))
    if 15.0 <= remap_pct <= 50.0:
        print(GREEN(BOLD("  ✅  PASS — Consistent Hashing confirmed!")))
        print(f"\n  {remap_pct:.1f}% of prefixes remapped when 1 of 3 nodes was removed.")
        print(f"  Expected ~20-40% (only keys owned by the removed node shift).")
        print(f"  The other {100-remap_pct:.1f}% stayed — this is the proof consistent")
        print(f"  hashing works. Modulo hashing would have remapped ~100%.")
    elif remap_pct >= 85.0:
        print(RED(BOLD("  ❌  FAIL — Looks like modulo/naive hashing!")))
        print(f"  {remap_pct:.1f}% of keys remapped — nearly everything changed.")
        print("  In true consistent hashing only ~1/N keys remap when 1 node fails.")
    elif remap_pct == 0.0:
        print(RED(BOLD("  ❌  FAIL — Ring was NOT updated (0% remapping)")))
        print("  The node was not successfully removed from the in-memory ring.")
        print("  Check that POST /admin/nodes/remove returned successfully.")
    else:
        print(YELLOW(BOLD("  ⚠️   INCONCLUSIVE")))
        print(f"  {remap_pct:.1f}% remapped — verify active nodes list above.")
    print(f"{'═'*58}\n")

# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    print(BOLD(CYAN("\n╔══════════════════════════════════════════════════════════╗")))
    print(BOLD(CYAN("║   Consistent Hashing Verification — Node Failure (Fixed) ║")))
    print(BOLD(CYAN("╚══════════════════════════════════════════════════════════╝")))
    print(f"\n  Strategy: eject '{NODE_TO_REMOVE}' from the in-memory ring via")
    print(f"  POST /admin/nodes/remove, then re-query all prefixes.\n")

    # Step 1: confirm app is up  
    print(BOLD("  [1/5] Checking Spring Boot is reachable..."))
    try:
        active = list_active_nodes()
        print(GREEN(f"  ✓ App is up. Active ring nodes: {list(active)}"))
    except Exception as e:
        print(RED(f"  ✗ Cannot reach {BASE_URL}: {e}"))
        print(RED("  Start the app with: mvn spring-boot:run"))
        sys.exit(1)

    # Step 2: BEFORE mappings
    print(BOLD("\n  [2/5] Recording BEFORE mappings (all 3 nodes active)..."))
    before, before_counts = collect_mappings("BEFORE")
    save_json(before, BEFORE_FILE)
    print_distribution(before_counts, len(PREFIXES))

    # Step 3: eject node-2 from ring
    print(BOLD(f"\n{'═'*58}"))
    print(BOLD(f"  [3/5] Ejecting '{NODE_TO_REMOVE}' from the consistent hash ring..."))
    print(f"{'═'*58}")
    result = remove_node_from_ring(NODE_TO_REMOVE)
    if result:
        remaining = list(result.get("activeNodes", []))
        print(GREEN(f"  ✓ Node '{NODE_TO_REMOVE}' removed from ring."))
        print(f"  Remaining active nodes: {remaining}")
        print(f"\n  NOTE: You can optionally also run in another terminal:")
        print(YELLOW(f"    docker stop {DOCKER_CONTAINER}"))
        print("  (but the ring is already updated — the docker step is optional here)")
    else:
        print(RED("  ✗ Failed to remove node from ring. Aborting."))
        sys.exit(1)

    time.sleep(0.5)  # brief pause for clarity

    # Step 4: AFTER mappings
    print(BOLD(f"\n  [4/5] Recording AFTER mappings (node-2 ejected from ring)..."))
    after, after_counts = collect_mappings("AFTER (node-2 ejected)")
    save_json(after, AFTER_FILE)
    print_distribution(after_counts, len(PREFIXES))

    # Step 5: analysis
    print(BOLD(f"\n  [5/5] Computing redistribution analysis..."))
    analyze(before, after)

    # Cleanup reminder
    print(BOLD("  CLEANUP"))
    print("  Restart the app to restore all 3 nodes to the ring:")
    print(GREEN(BOLD("    Ctrl+C  then  mvn spring-boot:run")))
    print("  Or if you stopped the Docker container, also run:")
    print(GREEN(BOLD(f"    docker start {DOCKER_CONTAINER}\n")))

if __name__ == "__main__":
    main()
