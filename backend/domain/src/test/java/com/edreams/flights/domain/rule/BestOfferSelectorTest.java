package com.edreams.flights.domain.rule;

import static com.edreams.flights.domain.rule.Fixtures.direct;
import static com.edreams.flights.domain.rule.Fixtures.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderQuote;

class BestOfferSelectorTest {

    private final BestOfferSelector selector = new BestOfferSelector(PricingPolicy.ofPercent("5"));

    @Test
    void picksTheCheapestProviderAndAppliesTheMarkup() {
        Itinerary itinerary = selector.select(List.of(
                valid(direct(ProviderId.ALPHA, "89.90")),
                valid(direct(ProviderId.BETA, "85.50")),
                valid(direct(ProviderId.GAMMA, "92.00"))));

        assertThat(itinerary.bestProvider()).isEqualTo(ProviderId.BETA);
        assertThat(itinerary.basePrice()).isEqualTo(Money.eur("85.50"));
        assertThat(itinerary.price()).isEqualTo(Money.eur("89.78"));
        assertThat(itinerary.quotes()).containsExactly(
                new ProviderQuote(ProviderId.ALPHA, Money.eur("89.90")),
                new ProviderQuote(ProviderId.BETA, Money.eur("85.50")),
                new ProviderQuote(ProviderId.GAMMA, Money.eur("92.00")));
        assertThat(itinerary.carrier().code()).isEqualTo("IB");
        assertThat(itinerary.stops()).isZero();
        assertThat(itinerary.origin().value()).isEqualTo("MAD");
        assertThat(itinerary.destination().value()).isEqualTo("BCN");
    }

    @Test
    void breaksTiesByProviderOrder() {
        Itinerary itinerary = selector.select(List.of(
                valid(direct(ProviderId.GAMMA, "80.00")),
                valid(direct(ProviderId.BETA, "80.00"))));
        assertThat(itinerary.bestProvider()).isEqualTo(ProviderId.BETA);
    }

    @Test
    void singleOfferIsItsOwnBest() {
        Itinerary itinerary = selector.select(List.of(valid(direct(ProviderId.GAMMA, "64.99"))));
        assertThat(itinerary.bestProvider()).isEqualTo(ProviderId.GAMMA);
        assertThat(itinerary.price()).isEqualTo(Money.eur("68.24"));
        assertThat(itinerary.quotes()).hasSize(1);
    }

    @Test
    void refusesMixedItineraries() {
        var other = valid(new com.edreams.flights.domain.model.RawOffer(ProviderId.ALPHA, "x",
                List.of(Fixtures.madBcn("VY", "212")), new java.math.BigDecimal("50"), "EUR"));
        assertThatThrownBy(() -> selector.select(List.of(valid(direct(ProviderId.ALPHA, "10")), other)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offers differ");
        assertThatThrownBy(() -> selector.select(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
