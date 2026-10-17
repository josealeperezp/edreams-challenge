package com.edreams.flights.domain.rule;

import static com.edreams.flights.domain.rule.Fixtures.valid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;

/** The same flight as each mock provider represents it must collapse to one signature. */
class ItinerarySignatureTest {

    @Test
    void providerQuirksDoNotChangeTheSignature() {
        RawOffer alpha = new RawOffer(ProviderId.ALPHA, "ALPHA-000123",
                List.of(new RawSegment("IB", "0845", "MAD", "BCN",
                        OffsetDateTime.parse("2026-11-02T07:15:00+01:00"), OffsetDateTime.parse("2026-11-02T08:35:00+01:00"))),
                BigDecimal.valueOf(89.9), "EUR");
        RawOffer beta = new RawOffer(ProviderId.BETA, "BETA-000123",
                List.of(new RawSegment("IB", "845", "MAD", "BCN",
                        OffsetDateTime.parse("2026-11-02T07:15:00+01:00"), OffsetDateTime.parse("2026-11-02T08:35:00+01:00"))),
                new BigDecimal("85.50"), "EUR");
        RawOffer gamma = new RawOffer(ProviderId.GAMMA, "GAMMA-000123",
                List.of(new RawSegment("IB", "845", "MAD", "BCN",
                        OffsetDateTime.parse("2026-11-02T06:15:00Z"), OffsetDateTime.parse("2026-11-02T07:35:00Z"))),
                new BigDecimal("92.00"), "EUR");

        ItinerarySignature expected = new ItinerarySignature("IB845|MAD|BCN|2026-11-02T06:15:00Z");
        assertThat(valid(alpha).signature()).isEqualTo(expected);
        assertThat(valid(beta).signature()).isEqualTo(expected);
        assertThat(valid(gamma).signature()).isEqualTo(expected);
    }

    @Test
    void differentDepartureOrFlightIsADifferentItinerary() {
        RawOffer base = Fixtures.direct(ProviderId.ALPHA, "100");
        RawOffer laterFlight = new RawOffer(ProviderId.ALPHA, "x",
                List.of(new RawSegment("IB", "845", "MAD", "BCN", Fixtures.DEP.plusHours(3), Fixtures.ARR.plusHours(3))),
                BigDecimal.TEN, "EUR");
        RawOffer otherNumber = new RawOffer(ProviderId.ALPHA, "x",
                List.of(new RawSegment("IB", "846", "MAD", "BCN", Fixtures.DEP, Fixtures.ARR)), BigDecimal.TEN, "EUR");

        assertThat(valid(base).signature()).isNotEqualTo(valid(laterFlight).signature());
        assertThat(valid(base).signature()).isNotEqualTo(valid(otherNumber).signature());
    }
}
