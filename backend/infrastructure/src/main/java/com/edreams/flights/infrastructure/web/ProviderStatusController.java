package com.edreams.flights.infrastructure.web;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.ProviderResult;
import com.edreams.flights.application.port.in.IngestionStatus;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.infrastructure.config.ProvidersProperties;
import com.edreams.flights.infrastructure.json.ProviderResultJson;
import com.edreams.flights.infrastructure.provider.resilience.ResilientProviders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Circuit state and last run outcome per provider, for the dashboard's health panel. */
@RestController
@RequestMapping("/api/providers")
public class ProviderStatusController {

    private final ResilientProviders providers;
    private final ProvidersProperties properties;
    private final IngestionStatus status;

    public ProviderStatusController(ResilientProviders providers, ProvidersProperties properties, IngestionStatus status) {
        this.providers = providers;
        this.properties = properties;
        this.status = status;
    }

    public record ProviderStatusJson(
            String id,
            String baseUrl,
            String circuit,
            float failureRate,
            int bufferedCalls,
            ProviderResultJson lastResult) {
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProviderStatusJson> status() {
        Map<ProviderId, ProviderResult> lastRun = status.latest().map(IngestionRun::providers).orElse(Map.of());
        return providers.all().stream().map(p -> new ProviderStatusJson(
                p.id().name(),
                properties.byId().get(p.id()).baseUrl(),
                p.circuitState().name(),
                p.circuitMetrics().getFailureRate(),
                p.circuitMetrics().getNumberOfBufferedCalls(),
                lastRun.containsKey(p.id()) ? ProviderResultJson.from(lastRun.get(p.id())) : null)).toList();
    }
}
