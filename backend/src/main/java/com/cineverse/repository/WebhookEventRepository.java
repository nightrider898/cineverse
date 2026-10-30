package com.cineverse.repository;

import com.cineverse.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    /** Atomic claim: returns 1 for the first delivery of an event id, 0 for any duplicate. */
    @Modifying
    @Query(value = """
        insert into webhook_events (provider, event_id, event_type) values (:provider, :eventId, :eventType)
        on conflict (provider, event_id) do nothing
        """, nativeQuery = true)
    int claim(@Param("provider") String provider, @Param("eventId") String eventId, @Param("eventType") String eventType);

    @Modifying
    @Query(value = "update webhook_events set processed_at = now(), outcome = :outcome where provider = :provider and event_id = :eventId", nativeQuery = true)
    int markProcessed(@Param("provider") String provider, @Param("eventId") String eventId, @Param("outcome") String outcome);
}
