package com.edreams.flights.domain.rule;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.ProviderQuote;

/**
 * Given every provider's offer for one itinerary, picks the cheapest, applies the markup and keeps
 * all quotes. Ties go to the lower {@link ProviderId} so the result is deterministic.
 */
public final class BestOfferSelector {

    private final PricingPolicy pricing;

    public BestOfferSelector(PricingPolicy pricing) {
        this.pricing = Objects.requireNonNull(pricing, "pricing");
    }

    public Itinerary select(List<ProviderOffer> sameItinerary) {
        if (sameItinerary == null || sameItinerary.isEmpty()) {
            throw new IllegalArgumentException("Nothing to select from");
        }
        ItinerarySignature signature = sameItinerary.getFirst().signature();
        Map<ProviderId, Money> cheapestPerProvider = new EnumMap<>(ProviderId.class);
        for (ProviderOffer offer : sameItinerary) {
            if (!signature.equals(offer.signature())) {
                throw new IllegalArgumentException("Offers differ: " + signature + " vs " + offer.signature());
            }
            cheapestPerProvider.merge(offer.provider(), offer.price(), (a, b) -> a.isCheaperThan(b) ? a : b);
        }
        ProviderOffer best = sameItinerary.stream()
                .min(Comparator.comparing(ProviderOffer::price, Money::compareAmount)
                        .thenComparing(ProviderOffer::provider))
                .orElseThrow();
        List<ProviderQuote> quotes = cheapestPerProvider.entrySet().stream()
                .map(e -> new ProviderQuote(e.getKey(), e.getValue()))
                .toList();
        return new Itinerary(signature, best.segments(), best.price(), pricing.apply(best.price()), best.provider(), quotes);
    }
}
