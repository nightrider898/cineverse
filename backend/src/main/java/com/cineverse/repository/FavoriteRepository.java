package com.cineverse.repository;

import com.cineverse.entity.Favorite;
import com.cineverse.entity.enums.FavoriteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favorite> findByUserIdAndTargetTypeAndTargetId(Long userId, FavoriteType type, Long targetId);
}
