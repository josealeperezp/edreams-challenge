package com.edreams.flights.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.edreams.flights.application.ingestion.IngestionAlreadyRunning;
import com.edreams.flights.application.port.in.RunIngestion;

/** Starts a run when the application is ready, so the stack never boots empty, and on the daily cron. */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final RunIngestion runIngestion;
    private final FlightsProperties properties;

    public IngestionScheduler(RunIngestion runIngestion, FlightsProperties properties) {
        this.runIngestion = runIngestion;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.ingestion().runOnStartup()) {
            trigger("startup");
        }
    }

    @Scheduled(cron = "${flights.ingestion.cron:0 0 3 * * *}")
    public void onSchedule() {
        trigger("schedule");
    }

    private void trigger(String source) {
        try {
            log.info("Ingestion run {} started by {}", runIngestion.start(), source);
        } catch (IngestionAlreadyRunning e) {
            log.info("Ingestion by {} skipped: {}", source, e.getMessage());
        }
    }
}
