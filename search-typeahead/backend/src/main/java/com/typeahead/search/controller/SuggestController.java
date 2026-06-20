package com.typeahead.search.controller;

import com.typeahead.search.config.ConsistentHashRouter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.search.dto.SuggestResponse;
import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SuggestController {

    private final QueryRepository queryRepository;
    private final ConsistentHashRouter router;
    private final ObjectMapper objectMapper;

    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestResponse>> suggest(@RequestParam(name = "q", required = false) String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String normalizedPrefix = prefix.trim().toLowerCase();
        String cacheKey = "suggest:" + normalizedPrefix;
        ConsistentHashRouter.RedisNode targetNode = router.route(cacheKey);

        try {
            String cachedJson = targetNode.getTemplate().opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.info("CACHE HIT on {} for key: {}", targetNode.getName(), cacheKey);
                List<SuggestResponse> cachedSuggestions = objectMapper.readValue(cachedJson, new TypeReference<List<SuggestResponse>>() {});
                return ResponseEntity.ok()
                        .header("X-Cache", "HIT")
                        .body(cachedSuggestions);
            }
        } catch (Exception e) {
            log.error("Redis cache read error on {} for key: {}", targetNode.getName(), cacheKey, e);
        }

        log.info("CACHE MISS on {} for key: {}", targetNode.getName(), cacheKey);
        List<SuggestResponse> suggestions = queryRepository
                .findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(normalizedPrefix)
                .stream()
                .map(query -> new SuggestResponse(query.getQuery(), query.getCount()))
                .collect(Collectors.toList());

        try {
            String jsonToCache = objectMapper.writeValueAsString(suggestions);
            targetNode.getTemplate().opsForValue().set(cacheKey, jsonToCache, Duration.ofSeconds(60));
        } catch (Exception e) {
            log.error("Redis cache write error on {} for key: {}", targetNode.getName(), cacheKey, e);
        }

        return ResponseEntity.ok()
                .header("X-Cache", "MISS")
                .body(suggestions);
    }
}

