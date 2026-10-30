package com.cineverse.booking;

import com.cineverse.config.AppProperties;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.notification.NotificationService;
import com.cineverse.notification.OutboundEmail;
import com.cineverse.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the "Booking confirmed" email: order id, payment id, gateway transaction id, show details, seats, a price
 * breakdown and the entry QR code (embedded inline). All values are captured inside the confirming transaction and
 * the email is sent after it commits.
 */
@Component
@RequiredArgsConstructor
public class BookingEmails {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a").withZone(IST);
    private static final DateTimeFormatter PAID = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'").withZone(IST);

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final BookingPolicy policy;
    private final NotificationService notificationService;
    private final AppProperties props;

    /** Call inside the transaction that confirmed the booking. */
    public void sendConfirmation(Booking b, Payment p) {
        String site = props.frontendUrl() == null ? "" : props.frontendUrl().replaceAll("/$", "");
        boolean movie = b.getBookingType() == BookingType.MOVIE;
        String title = TicketService.title(b);
        Map<String, Object> m = new HashMap<>();
        m.put("name", b.getUser().getFullName());
        m.put("reference", b.getReference());
        m.put("title", title);
        m.put("totalPaid", inr(b.getGrandTotal()));
        m.put("when", WHEN.format(policy.startTime(b)));
        m.put("bookingUrl", site + "/bookings/" + b.getId());

        if (movie) {
            Show s = b.getShow();
            Movie mv = s.getMovie();
            m.put("subtitle", join(" · ", s.getLanguage(), s.getFormat() == null ? null : s.getFormat().replace('_', ' '), mv.getCertificate()));
            m.put("venue", s.getCinema().getName());
            m.put("venueAddress", join(" · ", s.getCinema().getAddress(), s.getScreen() == null ? null : s.getScreen().getName()));
            List<String> seats = b.getSeats().stream().map(BookingSeat::getSeatLabel).toList();
            m.put("seatsLabel", "Seats");
            m.put("seats", String.join(", ", seats));
            m.put("seatCount", seats.size());
            m.put("ticketWord", seats.size() == 1 ? "ticket" : "tickets");
            m.put("posterCell", posterCell(mv.getPosterUrl(), title));
        } else {
            Event e = b.getEventSession().getEvent();
            m.put("subtitle", join(" · ", e.getCategory() == null ? null : e.getCategory().getName(), e.getLanguages(), e.getAgeRestriction()));
            m.put("venue", e.getVenue().getName());
            m.put("venueAddress", e.getVenue().getAddress());
            int qty = b.getItems().stream().mapToInt(BookingItem::getQuantity).sum();
            m.put("seatsLabel", "Tickets");
            m.put("seats", b.getItems().stream().map(i -> i.getQuantity() + " × " + i.getCategoryName()).collect(Collectors.joining(", ")));
            m.put("seatCount", qty);
            m.put("ticketWord", qty == 1 ? "ticket" : "tickets");
            m.put("posterCell", posterCell(e.getPosterUrl(), title));
        }

        // payment & order
        m.put("paymentId", p.getPublicId().toString());
        m.put("transactionId", p.getProviderPaymentId() != null ? p.getProviderPaymentId()
                : p.getProviderOrderId() != null ? p.getProviderOrderId() : "—");
        m.put("paidVia", paidVia(p));
        m.put("paidAt", PAID.format(p.getCompletedAt() != null ? p.getCompletedAt() : Instant.now()));
        m.put("priceRows", priceRows(b));
        m.put("cancellationNote", cancellationNote(b));

        Map<String, byte[]> images = new HashMap<>();
        ticketRepository.findByBookingId(b.getId()).ifPresent(t -> {
            m.put("ticketCode", t.getTicketCode());
            if (t.getQrCode() != null) images.put("cv-qr", ticketService.qrPng(t.getQrCode().getQrToken()));
        });
        m.putIfAbsent("ticketCode", b.getReference());

        String subject = "Booking confirmed · " + title + " · " + b.getReference();
        notificationService.emailAfterCommit(new OutboundEmail(b.getUser().getEmail(), subject, "booking-confirmed", m, images));
    }

    private String cancellationNote(Booking b) {
        if (!policy.venueAllowsCancellation(b)) return "This booking can't be cancelled.";
        String deadline = WHEN.format(policy.cancellationDeadline(b));
        String fee = props.cancellation().refundConvenienceFee() ? "" : " The convenience fee is non-refundable.";
        return "You can cancel from My Bookings until " + deadline + "." + fee;
    }

    private static String paidVia(Payment p) {
        String gateway = switch (p.getProvider() == null ? PaymentProviderType.MOCK : p.getProvider()) {
            case RAZORPAY -> "Razorpay";
            case STRIPE -> "Stripe";
            case PHONEPE -> "PhonePe";
            case VENUE -> "Box office";
            case MOCK -> "Test gateway";
        };
        String method = p.getMethod() == null ? "" : switch (p.getMethod()) {
            case UPI -> "UPI";
            case GPAY -> "Google Pay (UPI)";
            case PHONEPE -> "PhonePe";
            case CARD -> "Card";
            case NETBANKING -> "Net banking";
            case WALLET -> "Wallet";
            case PAY_AT_VENUE -> "Paid at venue";
        };
        return method.isEmpty() ? gateway : gateway + " · " + method;
    }

    private static String priceRows(Booking b) {
        List<String[]> rows = new ArrayList<>();
        if (b.getBookingType() == BookingType.MOVIE) {
            // group seats by type: "Tickets (2 × Gold)"
            Map<String, List<BookingSeat>> byType = b.getSeats().stream()
                    .collect(Collectors.groupingBy(s -> s.getSeatType() == null ? "Standard" : s.getSeatType(), LinkedHashMap::new, Collectors.toList()));
            byType.forEach((type, seats) -> rows.add(new String[]{"Tickets (" + seats.size() + " × " + pretty(type) + ")",
                    inr(seats.stream().map(BookingSeat::getPrice).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)), null}));
        } else {
            for (BookingItem i : b.getItems()) {
                rows.add(new String[]{i.getQuantity() + " × " + i.getCategoryName(),
                        inr(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))), null});
            }
        }
        if (b.getFoodSubtotal() != null && b.getFoodSubtotal().signum() > 0) {
            String items = b.getFoodOrder() == null ? "" : b.getFoodOrder().getItems().stream()
                    .map(f -> f.getQuantity() + " × " + f.getItemName()).collect(Collectors.joining(", "));
            rows.add(new String[]{items.isEmpty() ? "Food & beverages" : "Food & beverages (" + items + ")", inr(b.getFoodSubtotal()), null});
        }
        if (b.getConvenienceFee() != null && b.getConvenienceFee().signum() > 0) rows.add(new String[]{"Convenience fee", inr(b.getConvenienceFee()), null});
        if (b.getTax() != null && b.getTax().signum() > 0) rows.add(new String[]{"GST on convenience fee", inr(b.getTax()), null});
        if (b.getDiscount() != null && b.getDiscount().signum() > 0) {
            rows.add(new String[]{b.getCouponCode() == null ? "Discount" : "Coupon " + b.getCouponCode(), "− " + inr(b.getDiscount()), "#16A34A"});
        }
        StringBuilder html = new StringBuilder();
        for (String[] r : rows) {
            html.append("<tr><td style=\"color:#776B62;padding:3px 12px 3px 0;\">").append(esc(r[0]))
                    .append("</td><td align=\"right\" style=\"color:").append(r[2] == null ? "#211A15" : r[2])
                    .append(";padding:3px 0;white-space:nowrap;\">").append(esc(r[1])).append("</td></tr>");
        }
        return html.toString();
    }

    /** Left column with the poster when it's a public image (TMDB etc.); local SVG art is skipped (mail clients block SVG). */
    private static String posterCell(String posterUrl, String title) {
        if (posterUrl == null || !posterUrl.startsWith("https://") || posterUrl.endsWith(".svg")) return "";
        return "<td width=\"120\" valign=\"top\" class=\"hide-sm\" style=\"padding:18px 0 18px 18px;\"><img src=\""
                + esc(posterUrl) + "\" width=\"120\" alt=\"" + esc(title + " poster")
                + "\" style=\"display:block;border:0;width:120px;height:auto;border-radius:10px;\"></td>";
    }

    private static String pretty(String code) {
        String s = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(String sep, String... parts) {
        return Arrays.stream(parts).filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(sep));
    }

    static String inr(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "₹" + f.format(v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP));
    }

    /** Escapes only HTML-special characters (&lt; &gt; &amp; quotes); ₹, · and accented letters stay readable in UTF-8 mail. */
    static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
