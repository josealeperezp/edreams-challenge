package com.edreams.flights.infrastructure;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal configuration so slice tests in this module can run without the bootstrap module.
 * The component scan is required: {@code @WebMvcTest} narrows scanning to web beans, it does not
 * register controllers by itself.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class InfrastructureTestApplication {
}
