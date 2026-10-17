package com.edreams.flights.application.ingestion;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.edreams.flights.domain.model.ProviderId;

/** One ingestion run and its per-provider outcome. Immutable; transitions return a new value. */
public record IngestionRun(
        IngestionRunId id,
        Instant startedAt,
        Instant finishedAt,
        Status status,
        Map<ProviderId, ProviderResult> providers,
        Integer itineraries,
        String error) {

    public enum Status { RUNNING, COMPLETED, PARTIAL, FAILED }

    public IngestionRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(status, "status");
        var copy = new EnumMap<ProviderId, ProviderResult>(ProviderId.class);
        if (providers != null) {
            copy.putAll(providers);
        }
        providers = Collections.unmodifiableMap(copy);
    }

    public static IngestionRun started(IngestionRunId id, Instant at) {
        return new IngestionRun(id, at, null, Status.RUNNING, Map.of(), null, null);
    }

    public IngestionRun withProvider(ProviderId provider, ProviderResult result) {
        var updated = new EnumMap<ProviderId, ProviderResult>(ProviderId.class);
        updated.putAll(providers);
        updated.put(provider, result);
        return new IngestionRun(id, startedAt, finishedAt, status, updated, itineraries, error);
    }

    public IngestionRun finished(Status finalStatus, int resolvedItineraries, Instant at) {
        return new IngestionRun(id, startedAt, at, finalStatus, providers, resolvedItineraries, error);
    }

    public IngestionRun failed(String reason, Instant at) {
        return new IngestionRun(id, startedAt, at, Status.FAILED, providers, itineraries, reason);
    }

    public boolean running() {
        return status == Status.RUNNING;
    }
}
