package com.edreams.flights.domain.rule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;

/** Shared raw offers mirroring the mock providers' sample data. */
final class Fixtures {

    static final ConnectionPolicy POLICY = new ConnectionPolicy(Duration.ofMinutes(30), Duration.ofHours(24));
    static final ItineraryValidator VALIDATOR = new ItineraryValidator(POLICY);

    static final OffsetDateTime DEP = OffsetDateTime.parse("2026-11-02T07:15:00+01:00");
    static final OffsetDateTime ARR = OffsetDateTime.parse("2026-11-02T08:35:00+01:00");

    private Fixtures() {
    }

    static RawSegment madBcn(String carrier, String flightNumber) {
        return new RawSegment(carrier, flightNumber, "MAD", "BCN", DEP, ARR);
    }

    static RawOffer direct(ProviderId provider, String price) {
        return new RawOffer(provider, provider.name() + "-1", List.of(madBcn("IB", "845")), new BigDecimal(price), "EUR");
    }

    static RawOffer withSegments(List<RawSegment> segments) {
        return new RawOffer(ProviderId.ALPHA, "ALPHA-x", segments, new BigDecimal("100.00"), "EUR");
    }

    static ProviderOffer valid(RawOffer raw) {
        return switch (VALIDATOR.validate(raw)) {
            case Validation.Valid v -> v.offer();
            case Validation.Rejected r -> throw new AssertionError("expected valid, got " + r);
        };
    }

    static RejectionReason reasonOf(RawOffer raw) {
        return switch (VALIDATOR.validate(raw)) {
            case Validation.Valid v -> throw new AssertionError("expected rejection, got " + v);
            case Validation.Rejected r -> r.reason();
        };
    }
}
