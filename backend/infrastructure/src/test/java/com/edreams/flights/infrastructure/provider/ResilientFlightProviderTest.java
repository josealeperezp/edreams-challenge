package com.edreams.flights.infrastructure.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.ResourceAccessException;

import com.edreams.flights.infrastructure.provider.ProviderPayloads.RecordingSink;
import com.edreams.flights.infrastructure.provider.alpha.AlphaFlightProvider;
import com.edreams.flights.infrastructure.provider.http.ProviderCallException;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;
import com.edreams.flights.infrastructure.provider.resilience.ResilienceSettings;
import com.edreams.flights.infrastructure.provider.resilience.ResilientFlightProvider;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class ResilientFlightProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String OK_BODY = "[{\"offerId\":\"ALPHA-1\",\"price\":1.0,\"currency\":\"EUR\",\"legs\":[]}]";

    @BeforeEach
    void reset() {
        wm.resetAll();
    }

    private ResilientFlightProvider provider(ResilienceSettings settings, Duration readTimeout) {
        return new ResilientFlightProvider(
                new AlphaFlightProvider(ProviderHttpClients.build(wm.baseUrl() + "/alpha", readTimeout)), settings);
    }

    private static ResilienceSettings settings(int attempts, int window) {
        return new ResilienceSettings(attempts, Duration.ofMillis(10), window, 50f, Duration.ofSeconds(30));
    }

    @Test
    void retriesAServerErrorAndSucceedsOnTheNextAttempt() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500)).willSetStateTo("recovered"));
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson(OK_BODY)));
        RecordingSink sink = new RecordingSink();

        provider(settings(3, 4), Duration.ofSeconds(2)).fetch(sink);

        assertThat(sink.offers).hasSize(1);
        wm.verify(2, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void doesNotRetryAParseFailure() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson("{\"nope\":1}")));

        assertThatThrownBy(() -> provider(settings(3, 4), Duration.ofSeconds(2)).fetch(new RecordingSink()))
                .isInstanceOf(ProviderParseException.class);
        wm.verify(1, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void retriesATimeoutThenGivesUp() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson(OK_BODY).withFixedDelay(1500)));

        assertThatThrownBy(() -> provider(settings(2, 4), Duration.ofMillis(200)).fetch(new RecordingSink()))
                .isInstanceOf(ResourceAccessException.class);
        wm.verify(2, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void opensTheCircuitAfterRepeatedFailuresAndFailsFast() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(502)));
        ResilientFlightProvider provider = provider(settings(1, 2), Duration.ofSeconds(2));

        assertThatThrownBy(() -> provider.fetch(new RecordingSink())).isInstanceOf(ProviderCallException.class);
        assertThatThrownBy(() -> provider.fetch(new RecordingSink())).isInstanceOf(ProviderCallException.class);
        assertThat(provider.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> provider.fetch(new RecordingSink())).isInstanceOf(CallNotPermittedException.class);
        wm.verify(2, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }
}
