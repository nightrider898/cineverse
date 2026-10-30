package com.cineverse.notification;

import com.cineverse.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends real email over SMTP (EMAIL_HOST / EMAIL_PORT / EMAIL_USERNAME / EMAIL_PASSWORD), for example Gmail with an
 * app password, Brevo, SendGrid, Amazon SES or Zoho. Messages are multipart: HTML + plain text, with the logo,
 * banner and QR code embedded inline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cineverse.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final EmailRenderer renderer;
    private final AppProperties props;

    @Async
    @Override
    public void send(String to, String subject, String template, Map<String, Object> model) {
        send(new OutboundEmail(to, subject, template, model));
    }

    @Async
    @Override
    public void send(OutboundEmail email) {
        try {
            EmailRenderer.Rendered r = renderer.render(email);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(props.email().from());
            helper.setTo(email.to());
            helper.setSubject(r.subject());
            helper.setText(r.text(), r.html());
            for (Map.Entry<String, byte[]> img : r.inlineImages().entrySet()) {
                helper.addInline(img.getKey(), new ByteArrayResource(img.getValue()), "image/png");
            }
            mailSender.send(msg);
            log.info("email_sent template={} to={}", email.template(), mask(email.to()));
        } catch (Exception e) {
            log.error("email_failed template={} to={} error={}", email.template(), mask(email.to()), e.getMessage());
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
