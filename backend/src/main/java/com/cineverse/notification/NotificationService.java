package com.cineverse.notification;

import com.cineverse.dto.EngagementDtos.NotificationResponse;
import com.cineverse.entity.Notification;
import com.cineverse.entity.enums.NotificationType;
import com.cineverse.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final EmailService emailService;
    private final Clock clock;

    @Transactional
    public void notify(Long userId, NotificationType type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body.length() > 500 ? body.substring(0, 497) + "..." : body);
        n.setLink(link);
        repository.save(n);
    }

    /** Sends email only once the surrounding transaction commits, so we never email about rolled-back work. */
    public void emailAfterCommit(String to, String subject, String template, Map<String, Object> model) {
        emailAfterCommit(new OutboundEmail(to, subject, template, model));
    }

    /** The model must already be fully built (plain values): it is rendered after the transaction has ended. */
    public void emailAfterCommit(OutboundEmail email) {
        if (email.to() == null || email.to().isBlank()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailService.send(email);
                }
            });
        } else {
            emailService.send(email);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 50))).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repository.markRead(id, userId, Instant.now(clock));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now(clock));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null, n.getCreatedAt());
    }
}
