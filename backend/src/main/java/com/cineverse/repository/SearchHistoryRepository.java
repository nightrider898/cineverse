package com.cineverse.repository;

import com.cineverse.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    @Query("select h.query from SearchHistory h where h.userId = :userId group by h.query order by max(h.createdAt) desc")
    List<String> recentForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select lower(h.query) from SearchHistory h where h.createdAt > :since group by lower(h.query) order by count(h) desc")
    List<String> trending(@Param("since") Instant since, Pageable pageable);
}
