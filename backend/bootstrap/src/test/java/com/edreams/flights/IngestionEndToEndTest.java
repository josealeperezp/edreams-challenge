package com.edreams.flights;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.port.in.IngestionStatus;
import com.edreams.flights.domain.model.ProviderId;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * The whole pipeline against stubbed providers: one itinerary offered by all three at different
 * prices and formats comes out once, at the cheapest base price plus the markup, with three quotes.
 */
@AutoConfigureMockMvc
class IngestionEndToEndTest extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String ALPHA = """
            [{"offerId":"ALPHA-1","price":89.9,"currency":"EUR","legs":[
              {"carrier":"IB","flightNumber":"0845","from":"MAD","to":"BCN","dep":"2026-11-02T07:15:00+01:00","arr":"2026-11-02T08:35:00+01:00"}]},
             {"offerId":"ALPHA-2","price":120.0,"currency":"EUR","legs":[
              {"carrier":"VY","flightNumber":"1002","from":"BCN","to":"MAD","dep":"2026-11-03T18:00:00+01:00","arr":"2026-11-03T19:25:00+01:00"}]}]
            """;
    private static final String BETA = """
            <BetaResponse><Header><Provider>Mock-Beta</Provider></Header><Itineraries>
              <Itinerary ref="BETA-1"><Pricing><Fare currency="EUR"><Base>75.24</Base><Taxes>10.26</Taxes><Total>85.50</Total></Fare></Pricing>
                <Legs><Leg><Carrier><Code>IB</Code><Name>Iberia</Name></Carrier><FlightNumber>845</FlightNumber>
                  <Departure><Airport>MAD</Airport><DateTime>2026-11-02T07:15:00+01:00</DateTime></Departure>
                  <Arrival><Airport>BCN</Airport><DateTime>2026-11-02T08:35:00+01:00</DateTime></Arrival></Leg></Legs></Itinerary>
              <Itinerary ref="BETA-2"><Pricing><Fare currency="EUR"><Base>1</Base><Taxes>1</Taxes><Total>40.00</Total></Fare></Pricing>
                <Legs><Leg><Carrier><Code>IB</Code></Carrier><FlightNumber>999</FlightNumber>
                  <Departure><Airport>MAD</Airport><DateTime>2026-11-02T10:00:00+01:00</DateTime></Departure>
                  <Arrival><Airport>MAD</Airport><DateTime>2026-11-02T11:00:00+01:00</DateTime></Arrival></Leg></Legs></Itinerary>
            </Itineraries></BetaResponse>
            """;
    private static final String GAMMA = """
            offer_id,flight,origin,destination,departure_utc,arrival_utc,price,currency,segment_index,segment_count
            GAMMA-1,IB845,MAD,BCN,2026-11-02 06:15,2026-11-02 07:35,92.00,EUR,1,1
            """;

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("providers.alpha.base-url", () -> wm.baseUrl() + "/alpha");
        registry.add("providers.beta.base-url", () -> wm.baseUrl() + "/beta");
        registry.add("providers.gamma.base-url", () -> wm.baseUrl() + "/gamma");
        registry.add("flights.resilience.retry-initial-wait", () -> "10ms");
        // batch size 1 forces the resolve phase to upsert while the provider_offer cursor is open,
        // the exact path that failed at real volume with a read-only transaction on one connection
        registry.add("flights.ingestion.batch-size", () -> "1");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IngestionStatus status;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void stubProviders() {
        wm.resetAll();
        jdbc.sql("TRUNCATE provider_offer, itinerary, ingestion_run").update();
        wm.stubFor(WireMock.get(urlPathEqualTo("/alpha/flights")).willReturn(okJson(ALPHA)));
        wm.stubFor(WireMock.get(urlPathEqualTo("/beta/flights")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/xml").withBody(BETA)));
        wm.stubFor(WireMock.get(urlPathEqualTo("/gamma/flights")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/csv").withBody(GAMMA)));
    }

    @Test
    void ingestsDeduplicatesPricesAndServesTheResult() throws Exception {
        mvc.perform(post("/api/ingestion/runs")).andExpect(status().isAccepted());
        IngestionRun run = awaitFinished();

        assertThat(run.status()).isEqualTo(IngestionRun.Status.COMPLETED);
        assertThat(run.itineraries()).isEqualTo(2);
        assertThat(run.providers().get(ProviderId.ALPHA).accepted()).isEqualTo(2);
        assertThat(run.providers().get(ProviderId.BETA).accepted()).isEqualTo(1);
        assertThat(run.providers().get(ProviderId.BETA).rejectedTotal()).isEqualTo(1);
        assertThat(run.providers().get(ProviderId.GAMMA).accepted()).isEqualTo(1);

        mvc.perform(get("/api/itineraries").param("origin", "MAD").param("destination", "BCN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].signature").value("IB845|MAD|BCN|2026-11-02T06:15:00Z"))
                .andExpect(jsonPath("$.items[0].bestProvider").value("BETA"))
                .andExpect(jsonPath("$.items[0].basePrice").value(85.50))
                .andExpect(jsonPath("$.items[0].price").value(89.78))
                .andExpect(jsonPath("$.items[0].quotes.length()").value(3))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.inventory.approximateItineraries").value(2))
                .andExpect(jsonPath("$.inventory.latestRun.status").value("COMPLETED"));

        mvc.perform(get("/api/providers/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].circuit").value("CLOSED"))
                .andExpect(jsonPath("$[1].lastResult.accepted").value(1));
    }

    @Test
    void aProviderOutageYieldsAPartialRunAndKeepsTheOthers() throws Exception {
        wm.stubFor(WireMock.get(urlPathEqualTo("/beta/flights")).willReturn(aResponse().withStatus(500)));

        mvc.perform(post("/api/ingestion/runs")).andExpect(status().isAccepted());
        IngestionRun run = awaitFinished();

        assertThat(run.status()).isEqualTo(IngestionRun.Status.PARTIAL);
        assertThat(run.providers().get(ProviderId.BETA).ok()).isFalse();
        assertThat(run.providers().get(ProviderId.BETA).error()).contains("HTTP 500");
        assertThat(run.itineraries()).isEqualTo(2);

        mvc.perform(get("/api/itineraries").param("origin", "MAD").param("destination", "BCN"))
                .andExpect(jsonPath("$.items[0].bestProvider").value("ALPHA"))
                .andExpect(jsonPath("$.items[0].price").value(94.40))
                .andExpect(jsonPath("$.items[0].quotes.length()").value(2));
    }

    private IngestionRun awaitFinished() {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> status.latest().map(r -> !r.running()).orElse(false));
        return status.latest().orElseThrow();
    }
}
