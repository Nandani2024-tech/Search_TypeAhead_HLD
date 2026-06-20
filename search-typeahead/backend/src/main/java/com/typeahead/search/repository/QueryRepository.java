package com.typeahead.search.repository;

import com.typeahead.search.model.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryRepository extends JpaRepository<Query, Long> {

    List<Query> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);
}
