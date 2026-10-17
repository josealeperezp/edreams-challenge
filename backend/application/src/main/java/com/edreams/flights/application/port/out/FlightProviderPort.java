package com.edreams.flights.application.port.out;

import com.edreams.flights.domain.model.ProviderId;

/**
 * One external GDS provider. {@link #fetch} streams the provider's whole inventory into the sink
 * and throws when the provider is unavailable after the adapter's own retries.
 */
public interface FlightProviderPort {

    ProviderId id();

    void fetch(OfferSink sink);
}
