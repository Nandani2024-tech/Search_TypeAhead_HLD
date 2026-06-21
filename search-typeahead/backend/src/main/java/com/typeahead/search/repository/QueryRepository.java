package com.typeahead.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface QueryRepository extends JpaRepository<com.typeahead.search.model.Query, Long> {

    List<com.typeahead.search.model.Query> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);

    List<com.typeahead.search.model.Query> findTop50ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);

    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO queries (query, count, last_searched_at) VALUES (:queryText, :delta, CURRENT_TIMESTAMP) " +
                   "ON CONFLICT (query) DO UPDATE SET count = queries.count + :delta, last_searched_at = CURRENT_TIMESTAMP", 
           nativeQuery = true)
    void upsertBatchQuery(@Param("queryText") String queryText, @Param("delta") Long delta);

    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO queries (query, count, last_searched_at) VALUES (:queryText, 1, CURRENT_TIMESTAMP) " +
                   "ON CONFLICT (query) DO UPDATE SET count = queries.count + 1, last_searched_at = CURRENT_TIMESTAMP", 
           nativeQuery = true)
    void upsertQuery(@Param("queryText") String queryText);
}
