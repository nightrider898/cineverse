package com.cineverse.repository;

import com.cineverse.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {
    @Query("select f from FoodItem f where f.available = true and (f.cinemaId is null or f.cinemaId = :cinemaId) order by f.sortOrder, f.name")
    List<FoodItem> menuForCinema(@Param("cinemaId") Long cinemaId);

    List<FoodItem> findAllByOrderBySortOrderAscNameAsc();
}
