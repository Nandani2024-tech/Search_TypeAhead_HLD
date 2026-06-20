package com.typeahead.search.controller;

import com.typeahead.search.dto.SearchRequest;
import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final QueryRepository queryRepository;
    private final StringRedisTemplate redisTemplate;

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
        List<String> keysToDelete = new ArrayList<>();
        int maxLength = Math.min(query.length(), 100); 
        for (int i = 1; i <= maxLength; i++) {
            keysToDelete.add("suggest:" + query.substring(0, i));
        }
        
        try {
            redisTemplate.delete(keysToDelete);
            log.info("Invalidated {} cache keys for search term: {}", keysToDelete.size(), query);
        } catch (Exception e) {
            log.error("Failed to invalidate cache keys for: {}", query, e);
        }
    }
}

