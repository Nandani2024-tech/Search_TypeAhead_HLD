package com.typeahead.search.controller;

import com.typeahead.search.service.SearchBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
public class BatchDebugController {

    private final SearchBatchService batchService;

    @GetMapping("/debug")
    public Map<String, Object> getBatchDebug() {
        return batchService.getBatchMetrics();
    }
}
