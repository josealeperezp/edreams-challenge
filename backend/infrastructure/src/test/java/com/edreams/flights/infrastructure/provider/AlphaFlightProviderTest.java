package com.edreams.flights.infrastructure.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.infrastructure.provider.ProviderPayloads.RecordingSink;
import com.edreams.flights.infrastructure.provider.alpha.AlphaFlightProvider;
import com.edreams.flights.infrastructure.provider.http.ProviderCallException;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class AlphaFlightProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private AlphaFlightProvider provider() {
        return new AlphaFlightProvider(ProviderHttpClients.build(wm.baseUrl() + "/alpha", Duration.ofSeconds(2)));
    }

    @Test
    void streamsOffersAndReportsTheUndecodableOne() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson(ProviderPayloads.ALPHA_JSON)));
        RecordingSink sink = new RecordingSink();

        provider().fetch(sink);

        assertThat(sink.offers).hasSize(2);
        RawOffer first = sink.offers.getFirst();
        assertThat(first.provider()).isEqualTo(ProviderId.ALPHA);
        assertThat(first.providerOfferId()).isEqualTo("ALPHA-000001");
        assertThat(first.price()).isEqualByComparingTo(new BigDecimal("89.9"));
        assertThat(first.currency()).isEqualTo("EUR");
        assertThat(first.segments().getFirst().flightNumber()).isEqualTo("0845");
        assertThat(first.segments().getFirst().departure()).isEqualTo(OffsetDateTime.parse("2026-11-02T07:15:00+01:00"));
        assertThat(sink.offers.get(1).segments()).hasSize(2);
        assertThat(sink.offers.get(1).segments().get(1).origin()).isEqualTo("LHR");
        assertThat(sink.malformed).singleElement().asString().contains("ALPHA-000003").contains("not-a-date");
    }

    @Test
    void serverErrorBeforeTheBodyIsAProviderCall() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":\"upstream failure\",\"code\":500}")));

        assertThatThrownBy(() -> provider().fetch(new RecordingSink()))
                .isInstanceOf(ProviderCallException.class)
                .satisfies(e -> assertThat(((ProviderCallException) e).status()).isEqualTo(500));
    }

    @Test
    void aBodyThatIsNotTheContractIsAParseFailure() {
        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson("{\"not\":\"an array\"}")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink())).isInstanceOf(ProviderParseException.class);

        wm.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson("[{\"offerId\":\"x\",")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink())).isInstanceOf(ProviderParseException.class);
    }
}
