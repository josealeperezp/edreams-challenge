/**
 * Application layer: use cases (inbound ports) and the outbound ports they need
 * (provider gateway, cache, clock, event publication).
 * <p>
 * Rules: depends on the domain and the JDK only. Orchestration and concurrency
 * live here using {@code java.util.concurrent}; no Spring, Jakarta or Jackson.
 * Adapters in the infrastructure module implement the outbound ports.
 */
package com.edreams.flights.application;
