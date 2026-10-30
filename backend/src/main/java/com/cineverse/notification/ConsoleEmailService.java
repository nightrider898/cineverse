package com.cineverse.notification;

import com.cineverse.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Development provider: nothing is sent. Each email is rendered exactly as SMTP would send it and written to
 * {@code cineverse.email.outbox-dir} (default ./email-outbox) as an .html file with its images, so you can open it
 * in a browser. The plain-text version is logged.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "cineverse.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailService implements EmailService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final EmailRenderer renderer;
    private final Path outbox;

    public ConsoleEmailService(EmailRenderer renderer, AppProperties props) {
        this.renderer = renderer;
        String dir = props.email() == null || props.email().outboxDir() == null ? "email-outbox" : props.email().outboxDir();
        this.outbox = Path.of(dir).toAbsolutePath();
    }

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
            String name = LocalDateTime.now().format(STAMP) + "-" + email.template();
            Path dir = outbox.resolve(name);
            Files.createDirectories(dir);
            String html = r.html();
            for (Map.Entry<String, byte[]> img : r.inlineImages().entrySet()) {
                Files.write(dir.resolve(img.getKey() + ".png"), img.getValue());
            }
            // longest ids first so "cv-logo" never clobbers "cv-logo-light"
            for (String cid : r.inlineImages().keySet().stream().sorted((a, b) -> b.length() - a.length()).toList()) {
                html = html.replace("cid:" + cid, cid + ".png");
            }
            Path file = dir.resolve("email.html");
            Files.writeString(file, html);
            log.info("\n====== EMAIL (console provider, not sent) ======\nTo: {}\nSubject: {}\nPreview: {}\n\n{}\n================================================",
                    email.to(), email.subject(), file.toUri(), r.text());
        } catch (Exception e) {
            log.error("email_render_failed template={} error={}", email.template(), e.getMessage());
        }
    }
}
