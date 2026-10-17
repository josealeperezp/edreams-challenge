package com.edreams.flights.domain.model;

/** What one provider asked for an itinerary, kept for provenance next to the winning price. */
public record ProviderQuote(ProviderId provider, Money price) {
}
