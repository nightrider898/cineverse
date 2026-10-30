package com.cineverse;

import com.cineverse.booking.BookingService;
import com.cineverse.booking.CancellationService;
import com.cineverse.dto.BookingDtos.CreateBookingRequest;
import com.cineverse.dto.PaymentDtos.InitiatePaymentRequest;
import com.cineverse.entity.Show;
import com.cineverse.entity.User;
import com.cineverse.entity.enums.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.payment.PaymentProvider;
import com.cineverse.payment.PaymentService;
import com.cineverse.payment.RefundService;
import com.cineverse.repository.*;
import com.cineverse.seat.SeatLockService;
import com.cineverse.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec §100: cancellation succeeds before the policy deadline and is rejected after it. */
class CancellationTest extends IntegrationTest {
    @Autowired SeatLockService seatLockService;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired CancellationService cancellationService;
    @Autowired RefundService refundService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired RefundRepository refundRepository;
    @Autowired ShowSeatRepository showSeatRepository;
    @Autowired TicketRepository ticketRepository;

    @Test
    void cancelBeforeDeadlineReleasesSeatsAndRefunds() {
        Show show = fixtures.showAt(Duration.ofHours(10), 21);
        User u = fixtures.user("USER");
        Long bookingId = confirmedBooking(show, u, List.of("A3", "A4"));

        var cancelled = cancellationService.cancel(u.getId(), bookingId, "Plans changed", false);
        assertThat(cancelled.status()).isEqualTo("REFUND_PENDING");
        assertThat(showSeatRepository.findSeatMap(show.getId()).stream().filter(s -> s.getLabel().equals("A3")).findFirst().orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
        assertThat(ticketRepository.findByBookingId(bookingId).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);

        var refund = refundRepository.findFirstByBookingIdOrderByRequestedAtDesc(bookingId).orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        refundService.processDue();
        assertThat(refundRepository.findById(refund.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.REFUNDED);
        // policy keeps the convenience fee and its tax, so the payment is partially refunded
        var payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.PARTIALLY_REFUNDED).orElseThrow();
        var booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(booking.getGrandTotal().subtract(booking.getConvenienceFee()).subtract(booking.getTax()));
    }

    @Test
    void cancelAfterDeadlineIsRejected() {
        Show show = fixtures.showAt(Duration.ofHours(10), 22);
        User u = fixtures.user("USER");
        Long bookingId = confirmedBooking(show, u, List.of("B1"));

        clock.advance(Duration.ofHours(9)); // now 1 hour before the show; policy cut-off is 120 minutes
        assertThatThrownBy(() -> cancellationService.cancel(u.getId(), bookingId, null, false))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void otherUsersCannotCancel() {
        Show show = fixtures.showAt(Duration.ofHours(30), 23);
        User owner = fixtures.user("USER");
        User stranger = fixtures.user("USER");
        Long bookingId = confirmedBooking(show, owner, List.of("C1"));
        assertThatThrownBy(() -> cancellationService.cancel(stranger.getId(), bookingId, null, false))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.BOOKING_NOT_FOUND);
    }

    private Long confirmedBooking(Show show, User u, List<String> seats) {
        var lock = seatLockService.lockSeats(show.getId(), u.getId(), seats);
        var booking = bookingService.createFromLock(u.getId(), new CreateBookingRequest(lock.lockId(), List.of()));
        var init = paymentService.initiate(u.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.UPI));
        paymentService.applyOutcome(paymentRepository.findByPublicId(init.paymentId()).orElseThrow().getId(),
                PaymentProvider.Outcome.SUCCESS, "p", null, "TEST");
        return booking.id();
    }
}