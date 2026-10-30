package com.cineverse.service;

import com.cineverse.entity.enums.NotificationType;
import com.cineverse.notification.NotificationService;
import com.cineverse.repository.BookingRepository;
import com.cineverse.repository.NotificationRepository;
import com.cineverse.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Show reminders (3 hours before) and watchlist "now in cinemas" alerts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJobs {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final BookingRepository bookingRepository;
    private final WatchlistRepository watchlistRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    @Transactional
    public void showReminders() {
        Instant now = Instant.now(clock);
        bookingRepository.confirmedForShowsStartingBetween(now, now.plus(Duration.ofHours(3))).forEach(b -> {
            String link = "/bookings/" + b.getId();
            Long userId = b.getUser().getId();
            if (!notificationRepository.existsByUserIdAndTypeAndLink(userId, NotificationType.SHOW_REMINDER, link)) {
                notificationService.notify(userId, NotificationType.SHOW_REMINDER, "Starting soon: " + b.getShow().getMovie().getTitle(),
                        b.getShow().getCinema().getName() + " · " + b.getShow().getScreen().getName() + " at " + TIME.format(b.getShow().getStartTime())
                                + ". Keep your QR ticket ready.", link);
            }
        });
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void watchlistReleases() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        watchlistRepository.dueReminders(today).forEach(w -> {
            notificationService.notify(w.getUserId(), NotificationType.PROMOTION, w.getMovie().getTitle() + " is now in cinemas",
                    "A movie on your watchlist has released. Book before the best seats go.", "/movies/" + w.getMovie().getId());
            w.setRemindedAt(Instant.now(clock));
        });
    }
}
