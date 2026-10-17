package com.edreams.flights.application.ingestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;

final class TestOffers {

    static final OffsetDateTime DEP = OffsetDateTime.parse("2026-11-02T07:15:00+01:00");
    static final OffsetDateTime ARR = OffsetDateTime.parse("2026-11-02T08:35:00+01:00");

    private TestOffers() {
    }

    /** IB845 MAD-BCN, the itinerary every provider shares in the mock data. */
    static RawOffer madBcn(ProviderId provider, String price) {
        return new RawOffer(provider, provider.name() + "-madbcn",
                List.of(new RawSegment("IB", "845", "MAD", "BCN", DEP, ARR)), new BigDecimal(price), "EUR");
    }

    static RawOffer bcnLhr(ProviderId provider, String price) {
        return new RawOffer(provider, provider.name() + "-bcnlhr",
                List.of(new RawSegment("VY", "1002", "BCN", "LHR", DEP.plusHours(5), ARR.plusHours(6))), new BigDecimal(price), "EUR");
    }

    static RawOffer deadLeg(ProviderId provider) {
        return new RawOffer(provider, provider.name() + "-dead",
                List.of(new RawSegment("IB", "845", "MAD", "MAD", DEP, ARR)), new BigDecimal("50"), "EUR");
    }
}
