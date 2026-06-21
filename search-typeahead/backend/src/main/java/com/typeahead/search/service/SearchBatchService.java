package com.typeahead.search.service;

import com.typeahead.search.config.ConsistentHashRouter;
import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchBatchService {

    private final QueryRepository queryRepository;
    private final ConsistentHashRouter router;

    @Value("${search.batch.max.size:1000}")
    private int maxBufferSize;

    // The Buffer: Aggregates counts per query
    private final Map<String, Long> buffer = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong totalSearchRequests = new AtomicLong(0);
    private final AtomicLong totalDbWrites = new AtomicLong(0);
    private final AtomicLong flushCount = new AtomicLong(0);
    private LocalDateTime lastFlushTime = LocalDateTime.now();

    public void bufferQuery(String query) {
        totalSearchRequests.getAndIncrement();
        
        // Atomic increment of the count in the buffer
        buffer.merge(query, 1L, Long::sum);

        // Check if size trigger is met
        if (buffer.size() >= maxBufferSize) {
            log.info("Batch size trigger met ({} queries). Flushing...", buffer.size());
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${search.batch.flush.interval:10000}")
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        log.info("Starting batch flush of {} distinct queries...", buffer.size());
        
        // Snapshot the buffer and clear it
        // We use synchronized on flush to ensure only one thread flushes at a time
        // while the main buffer stays open for new incoming requests.
        Map<String, Long> snapshot = new ConcurrentHashMap<>(buffer);
        buffer.clear();

        snapshot.forEach((query, delta) -> {
            try {
                // One aggregated UPSERT to the DB
                queryRepository.upsertBatchQuery(query, delta);
                totalDbWrites.getAndIncrement();

                // Invalidate cache for this query
                invalidatePrefixes(query);
            } catch (Exception e) {
                log.error("Failed to flush query '{}' to DB", query, e);
                // In a production system, we might re-buffer the failed query
                // but for this phase we acknowledge it's dropped if it fails.
            }
        });

        lastFlushTime = LocalDateTime.now();
        flushCount.getAndIncrement();
        log.info("Batch flush completed. Last flush at: {}", lastFlushTime);
    }

    private void invalidatePrefixes(String query) {
        int maxLength = Math.min(query.length(), 100);
        for (int i = 1; i <= maxLength; i++) {
            String prefix = query.substring(0, i);
            String[] modes = {"basic", "trending"};

            for (String mode : modes) {
                String cacheKey = "suggest:" + mode + ":" + prefix;
                ConsistentHashRouter.RedisNode targetNode = router.route(cacheKey);
                try {
                    targetNode.getTemplate().delete(cacheKey);
                } catch (Exception e) {
                    log.error("Failed to invalidate cache for {} on {}", cacheKey, targetNode.getName());
                }
            }
        }
    }

    // Metric Getters for Debug API
    public Map<String, Object> getBatchMetrics() {
        return Map.of(
            "currentlyBufferedQueries", buffer.size(),
            "totalSearchRequestsReceived", totalSearchRequests.get(),
            "totalDbWritesPerformed", totalDbWrites.get(),
            "totalFlushesPerformed", flushCount.get(),
            "lastSuccessfulFlushTimestamp", lastFlushTime.toString(),
            "writeReductionPercentage", calculateReduction() + "%"
        );
    }

    private String calculateReduction() {
        long reqs = totalSearchRequests.get();
        long writes = totalDbWrites.get();
        if (reqs == 0) return "0";
        double reduction = (1.0 - ((double) writes / reqs)) * 100.0;
        return String.format("%.2f", reduction);
    }
}
