package com.edreams.flights.infrastructure.web;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edreams.flights.infrastructure.config.ProvidersProperties;

/** Exposes what this instance is and which providers it is wired to. Used by the dashboard header. */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private final String applicationName;
    private final ProvidersProperties providers;
    private final ObjectProvider<BuildProperties> build;

    public SystemInfoController(
            @Value("${spring.application.name:flights-backend}") String applicationName,
            ProvidersProperties providers,
            ObjectProvider<BuildProperties> build) {
        this.applicationName = applicationName;
        this.providers = providers;
        this.build = build;
    }

    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public SystemInfo info() {
        var buildProperties = build.getIfAvailable();
        var version = buildProperties == null ? "dev" : buildProperties.getVersion();
        List<ProviderInfo> providerInfos = providers.byId().entrySet().stream()
                .map(e -> new ProviderInfo(e.getKey().name(), e.getValue().baseUrl(), e.getValue().timeout().toMillis()))
                .toList();
        return new SystemInfo(applicationName, version, providerInfos);
    }

    public record SystemInfo(String application, String version, List<ProviderInfo> providers) {}

    public record ProviderInfo(String id, String baseUrl, long timeoutMillis) {}
}
