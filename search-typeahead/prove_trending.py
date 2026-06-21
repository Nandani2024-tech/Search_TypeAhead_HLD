import requests
import time
import subprocess

# Backend URL
BASE_URL = "http://localhost:8080"
PREFIX = "trendtest"

def run_sql(sql):
    cmd = ["docker", "exec", "typeahead-postgres", "psql", "-U", "postgres", "-d", "typeahead", "-c", sql]
    subprocess.run(cmd, capture_output=True)

def invalidate_cache():
    # Calling search on a fake query clears prefixes, but we can just clear manually via redis
    # Or just wait for the 60s TTL, but let's be fast
    for i in range(1, 4): # Clear from all 3 redis nodes just in case
        port = 6379 + (i-1)
        subprocess.run(["docker", "exec", f"typeahead-redis-{i}", "redis-cli", "FLUSHALL"], capture_output=True)

def verify():
    print("\n" + "="*50)
    print("   EXERTING PROOF OF TRENDING VS BASIC")
    print("="*50)

    # 1. Setup Data
    print("\n[1/4] Setting up test data via SQL...")
    run_sql(f"DELETE FROM queries WHERE query LIKE '{PREFIX}%';")
    
    # Query A: High volume (1000), but stale (3 days old)
    run_sql(f"INSERT INTO queries (query, count, last_searched_at) VALUES ('{PREFIX} legacy', 1000, NOW() - INTERVAL '3 days');")
    
    # Query B: Low volume (50), but fresh (now)
    run_sql(f"INSERT INTO queries (query, count, last_searched_at) VALUES ('{PREFIX} fresh', 50, NOW());")

    # 2. Clear Cache
    print("[2/4] Clearing Redis cache...")
    invalidate_cache()

    # 3. Basic Mode Check
    print("[3/4] Requesting BASIC mode (Sort by Count)...")
    r_basic = requests.get(f"{BASE_URL}/suggest?q={PREFIX}&mode=basic")
    basic_results = r_basic.json()
    
    print("\n--- BASIC RESULTS ---")
    for i, res in enumerate(basic_results):
        print(f"{i+1}. {res['query']} (Count: {res['count']})")

    # 4. Trending Mode Check
    print("\n[4/4] Requesting TRENDING mode (Sort by Recency Decay)...")
    r_trend = requests.get(f"{BASE_URL}/suggest?q={PREFIX}&mode=trending")
    trend_results = r_trend.json()
    
    print("\n--- TRENDING RESULTS ---")
    for i, res in enumerate(trend_results):
        print(f"{i+1}. {res['query']} (Count: {res['count']})")

    print("\n" + "="*50)
    if basic_results[0]['query'] == f"{PREFIX} legacy" and trend_results[0]['query'] == f"{PREFIX} fresh":
        print("✅ PROOF SUCCESSFUL!")
        print(f"   Legacy query (1000 searches) won in BASIC mode.")
        print(f"   Fresh query (50 searches) won in TRENDING mode.")
    else:
        print("❌ PROOF FAILED - Check logs.")
    print("="*50)

if __name__ == "__main__":
    verify()
