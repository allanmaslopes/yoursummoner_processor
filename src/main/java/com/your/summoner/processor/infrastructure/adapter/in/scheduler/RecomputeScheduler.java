package com.your.summoner.processor.infrastructure.adapter.in.scheduler;

import com.your.summoner.processor.application.service.RecomputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "processor.enabled", havingValue = "true", matchIfMissing = true)
public final class RecomputeScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RecomputeScheduler.class);
    private final RecomputeService service;

    public RecomputeScheduler(RecomputeService service) { this.service = service; }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() { Thread.ofVirtual().name("initial-recompute").start(this::recompute); }

    @Scheduled(cron = "${processor.cron:0 15 * * * *}")
    public void recompute() {
        try {
            service.recompute();
        } catch (RuntimeException failure) {
            LOG.warn("Recompute failed: {}", failure.toString());
        }
    }
}
