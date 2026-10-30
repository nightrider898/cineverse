package com.cineverse.booking;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.BookingDtos.TicketResponse;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Ticket;
import com.cineverse.entity.TicketQrCode;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.TicketStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.TicketRepository;
import com.cineverse.service.AuditService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Issues tickets and QR tokens and validates them at the gate.
 * QR payload format: {@code CV1.<ticketCode>.<hmac>} — an opaque, signed identifier with no payment data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    public record ValidationResult(String result, String message, String bookingReference, String title, String venue,
                                   String screen, Instant startTime, List<String> seats, int admits, Instant usedAt) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Ticket issue(Booking booking) {
        return ticketRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            Ticket t = new Ticket();
            t.setBooking(booking);
            t.setTicketCode(randomCode(10));
            TicketQrCode qr = new TicketQrCode();
            qr.setTicket(t);
            qr.setQrToken("CV1." + t.getTicketCode() + "." + sign(t.getTicketCode()));
            t.setQrCode(qr);
            ticketRepository.save(t);
            log.info("ticket_issued bookingId={} ticket={}", booking.getId(), t.getTicketCode());
            return t;
        });
    }

    public TicketResponse toResponse(Ticket t, boolean includeImage) {
        String token = t.getQrCode() == null ? null : t.getQrCode().getQrToken();
        return new TicketResponse(t.getTicketCode(), t.getStatus().name(), token,
                includeImage && token != null ? qrDataUri(token) : null, t.getIssuedAt(), t.getUsedAt());
    }

    @Transactional
    public ValidationResult validate(String rawToken, Long staffUserId) {
        String token = rawToken == null ? "" : rawToken.trim();
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !"CV1".equals(parts[0]) || !constantTimeEquals(sign(parts[1]), parts[2])) {
            return new ValidationResult("INVALID", "This QR code is not a CineVerse ticket", null, null, null, null, null, List.of(), 0, null);
        }
        Ticket t = ticketRepository.findByQrTokenForUpdate(token).orElse(null);
        if (t == null) {
            return new ValidationResult("INVALID", "Ticket not found", null, null, null, null, null, List.of(), 0, null);
        }
        Booking b = t.getBooking();
        Instant now = Instant.now(clock);
        Instant start = b.getBookingType() == BookingType.MOVIE ? b.getShow().getStartTime() : b.getEventSession().getStartTime();
        Instant end = b.getBookingType() == BookingType.MOVIE ? b.getShow().getEndTime() : b.getEventSession().getEndTime();

        String result;
        String message;
        if (t.getStatus() == TicketStatus.USED) {
            result = "ALREADY_USED";
            message = "Ticket was already scanned at " + t.getUsedAt();
        } else if (t.getStatus() == TicketStatus.CANCELLED || b.getStatus() != BookingStatus.CONFIRMED) {
            result = "CANCELLED";
            message = "This booking is not active";
        } else if (now.isAfter(end)) {
            t.setStatus(TicketStatus.EXPIRED);
            result = "EXPIRED";
            message = "This show has already ended";
        } else if (now.isBefore(start.minus(Duration.ofHours(3)))) {
            result = "TOO_EARLY";
            message = "Entry opens 3 hours before the start time";
        } else {
            t.setStatus(TicketStatus.USED);
            t.setUsedAt(now);
            t.setValidatedBy(staffUserId);
            result = "VALID";
            message = "Admit " + b.ticketCount();
            auditService.record(staffUserId, "TICKET_VALIDATED", "TICKET", t.getTicketCode(), Map.of("bookingId", b.getId()));
        }
        return new ValidationResult(result, message, b.getReference(), title(b), venue(b),
                b.getBookingType() == BookingType.MOVIE ? b.getShow().getScreen().getName() : null, start,
                b.getSeats().stream().map(s -> s.getSeatLabel()).toList(), b.ticketCount(), t.getUsedAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Long bookingId) {
        ticketRepository.findByBookingId(bookingId).ifPresent(t -> t.setStatus(TicketStatus.CANCELLED));
    }

    public String qrDataUri(String token) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng(token));
    }

    /** PNG bytes of the entry QR (also embedded inline in the confirmation email). */
    public byte[] qrPng(String token) {
        try {
            var matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 360, 360,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("QR generation failed", e);
        }
    }

    private String sign(String ticketCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String secret = props.qr() == null || props.qr().secret() == null || props.qr().secret().isBlank()
                    ? props.jwt().secret() : props.qr().secret();
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(ticketCode.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder("TKT-");
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    static String title(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getMovie().getTitle() : b.getEventSession().getEvent().getTitle();
    }

    static String venue(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getCinema().getName() : b.getEventSession().getEvent().getVenue().getName();
    }
}
