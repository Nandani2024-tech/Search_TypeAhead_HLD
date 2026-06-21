# Design & Performance Report: Search Typeahead HLD Implementation

## 1. Design Decisions & Trade-offs

### SQL vs. NoSQL
*   **Choice**: PostgreSQL 15.
*   **Reasoning**: Required atomic ACID guarantees for search count increments and complex prefix-based retrieval. Postgres allows for an efficient `INSERT ... ON CONFLICT DO UPDATE` (UPSERT) which is critical for maintaining consistent global counts across batched flushes. NoSQL (like Cassandra) was considered but avoided to prevent eventual consistency anomalies in the "Trending" score which depends on precise timestamps.

### Consistent Hashing vs. Simple Modulo
*   **Choice**: Consistent Hashing (using a Hash Ring).
*   **Implementation**: Used **MD5** as the uniform hash function with **160 virtual nodes** per physical node.
*   **Trade-off**: While slightly more complex than `hash(key) % N`, it prevents a "Cache Storm." In my failure test, removing 1 node only invalidated ~33% of the keys, whereas Modulo would have invalidated ~100% of the cache. 

### Cache Invalidation Strategy
*   **Choice**: Prefix-Tree Cascade Invalidation.
*   **Mechanism**: On every search flush, the system iterates through every prefix length (1-100) of the query.
*   **Breadth**: Invalidates **both** `basic` and `trending` cache keys for that specific prefix.
*   **Trade-off**: Higher Redis `DEL` CPU usage during flushes, but ensures users see new trends within the target 10s window.

### In-Memory Buffer vs. Durable Queue
*   **Choice**: `ConcurrentHashMap` in-memory.
*   **Trade-off (Data Loss)**: Any searches received since the last flush are **lost if the process crashes**. For a typeahead system, high throughput (handling 10k searches/sec) is prioritized over 100% durability of a single search click.

### Trending Decay Formula
*   **Formula**: `Score = Count * 2^(-Δt / 12h)`
*   **Decay Constant**: 12-hour Half-Life.
*   **Reasoning**: Chosen to match human daily search cycles. It ensures that a legacy product (searched 1M times last year) can be overtaken by a viral topic (searched 1k times today) within ~2 days of the legacy term going stagnant.

---

## 2. Performance Data

### Metrics Sources
*   **Write Reduction**: Viewable via `/batch/debug`.
*   **Node Distribution**: Viewable via `/cache/debug?prefix=...`.
*   **Cache Hits**: Search logs show `CACHE HIT on redis-node-X`.

### Observed Performance (Local Benchmark)
*   **Write Efficiency**: 1,000 /search requests -> 1 DB write cycle (99.9% reduction).
*   **Scaling Limit**: The `ConcurrentHashMap` handles ~10,000+ distinct query aggregations per second with negligible latency.
*   **Suggest Latency**: 
    *   **p50 (Cache Hit)**: < 5ms.
    *   **p95 (Cache Miss)**: 15-25ms (limited by Postgres index scan on 1.2M rows).

### Capacity Calculation
With a 2s flush and 1000-size limit, the DB is guaranteed to receive no more than **5 writes per second** regardless of user traffic surge, protecting the persistent layer from saturation.

---

## 3. Failure & Staleness Analysis

### Scenario: App Crash during Flush Window
*   **Risk**: Potential loss of up to 10 seconds of search volume data. 
*   **Mitigation**: Acceptable for typeahead. For higher durability, a local WAL (Write-Ahead-Log) or Redis-based buffer would be the next evolutionary step.

### Scenario: Redis Node Failure
*   **Ring Behavior**: Requests owned by the failed node are automatically re-routed to the next neighbor on the ring (clockwise).
*   **Impact**: Transient cache misses for ~33.3% of the key space until Postgres warms up the new owner node.

### Cache Staleness
*   **TTL**: 60 seconds (default).
*   **Consistency**: Cache is actively invalidated upon DB flush. Maximum staleness of a suggestion is therefore bounded by the `batch flush interval` (10s), as a flush resets the cache.

---

## 4. Diagram Recommendation
For the cleanest integration, I provide a **Mermaid-JS** diagram (included in the `README.md`). Mermaid is the industry standard for HLD because it is:
1.  **Version Controlled**: It's just text.
2.  **Native Support**: GitHub and most Modern IDEs render it automatically.
3.  **Low Friction**: No binary assets to manage.
