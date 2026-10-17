package com.edreams.flights.infrastructure.json;

import java.math.BigDecimal;

import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderQuote;

public record QuoteJson(String provider, BigDecimal price) {

    public static QuoteJson from(ProviderQuote q) {
        return new QuoteJson(q.provider().name(), q.price().amount());
    }

    public ProviderQuote toQuote() {
        return new ProviderQuote(ProviderId.valueOf(provider), Money.eur(price));
    }
}
