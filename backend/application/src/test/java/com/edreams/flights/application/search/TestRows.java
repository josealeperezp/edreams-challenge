package com.edreams.flights.application.search;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderQuote;
import com.edreams.flights.domain.model.Segment;

final class TestRows {

    private TestRows() {
    }

    static ItineraryRow row(long id, String price) {
        Segment segment = new Segment(new Carrier("IB"), new FlightNumber(845), new AirportCode("MAD"), new AirportCode("BCN"),
                OffsetDateTime.parse("2026-11-02T07:15:00+01:00"), OffsetDateTime.parse("2026-11-02T08:35:00+01:00"));
        Money base = Money.eur(price);
        Itinerary itinerary = new Itinerary(ItinerarySignature.of(List.of(segment)), List.of(segment), base,
                base.plusPercent(java.math.BigDecimal.valueOf(5)), ProviderId.BETA, List.of(new ProviderQuote(ProviderId.BETA, base)));
        return new ItineraryRow(id, itinerary, IngestionRunId.newId(), Instant.parse("2026-10-17T10:00:00Z"));
    }
}
