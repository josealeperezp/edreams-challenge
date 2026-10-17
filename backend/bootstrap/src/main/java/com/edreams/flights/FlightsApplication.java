package com.edreams.flights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Composition root. Lives in the root package so component scanning covers every module;
 * wiring of domain and application beans lives in {@code com.edreams.flights.bootstrap}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FlightsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightsApplication.class, args);
    }
}
