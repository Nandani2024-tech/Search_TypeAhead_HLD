package com.typeahead.search.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "queries", indexes = {
    @Index(name = "idx_query_text", columnList = "query")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Query {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String query;

    @Column(nullable = false)
    private Long count;

    @Column(name = "last_searched_at")
    private Timestamp lastSearchedAt;
}
