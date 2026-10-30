package com.cineverse.media;

import com.cineverse.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the poster/trailer sync shortly after startup (in the background) and again every night. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaSyncJob {
    private final MediaSyncService mediaSyncService;
    private final TmdbClient tmdb;
    private final AppProperties props;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.media() == null || !props.media().syncOnStartup()) return;
        if (!tmdb.enabled()) {
            log.info("media_sync_skipped reason=\"TMDB_API_KEY not set: posters and trailers will not be fetched\"");
            return;
        }
        Thread.ofVirtual().name("media-sync").start(() -> {
            try {
                Thread.sleep(15_000); // let the demo seeder and caches settle first
                mediaSyncService.syncPending(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("media_sync_failed reason={}", e.getMessage());
            }
        });
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        if (tmdb.enabled()) mediaSyncService.syncPending(false);
    }
}
