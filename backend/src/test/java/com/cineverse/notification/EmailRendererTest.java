package com.cineverse.notification;

import com.cineverse.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the real classpath templates with brand settings; no Spring context, no SMTP. */
class EmailRendererTest {
    private final AppProperties props = new AppProperties("BookMyEvent", "http://localhost:3000", "Asia/Kolkata", null, null, null, null, null, null,
            null, null, null, null, new AppProperties.Email("console", "BookMyEvent <no-reply@test>", true, "target/email-outbox"), null, null, null,
            new AppProperties.Brand("BookMyEvent", "BookMyEvent Entertainment Pvt. Ltd.", "Every seat tells a story.", "help@bookmyevent.test",
                    "+91 40 4000 1234", "Hyderabad", "https://instagram.com/bme", null, null, "https://youtube.com/@bme", null));
    private final EmailRenderer renderer = new EmailRenderer(props);

    @Test
    void welcomeEmailIsBrandedAndEscapesUserInput() {
        var r = renderer.render(new OutboundEmail("a@b.test", "Welcome to BookMyEvent, Asha!", "welcome",
                Map.of("name", "Asha <script>alert(1)</script> {{supportEmail}}", "fullName", "Asha Rao", "email", "a@b.test",
                        "memberSince", "30 October 2026", "cityName", "Hyderabad")));
        assertThat(r.html()).contains("Welcome to BookMyEvent, Asha").contains("cid:cv-logo").contains("cid:cv-banner-welcome")
                .contains("Every seat tells a story.").contains("https://instagram.com/bme").contains("help@bookmyevent.test")
                .doesNotContain("<script>").doesNotContain("{{");
        assertThat(r.inlineImages()).containsKeys("cv-logo", "cv-logo-light", "cv-banner-welcome");
        assertThat(r.text()).contains("Thank you for registering");
    }

    @Test
    void bookingEmailCarriesOrderPaymentAndTransactionIds() {
        var r = renderer.render(new OutboundEmail("a@b.test", "Booking confirmed", "booking-confirmed",
                Map.ofEntries(Map.entry("name", "Asha Rao"), Map.entry("reference", "CV-2026-000214"), Map.entry("title", "Peddi"),
                        Map.entry("totalPaid", "₹770.00"), Map.entry("paymentId", "6f1c…"), Map.entry("transactionId", "pay_PQx81LmZt0aK3e"),
                        Map.entry("paidVia", "Razorpay · UPI"), Map.entry("priceRows", "<tr><td>Tickets</td><td>₹540.00</td></tr>")),
                Map.of("cv-qr", "png".getBytes(StandardCharsets.UTF_8))));
        assertThat(r.html()).contains("CV-2026-000214").contains("pay_PQx81LmZt0aK3e").contains("Razorpay · UPI").contains("cid:cv-qr")
                .contains("<tr><td>Tickets</td><td>₹540.00</td></tr>");
        assertThat(r.inlineImages()).containsKey("cv-qr");
    }

    @Test
    void noticeTemplatesRenderForExistingCallers() {
        var r = renderer.render(new OutboundEmail("a@b.test", "Reset", "password-reset", Map.of("name", "Asha", "link", "http://x/reset?token=1")));
        assertThat(r.html()).contains("Reset your password").contains("http://x/reset?token=1");
    }
}
