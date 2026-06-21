package com.typeahead.search.controller;

import com.typeahead.search.dto.SearchRequest;
import com.typeahead.search.service.SearchBatchService;
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

    private final SearchBatchService batchService;

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) SearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be blank"));
        }

        String sanitizedQuery = request.getQuery().trim().toLowerCase();
        
        // Instead of immediate DB write: Buffer in memory
        batchService.bufferQuery(sanitizedQuery);

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
