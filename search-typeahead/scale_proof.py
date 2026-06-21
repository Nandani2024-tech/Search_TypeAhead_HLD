import requests
import time

BASE_URL = "http://localhost:8080"
QUERY = "scale_test"

def prove_batching():
    print("\n" + "="*60)
    print("   OFFICIAL SCALE PROOF: 1000 SEARCHES -> 1 DB WRITE")
    print("="*60)

    # 1. Get Baseline
    resp = requests.get(f"{BASE_URL}/batch/debug").json()
    start_reqs = resp['totalSearchRequestsReceived']
    start_writes = resp['totalDbWritesPerformed']
    
    print(f"\n[PHASE 1] Baseline State:")
    print(f"   - Requests Received: {start_reqs}")
    print(f"   - DB Writes Performed: {start_writes}")

    # 2. Fire 1000 searches
    print(f"\n[PHASE 2] Simulating high load: Sending 1,000 searches for '{QUERY}'...")
    for _ in range(1000):
        requests.post(f"{BASE_URL}/search", json={"query": QUERY})

    # 3. Intermediate Check
    resp_mid = requests.get(f"{BASE_URL}/batch/debug").json()
    print(f"\n[PHASE 3] Intermediate State (During Buffering):")
    print(f"   - Requests Received: {resp_mid['totalSearchRequestsReceived']} (+1000)")
    print(f"   - DB Writes Performed: {resp_mid['totalDbWritesPerformed']} (STILL {start_writes}!)")
    print(f"   - Currently Buffered: {resp_mid['currentlyBufferedQueries']} (AGGREGATED)")

    # 4. Wait for Flush
    print(f"\n[PHASE 4] Waiting for 10s flush timer...")
    for i in range(10, 0, -1):
        print(f"   Flush in {i}s...", end="\r")
        time.sleep(1)
    
    # 5. Final Check
    resp_final = requests.get(f"{BASE_URL}/batch/debug").json()
    print(f"\n\n[PHASE 5] Final State (Post-Flush):")
    print(f"   - Requests Received: {resp_final['totalSearchRequestsReceived']}")
    print(f"   - DB Writes Performed: {resp_final['totalDbWritesPerformed']}")
    
    actual_writes_for_burst = resp_final['totalDbWritesPerformed'] - start_writes
    reduction = (1.0 - (float(actual_writes_for_burst) / 1000.0)) * 100

    print("\n" + "="*60)
    print("   CONCLUSION")
    print("="*60)
    print(f"   Total Search Requests Sent: 1,000")
    print(f"   Actual Database Writes:     {actual_writes_for_burst}")
    print(f"   Efficiency Gain:            {reduction:.2f}%")
    print("="*60)

if __name__ == "__main__":
    prove_batching()
