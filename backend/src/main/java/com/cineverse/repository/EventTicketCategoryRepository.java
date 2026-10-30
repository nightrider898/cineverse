package com.cineverse.repository;

import com.cineverse.entity.EventTicketCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EventTicketCategoryRepository extends JpaRepository<EventTicketCategory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EventTicketCategory c where c.id in :ids order by c.id")
    List<EventTicketCategory> lockByIds(@Param("ids") Collection<Long> ids);
}
