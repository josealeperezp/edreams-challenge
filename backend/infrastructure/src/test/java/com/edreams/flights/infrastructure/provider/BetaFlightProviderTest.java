package com.edreams.flights.infrastructure.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.infrastructure.provider.ProviderPayloads.RecordingSink;
import com.edreams.flights.infrastructure.provider.beta.BetaFlightProvider;
import com.edreams.flights.infrastructure.provider.http.ProviderCallException;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class BetaFlightProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private BetaFlightProvider provider() {
        return new BetaFlightProvider(ProviderHttpClients.build(wm.baseUrl() + "/beta", Duration.ofSeconds(2)));
    }

    private static ResponseDefinitionBuilder xml(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/xml").withBody(body);
    }

    @Test
    void readsNestedItinerariesAndUsesTotalAsThePrice() {
        wm.stubFor(get(urlPathEqualTo("/beta/flights")).willReturn(xml(ProviderPayloads.BETA_XML)));
        RecordingSink sink = new RecordingSink();

        provider().fetch(sink);

        assertThat(sink.offers).hasSize(2);
        RawOffer first = sink.offers.getFirst();
        assertThat(first.providerOfferId()).isEqualTo("BETA-000001");
        assertThat(first.price()).isEqualByComparingTo(new BigDecimal("85.50"));
        assertThat(first.currency()).isEqualTo("EUR");
        assertThat(first.segments().getFirst().carrier()).isEqualTo("IB");
        assertThat(first.segments().getFirst().flightNumber()).isEqualTo("845");
        assertThat(first.segments().getFirst().origin()).isEqualTo("MAD");
        assertThat(first.segments().getFirst().arrival()).isEqualTo(OffsetDateTime.parse("2026-11-02T08:35:00+01:00"));
        RawOffer second = sink.offers.get(1);
        assertThat(second.segments()).hasSize(2);
        assertThat(second.segments().get(1).destination()).isEqualTo("LHR");
        assertThat(sink.malformed).singleElement().asString().contains("BETA-000003");
    }

    @Test
    void wrongRootOrBrokenXmlIsAParseFailure() {
        wm.stubFor(get(urlPathEqualTo("/beta/flights")).willReturn(xml("<Other><Itineraries/></Other>")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink())).isInstanceOf(ProviderParseException.class);

        wm.stubFor(get(urlPathEqualTo("/beta/flights")).willReturn(xml("<BetaResponse><Itineraries><Itinerary ref=\"x\">")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink())).isInstanceOf(ProviderParseException.class);
    }

    @Test
    void serviceUnavailableIsAProviderCall() {
        wm.stubFor(get(urlPathEqualTo("/beta/flights")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink()))
                .isInstanceOf(ProviderCallException.class)
                .satisfies(e -> assertThat(((ProviderCallException) e).serverError()).isTrue());
    }
}
