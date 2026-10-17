package com.edreams.flights.application.ingestion;

import com.edreams.flights.domain.model.ProviderId;

/** Progress notification published while a run executes; the SSE endpoint relays it to the dashboard. */
public record IngestionEvent(Type type, IngestionRun run, ProviderId provider) {

    public enum Type { RUN_STARTED, PROVIDER_FINISHED, RUN_COMPLETED }

    public static IngestionEvent runStarted(IngestionRun run) {
        return new IngestionEvent(Type.RUN_STARTED, run, null);
    }

    public static IngestionEvent providerFinished(IngestionRun run, ProviderId provider) {
        return new IngestionEvent(Type.PROVIDER_FINISHED, run, provider);
    }

    public static IngestionEvent runCompleted(IngestionRun run) {
        return new IngestionEvent(Type.RUN_COMPLETED, run, null);
    }
}
