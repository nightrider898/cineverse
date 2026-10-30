package com.cineverse;

import com.cineverse.booking.BookingService;
import com.cineverse.dto.BookingDtos.CreateBookingRequest;
import com.cineverse.dto.PaymentDtos.InitiatePaymentRequest;
import com.cineverse.entity.Show;
import com.cineverse.entity.User;
import com.cineverse.entity.enums.*;
import com.cineverse.payment.PaymentService;
import com.cineverse.payment.provider.MockPaymentProvider;
import com.cineverse.repository.*;
import com.cineverse.seat.SeatLockService;
import com.cineverse.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §98: a payment webhook delivered twice must not create a second booking or ticket. */
class PaymentIdempotencyTest extends IntegrationTest {
    @Autowired SeatLockService seatLockService;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired MockPaymentProvider mockProvider;
    @Autowired PaymentRepository paymentRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired ShowSeatRepository showSeatRepository;
    @Autowired CouponUsageRepository couponUsageRepository;

    @Test
    void duplicateWebhookIsProcessedOnce() {
        Show show = fixtures.show(Duration.ofDays(2));
        User u = fixtures.user("USER");
        var lock = seatLockService.lockSeats(show.getId(), u.getId(), List.of("F5", "F6"));
        var booking = bookingService.createFromLock(u.getId(), new CreateBookingRequest(lock.lockId(), List.of()));
        var init = paymentService.initiate(u.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.UPI));
        var payment = paymentRepository.findByPublicId(init.paymentId()).orElseThrow();

        String body = "{\"eventId\":\"evt_dup_1\",\"orderId\":\"" + payment.getProviderOrderId() + "\",\"paymentRef\":\"pay_1\",\"outcome\":\"SUCCESS\"}";
        Map<String, String> headers = Map.of(MockPaymentProvider.SIGNATURE_HEADER, mockProvider.signature(body));

        assertThat(paymentService.handleWebhook(PaymentProviderType.MOCK, body, headers)).isEqualTo("SUCCESS");
        assertThat(paymentService.handleWebhook(PaymentProviderType.MOCK, body, headers)).isEqualTo("DUPLICATE");

        var b = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(ticketRepository.findByBookingId(b.getId())).isPresent();
        assertThat(ticketRepository.findAll().stream().filter(t -> t.getBooking().getId().equals(b.getId()))).hasSize(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(showSeatRepository.findSeatMap(show.getId()).stream().filter(s -> s.getStatus() == SeatStatus.BOOKED)).hasSize(2);
    }

    @Test
    void distinctSuccessEventsForSamePaymentStillConfirmOnce() {
        Show show = fixtures.show(Duration.ofDays(2));
        User u = fixtures.user("USER");
        var lock = seatLockService.lockSeats(show.getId(), u.getId(), List.of("G1"));
        var booking = bookingService.createFromLock(u.getId(), new CreateBookingRequest(lock.lockId(), List.of()));
        var init = paymentService.initiate(u.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.CARD));
        var payment = paymentRepository.findByPublicId(init.paymentId()).orElseThrow();

        for (String eventId : List.of("evt_a", "evt_b", "evt_c")) {
            String body = "{\"eventId\":\"" + eventId + "\",\"orderId\":\"" + payment.getProviderOrderId() + "\",\"paymentRef\":\"p\",\"outcome\":\"SUCCESS\"}";
            paymentService.handleWebhook(PaymentProviderType.MOCK, body, Map.of(MockPaymentProvider.SIGNATURE_HEADER, mockProvider.signature(body)));
        }
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(ticketRepository.findAll().stream().filter(t -> t.getBooking().getId().equals(booking.id()))).hasSize(1);
    }

    @Test
    void forgedWebhookIsRejected() {
        String body = "{\"eventId\":\"evt_forged\",\"orderId\":\"x\",\"outcome\":\"SUCCESS\"}";
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        paymentService.handleWebhook(PaymentProviderType.MOCK, body, Map.of(MockPaymentProvider.SIGNATURE_HEADER, "bad")))
                .hasMessageContaining("signature");
    }

    @Test
    void failedPaymentKeepsBookingOpenForRetry() {
        Show show = fixtures.show(Duration.ofDays(2));
        User u = fixtures.user("USER");
        var lock = seatLockService.lockSeats(show.getId(), u.getId(), List.of("H9"));
        var booking = bookingService.createFromLock(u.getId(), new CreateBookingRequest(lock.lockId(), List.of()));
        var first = paymentService.initiate(u.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.CARD));
        var p1 = paymentRepository.findByPublicId(first.paymentId()).orElseThrow();
        paymentService.applyOutcome(p1.getId(), com.cineverse.payment.PaymentProvider.Outcome.FAILED, null, "Card declined", "TEST");
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);

        var second = paymentService.initiate(u.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.UPI));
        var p2 = paymentRepository.findByPublicId(second.paymentId()).orElseThrow();
        paymentService.applyOutcome(p2.getId(), com.cineverse.payment.PaymentProvider.Outcome.SUCCESS, "pay_ok", null, "TEST");
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void paymentArrivingAfterSeatsWereResoldIsRefunded(@Autowired RefundRepository refunds) {
        Show show = fixtures.show(Duration.ofDays(2));
        User slow = fixtures.user("USER");
        User fast = fixtures.user("USER");
        var lock = seatLockService.lockSeats(show.getId(), slow.getId(), List.of("J5"));
        var booking = bookingService.createFromLock(slow.getId(), new CreateBookingRequest(lock.lockId(), List.of()));
        var init = paymentService.initiate(slow.getId(), new InitiatePaymentRequest(booking.id(), PaymentMethod.UPI));
        clock.advance(Duration.ofMinutes(6));
        seatLockService.lockSeats(show.getId(), fast.getId(), List.of("J5")); // someone else takes the lapsed seat

        var payment = paymentRepository.findByPublicId(init.paymentId()).orElseThrow();
        paymentService.applyOutcome(payment.getId(), com.cineverse.payment.PaymentProvider.Outcome.SUCCESS, "late", null, "TEST");

        assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(refunds.findByPaymentId(payment.getId())).isPresent();
    }
}
