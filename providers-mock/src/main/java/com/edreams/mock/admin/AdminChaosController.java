package com.edreams.mock.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.edreams.mock.chaos.ChaosRegistry;
import com.edreams.mock.chaos.ChaosSettings;
import com.edreams.mock.inventory.ProviderId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Runtime control of latency and failure injection, so a provider can be broken and healed during a demo. */
@RestController
@RequestMapping("/admin/chaos")
public class AdminChaosController {

    private final ChaosRegistry registry;

    public AdminChaosController(ChaosRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, ChaosSettings> all() {
        return registry.all();
    }

    @PutMapping("/{provider}")
    public ChaosSettings update(@PathVariable String provider, @Valid @RequestBody ChaosRequest request) {
        return registry.update(resolve(provider), request.toSettings());
    }

    @DeleteMapping("/{provider}")
    public ChaosSettings reset(@PathVariable String provider) {
        return registry.reset(resolve(provider));
    }

    private static ProviderId resolve(String provider) {
        return ProviderId.fromPath(provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider: " + provider));
    }

    public record ChaosRequest(
            @NotNull Boolean enabled,
            @Min(0) @Max(ChaosSettings.MAX_LATENCY_MS) int latencyMinMs,
            @Min(0) @Max(ChaosSettings.MAX_LATENCY_MS) int latencyMaxMs,
            @DecimalMin("0.0") @DecimalMax("1.0") double errorRate) {

        ChaosSettings toSettings() {
            return new ChaosSettings(enabled, latencyMinMs, latencyMaxMs, errorRate);
        }
    }
}
