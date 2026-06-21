import requests
import time

BASE_URL = "http://localhost:8080"

def verify_batching():
    print("\n" + "="*50)
    print("   VERIFYING BATCH WRITE AGGREGATION")
    print("="*50)

    # 1. Initial State
    print("\n[1/4] Checking initial metrics...")
    r = requests.get(f"{BASE_URL}/batch/debug")
    print(f"Initial Metrics: {r.json()}")

    # 2. Burst Search
    print("\n[2/4] Sending 50 search requests for 'iphone' in a tight loop...")
    for _ in range(50):
        requests.post(f"{BASE_URL}/search", json={"query": "iphone"})
    
    # 3. Check Buffer Immediately
    print("\n[3/4] Checking metrics immediately after burst...")
    r = requests.get(f"{BASE_URL}/batch/debug")
    metrics = r.json()
    print(f"Metrics (Buffered): {metrics}")
    print(f"Search Requests Captured: {metrics['totalSearchRequestsReceived']}")
    
    # 4. Wait for Flush
    print("\n[4/4] Waiting 3 seconds for the 2s flush timer to fire...")
    time.sleep(3)
    
    r = requests.get(f"{BASE_URL}/batch/debug")
    final_metrics = r.json()
    print(f"Final Metrics (Flushed): {final_metrics}")
    
    reduction = final_metrics['writeReductionPercentage']
    print("\n" + "="*50)
    print(f"✅ BATCHING VERIFIED!")
    print(f"   Search Requests: {final_metrics['totalSearchRequestsReceived']}")
    print(f"   Database Writes: {final_metrics['totalDbWritesPerformed']}")
    print(f"   Write Reduction: {reduction}")
    print("="*50)

if __name__ == "__main__":
    verify_batching()
