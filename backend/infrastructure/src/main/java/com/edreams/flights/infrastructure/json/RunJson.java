package com.edreams.flights.infrastructure.json;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.ingestion.ProviderResult;
import com.edreams.flights.domain.model.ProviderId;

public record RunJson(
        UUID id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        Map<String, ProviderResultJson> providers,
        Integer itineraries,
        String error) {

    public static RunJson from(IngestionRun run) {
        return new RunJson(run.id().value(), run.startedAt(), run.finishedAt(), run.status().name(),
                providersFrom(run.providers()), run.itineraries(), run.error());
    }

    public static Map<String, ProviderResultJson> providersFrom(Map<ProviderId, ProviderResult> providers) {
        Map<String, ProviderResultJson> json = new LinkedHashMap<>();
        providers.forEach((provider, result) -> json.put(provider.name(), ProviderResultJson.from(result)));
        return json;
    }

    public static Map<ProviderId, ProviderResult> providersTo(Map<String, ProviderResultJson> json) {
        Map<ProviderId, ProviderResult> providers = new EnumMap<>(ProviderId.class);
        if (json != null) {
            json.forEach((provider, result) -> providers.put(ProviderId.valueOf(provider), result.toResult()));
        }
        return providers;
    }

    public IngestionRun toRun() {
        return new IngestionRun(new IngestionRunId(id), startedAt, finishedAt, IngestionRun.Status.valueOf(status),
                providersTo(providers), itineraries, error);
    }
}
