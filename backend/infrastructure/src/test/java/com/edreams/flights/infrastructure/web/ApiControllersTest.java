package com.edreams.flights.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.edreams.flights.application.ingestion.IngestionAlreadyRunning;
import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.in.IngestionStatus;
import com.edreams.flights.application.port.in.RunIngestion;
import com.edreams.flights.application.port.in.SearchItineraries;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderQuote;
import com.edreams.flights.domain.model.Segment;
import com.edreams.flights.infrastructure.web.sse.SseIngestionEvents;

@WebMvcTest({SearchController.class, IngestionController.class})
class ApiControllersTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SearchItineraries search;

    @MockitoBean
    private RunIngestion runIngestion;

    @MockitoBean
    private IngestionStatus ingestionStatus;

    @MockitoBean
    private SseIngestionEvents events;

    private static ItineraryRow row() {
        Segment segment = new Segment(new Carrier("IB"), new FlightNumber(845), new AirportCode("MAD"), new AirportCode("BCN"),
                OffsetDateTime.parse("2026-11-02T07:15:00+01:00"), OffsetDateTime.parse("2026-11-02T08:35:00+01:00"));
        Itinerary itinerary = new Itinerary(ItinerarySignature.of(List.of(segment)), List.of(segment), Money.eur("85.50"),
                Money.eur("89.78"), ProviderId.BETA, List.of(new ProviderQuote(ProviderId.BETA, Money.eur("85.50")),
                        new ProviderQuote(ProviderId.ALPHA, Money.eur("89.90"))));
        return new ItineraryRow(7L, itinerary, IngestionRunId.newId(), Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    void searchReturnsAFlatPage() throws Exception {
        when(search.search(any())).thenReturn(new SearchPage(List.of(row()), "abc", true, new SearchPage.Inventory(3, 100, null)));

        mvc.perform(get("/api/itineraries").param("origin", "mad").param("destination", "BCN").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].origin").value("MAD"))
                .andExpect(jsonPath("$.items[0].carrier").value("IB"))
                .andExpect(jsonPath("$.items[0].price").value(89.78))
                .andExpect(jsonPath("$.items[0].bestProvider").value("BETA"))
                .andExpect(jsonPath("$.items[0].quotes.length()").value(2))
                .andExpect(jsonPath("$.items[0].segments[0].flightNumber").value(845))
                .andExpect(jsonPath("$.nextCursor").value("abc"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.inventory.version").value(3));
    }

    @Test
    void statsDescribeTheFilteredInventory() throws Exception {
        SearchStats stats = new SearchStats(3, new BigDecimal("85.50"), new BigDecimal("120.00"),
                List.of(new SearchStats.PriceBucket(new BigDecimal("85.50"), new BigDecimal("120.00"), 3)),
                List.of(new SearchStats.CarrierCount(new Carrier("IB"), 3, new BigDecimal("85.50"))),
                List.of(new SearchStats.ProviderCount(ProviderId.BETA, 2), new SearchStats.ProviderCount(ProviderId.GAMMA, 1)),
                List.of(new SearchStats.StopsCount(0, 3)));
        when(search.stats(any())).thenReturn(stats);

        mvc.perform(get("/api/itineraries/stats").param("origin", "mad").param("destination", "BCN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.minPrice").value(85.50))
                .andExpect(jsonPath("$.priceBuckets[0].count").value(3))
                .andExpect(jsonPath("$.byCarrier[0].carrier").value("IB"))
                .andExpect(jsonPath("$.byProvider[1].provider").value("GAMMA"))
                .andExpect(jsonPath("$.byStops[0].stops").value(0));

        mvc.perform(get("/api/itineraries/stats").param("carrier", "IBERIA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void invalidFiltersAreProblemDetails() throws Exception {
        mvc.perform(get("/api/itineraries").param("origin", "MADRID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid IATA airport code: MADRID"));
        mvc.perform(get("/api/itineraries").param("cursor", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid cursor"));
        mvc.perform(get("/api/itineraries").param("limit", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startingARunIsAcceptedOrConflicts() throws Exception {
        IngestionRunId id = IngestionRunId.newId();
        when(runIngestion.start()).thenReturn(id);
        mvc.perform(post("/api/ingestion/runs"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/ingestion/runs/" + id))
                .andExpect(jsonPath("$.runId").value(id.toString()));

        when(runIngestion.start()).thenThrow(new IngestionAlreadyRunning(id));
        mvc.perform(post("/api/ingestion/runs"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.runningRunId").value(id.toString()));
    }

    @Test
    void latestRunIsExposedOr404() throws Exception {
        when(ingestionStatus.latest()).thenReturn(Optional.empty());
        mvc.perform(get("/api/ingestion/runs/latest")).andExpect(status().isNotFound());

        IngestionRun run = IngestionRun.started(IngestionRunId.newId(), Instant.parse("2026-10-17T10:00:00Z"));
        when(ingestionStatus.latest()).thenReturn(Optional.of(run));
        mvc.perform(get("/api/ingestion/runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.id").value(run.id().toString()));
    }
}
