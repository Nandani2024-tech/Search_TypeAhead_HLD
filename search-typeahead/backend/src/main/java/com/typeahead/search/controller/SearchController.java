package com.typeahead.search.controller;

import com.typeahead.search.config.ConsistentHashRouter;
import com.typeahead.search.dto.SearchRequest;
import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final QueryRepository queryRepository;
    private final ConsistentHashRouter router;

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) SearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be blank"));
        }

        String sanitizedQuery = request.getQuery().trim().toLowerCase();
        
        queryRepository.upsertQuery(sanitizedQuery);

        invalidatePrefixes(sanitizedQuery);

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }

    private void invalidatePrefixes(String query) {
        int maxLength = Math.min(query.length(), 100); 
        for (int i = 1; i <= maxLength; i++) {
            String cacheKey = "suggest:" + query.substring(0, i);
            ConsistentHashRouter.RedisNode targetNode = router.route(cacheKey);
            try {
                targetNode.getTemplate().delete(cacheKey);
            } catch (Exception e) {
                log.error("Failed to invalidate cache key {} on node {}", cacheKey, targetNode.getName(), e);
            }
        }
        log.info("Invalidated {} cache keys across cluster for search term: {}", maxLength, query);
    }
}

