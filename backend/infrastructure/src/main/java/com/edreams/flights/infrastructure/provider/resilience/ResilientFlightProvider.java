package com.edreams.flights.infrastructure.provider.resilience;

import java.util.function.Predicate;

import org.springframework.web.client.ResourceAccessException;

import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.infrastructure.provider.http.ProviderCallException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Wraps a provider adapter with retry and a circuit breaker, applied programmatically so no
 * framework annotation is needed. Transient failures (5xx, connection and timeout errors) are
 * retried with exponential backoff; parse errors and 4xx are not. When the breaker is open the
 * call fails immediately, which keeps a dead provider from slowing the whole run.
 */
public final class ResilientFlightProvider implements FlightProviderPort {

    private static final Predicate<Throwable> TRANSIENT = e ->
            e instanceof ResourceAccessException
                    || (e instanceof ProviderCallException call && call.serverError());

    private final FlightProviderPort delegate;
    private final CircuitBreaker breaker;
    private final Retry retry;

    public ResilientFlightProvider(FlightProviderPort delegate, ResilienceSettings settings) {
        this.delegate = delegate;
        this.breaker = CircuitBreaker.of(delegate.id().name().toLowerCase(), CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.breakerWindow())
                .minimumNumberOfCalls(settings.breakerWindow())
                .failureRateThreshold(settings.breakerFailureRatePercent())
                .waitDurationInOpenState(settings.breakerOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
        this.retry = Retry.of(delegate.id().name().toLowerCase(), RetryConfig.custom()
                .maxAttempts(settings.retryAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(settings.retryInitialWait(), 2.0))
                .retryOnException(TRANSIENT)
                .ignoreExceptions(CallNotPermittedException.class)
                .build());
    }

    @Override
    public ProviderId id() {
        return delegate.id();
    }

    @Override
    public void fetch(OfferSink sink) {
        Runnable call = CircuitBreaker.decorateRunnable(breaker, () -> delegate.fetch(sink));
        Retry.decorateRunnable(retry, call).run();
    }

    public CircuitBreaker.State circuitState() {
        return breaker.getState();
    }

    public CircuitBreaker.Metrics circuitMetrics() {
        return breaker.getMetrics();
    }
}
