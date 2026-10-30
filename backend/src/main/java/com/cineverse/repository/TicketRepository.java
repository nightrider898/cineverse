package com.cineverse.repository;

import com.cineverse.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t join t.qrCode q where q.qrToken = :token")
    Optional<Ticket> findByQrTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.ticketCode = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);
}
