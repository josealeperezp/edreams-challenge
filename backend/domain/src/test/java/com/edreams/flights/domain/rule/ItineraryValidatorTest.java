package com.edreams.flights.domain.rule;

import static com.edreams.flights.domain.rule.Fixtures.ARR;
import static com.edreams.flights.domain.rule.Fixtures.DEP;
import static com.edreams.flights.domain.rule.Fixtures.madBcn;
import static com.edreams.flights.domain.rule.Fixtures.reasonOf;
import static com.edreams.flights.domain.rule.Fixtures.valid;
import static com.edreams.flights.domain.rule.Fixtures.withSegments;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;

class ItineraryValidatorTest {

    private static final RawSegment BCN_LHR = new RawSegment("BA", "2845", "BCN", "LHR",
            ARR.plusMinutes(90), ARR.plusMinutes(90).plusHours(2));

    @Test
    void normalisesAValidDirectOffer() {
        ProviderOffer offer = valid(new RawOffer(ProviderId.GAMMA, "GAMMA-1",
                List.of(new RawSegment(" ib ", "0845", "mad", "bcn ", DEP, ARR)), new BigDecimal("89.9"), "EUR"));

        assertThat(offer.segments()).hasSize(1);
        assertThat(offer.segments().getFirst().carrier()).isEqualTo(new Carrier("IB"));
        assertThat(offer.segments().getFirst().flightNumber()).isEqualTo(new FlightNumber(845));
        assertThat(offer.segments().getFirst().origin().value()).isEqualTo("MAD");
        assertThat(offer.price().amount()).isEqualByComparingTo("89.90");
        assertThat(offer.signature().value()).isEqualTo("IB845|MAD|BCN|2026-11-02T06:15:00Z");
    }

    @Test
    void acceptsAValidConnection() {
        ProviderOffer offer = valid(withSegments(List.of(madBcn("IB", "845"), BCN_LHR)));
        assertThat(offer.segments()).hasSize(2);
        assertThat(offer.signature().value()).startsWith("IB845|MAD|BCN|").contains(";BA2845|BCN|LHR|");
    }

    @Test
    void rejectsEachInvalidShape() {
        assertThat(reasonOf(withSegments(List.of()))).isEqualTo(RejectionReason.NO_SEGMENTS);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("IB", "845", "MAD", "MAD", DEP, ARR)))))
                .isEqualTo(RejectionReason.DEAD_LEG);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("IB", "845", "MAD", "BCN", DEP, DEP.minusMinutes(45))))))
                .isEqualTo(RejectionReason.NEGATIVE_DURATION);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("", "845", "MAD", "BCN", DEP, ARR)))))
                .isEqualTo(RejectionReason.MISSING_CARRIER);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("IB", "no", "MAD", "BCN", DEP, ARR)))))
                .isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("IB", "845", "MADRID", "BCN", DEP, ARR)))))
                .isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(withSegments(List.of(new RawSegment("IB", "845", "MAD", "BCN", null, ARR)))))
                .isEqualTo(RejectionReason.MALFORMED);
    }

    @Test
    void rejectsInvalidConnections() {
        RawSegment fromElsewhere = new RawSegment("BA", "2845", "ZRH", "LHR", ARR.plusMinutes(90), ARR.plusHours(4));
        assertThat(reasonOf(withSegments(List.of(madBcn("IB", "845"), fromElsewhere))))
                .isEqualTo(RejectionReason.BROKEN_CONNECTION);

        RawSegment tooSoon = new RawSegment("BA", "2845", "BCN", "LHR", ARR.plusMinutes(10), ARR.plusHours(3));
        assertThat(reasonOf(withSegments(List.of(madBcn("IB", "845"), tooSoon))))
                .isEqualTo(RejectionReason.CONNECTION_TOO_SHORT);

        RawSegment nextWeek = new RawSegment("BA", "2845", "BCN", "LHR", ARR.plusDays(2), ARR.plusDays(2).plusHours(2));
        assertThat(reasonOf(withSegments(List.of(madBcn("IB", "845"), nextWeek))))
                .isEqualTo(RejectionReason.LAYOVER_TOO_LONG);
    }

    @Test
    void rejectsBadPricesAndCurrencies() {
        assertThat(reasonOf(new RawOffer(ProviderId.ALPHA, "a", List.of(madBcn("IB", "845")), BigDecimal.ZERO, "EUR")))
                .isEqualTo(RejectionReason.INVALID_PRICE);
        assertThat(reasonOf(new RawOffer(ProviderId.ALPHA, "a", List.of(madBcn("IB", "845")), null, "EUR")))
                .isEqualTo(RejectionReason.INVALID_PRICE);
        assertThat(reasonOf(new RawOffer(ProviderId.ALPHA, "a", List.of(madBcn("IB", "845")), BigDecimal.TEN, "USD")))
                .isEqualTo(RejectionReason.UNSUPPORTED_CURRENCY);
        assertThat(reasonOf(new RawOffer(ProviderId.ALPHA, "a", List.of(madBcn("IB", "845")), BigDecimal.TEN, "xx")))
                .isEqualTo(RejectionReason.UNSUPPORTED_CURRENCY);
    }
}
