package com.cineverse.notification;

import java.util.Map;

/**
 * A transactional email before rendering.
 *
 * @param template     template name (welcome, signin-alert, booking-confirmed, password-reset, booking-cancelled, refund-update)
 * @param model        template values; plain values are HTML-escaped when rendered
 * @param inlineImages extra images referenced as {@code cid:<key>} (e.g. the ticket QR code), PNG bytes
 */
public record OutboundEmail(String to, String subject, String template, Map<String, Object> model, Map<String, byte[]> inlineImages) {
    public OutboundEmail {
        // HashMap copies (not Map.copyOf): template values may legitimately be null
        model = model == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.HashMap<>(model));
        inlineImages = inlineImages == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(inlineImages));
    }

    public OutboundEmail(String to, String subject, String template, Map<String, Object> model) {
        this(to, subject, template, model, null);
    }
}
