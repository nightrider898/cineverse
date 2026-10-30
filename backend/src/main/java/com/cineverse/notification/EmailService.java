package com.cineverse.notification;

import java.util.Map;

/** Provider abstraction for transactional email. Implementations render through {@link EmailRenderer}. */
public interface EmailService {
    void send(OutboundEmail email);

    default void send(String to, String subject, String template, Map<String, Object> model) {
        send(new OutboundEmail(to, subject, template, model));
    }
}
