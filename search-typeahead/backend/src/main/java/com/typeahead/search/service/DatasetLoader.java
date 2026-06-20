package com.typeahead.search.service;

import com.typeahead.search.repository.QueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetLoader implements CommandLineRunner {

    private final QueryRepository queryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${dataset.file.path:src/main/resources/data/queries_aggregated.csv}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 10000;
    private static final int PROGRESS_LOG_INTERVAL = 100000;

    @Override
    public void run(String... args) throws Exception {
        long existingCount = queryRepository.count();
        if (existingCount > 0) {
            log.info("Dataset already loaded. Found {} rows. Skipping initialization.", existingCount);
            return;
        }

        log.info("Starting dataset loading from: {}", csvFilePath);
        long startTime = System.currentTimeMillis();

        String sql = "INSERT INTO queries (query, count, last_searched_at) VALUES (?, ?, ?) ON CONFLICT (query) DO NOTHING";

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // Skip header if it exists. Let's check first line.
            line = reader.readLine();
            if (line == null) return;
            
            List<Object[]> batchArgs = new ArrayList<>();
            long totalLoaded = 0;

            // Basic header check
            if (line.contains("query") && line.contains("count")) {
                log.info("Detected CSV header. Skipping first line.");
            } else {
                // If no header, process this line
                String[] parts = parseCsvLine(line);
                if (parts.length >= 2) {
                    batchArgs.add(new Object[]{parts[0], Long.parseLong(parts[1]), parts.length > 2 ? Timestamp.valueOf(parts[2]) : null});
                    totalLoaded++;
                }
            }

            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 2) continue;

                try {
                    String queryText = parts[0];
                    Long count = Long.parseLong(parts[1]);
                    Timestamp timestamp = parts.length > 2 ? Timestamp.valueOf(parts[2]) : null;

                    batchArgs.add(new Object[]{queryText, count, timestamp});
                    totalLoaded++;

                    if (batchArgs.size() >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(sql, batchArgs);
                        batchArgs.clear();
                    }

                    if (totalLoaded % PROGRESS_LOG_INTERVAL == 0) {
                        log.info("Loaded {} rows...", totalLoaded);
                    }
                } catch (Exception e) {
                    log.error("Error parsing line: {}. Error: {}", line, e.getMessage());
                }
            }

            // Insert remaining
            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
            }

            long endTime = System.currentTimeMillis();
            log.info("Dataset loading completed. Total rows loaded: {}. Time taken: {} ms", 
                totalLoaded, (endTime - startTime));
        } catch (Exception e) {
            log.error("Fatal error during dataset loading: {}", e.getMessage(), e);
        }
    }

    private String[] parseCsvLine(String line) {
        // Simple manual split for yyyy-MM-dd HH:mm:ss CSVs. 
        // Note: Real CSV parsing might need to handle quotes if queries have commas.
        // Assuming queries don't have commas for now based on context, 
        // or using a more robust split regex if needed.
        return line.split(",");
    }
}
