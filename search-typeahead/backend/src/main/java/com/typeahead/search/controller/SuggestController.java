package com.typeahead.search.controller;

import com.typeahead.search.dto.SuggestResponse;
import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class SuggestController {

    private final QueryRepository queryRepository;

    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestResponse>> suggest(@RequestParam(name = "q", required = false) String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<SuggestResponse> suggestions = queryRepository
                .findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(prefix.trim())
                .stream()
                .map(query -> new SuggestResponse(query.getQuery(), query.getCount()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(suggestions);
    }
}
