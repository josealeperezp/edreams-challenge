package com.edreams.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Simulates the three legacy GDS providers behind distinct paths: {@code /alpha} streams JSON,
 * {@code /beta} nested XML, {@code /gamma} CSV. Inventory is generated deterministically from a seed;
 * latency and failures are injected by {@link com.edreams.mock.chaos.ChaosFilter} and controlled at
 * runtime through {@code /admin/chaos}. This application is tooling to exercise the real system and
 * carries no test suite by decision.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MockProvidersApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockProvidersApplication.class, args);
    }
}
