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
import com.edreams.flights.infrastructure.provider.gamma.GammaFlightProvider;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class GammaFlightProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private GammaFlightProvider provider() {
        return new GammaFlightProvider(ProviderHttpClients.build(wm.baseUrl() + "/gamma", Duration.ofSeconds(2)));
    }

    private static ResponseDefinitionBuilder csv(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/csv").withBody(body);
    }

    @Test
    void groupsRowsIntoOffersOrderedBySegmentIndex() {
        wm.stubFor(get(urlPathEqualTo("/gamma/flights")).willReturn(csv(ProviderPayloads.GAMMA_CSV)));
        RecordingSink sink = new RecordingSink();

        provider().fetch(sink);

        assertThat(sink.offers).extracting(RawOffer::providerOfferId).containsExactly("GAMMA-000001", "GAMMA-000002", "GAMMA-000003");
        RawOffer direct = sink.offers.getFirst();
        assertThat(direct.segments().getFirst().carrier()).isEqualTo("IB");
        assertThat(direct.segments().getFirst().flightNumber()).isEqualTo("845");
        assertThat(direct.segments().getFirst().departure()).isEqualTo(OffsetDateTime.parse("2026-11-02T06:15:00Z"));
        assertThat(direct.price()).isEqualByComparingTo(new BigDecimal("92.00"));

        RawOffer connection = sink.offers.get(1);
        assertThat(connection.segments()).hasSize(2);
        assertThat(connection.segments().getFirst().origin()).isEqualTo("MAD");
        assertThat(connection.segments().get(1).origin()).isEqualTo("LHR");

        RawOffer missingCarrier = sink.offers.get(2);
        assertThat(missingCarrier.segments().getFirst().carrier()).isEmpty();
        assertThat(missingCarrier.segments().getFirst().flightNumber()).isEqualTo("845");

        assertThat(sink.malformed).singleElement().asString().contains("GAMMA-000004");
    }

    @Test
    void missingHeaderColumnIsAParseFailure() {
        wm.stubFor(get(urlPathEqualTo("/gamma/flights")).willReturn(csv("offer_id,flight,origin\nx,IB1,MAD\n")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink()))
                .isInstanceOf(ProviderParseException.class)
                .hasMessageContaining("destination");

        wm.stubFor(get(urlPathEqualTo("/gamma/flights")).willReturn(csv("")));
        assertThatThrownBy(() -> provider().fetch(new RecordingSink())).isInstanceOf(ProviderParseException.class);
    }
}
