package com.edreams.flights.infrastructure.provider.resilience;

import java.util.List;
import java.util.Optional;

import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.domain.model.ProviderId;

/** The three decorated providers as one bean, so wiring never depends on collection injection rules. */
public record ResilientProviders(List<ResilientFlightProvider> all) {

    public ResilientProviders {
        all = List.copyOf(all);
    }

    public List<FlightProviderPort> asPorts() {
        return List.copyOf(all);
    }

    public Optional<ResilientFlightProvider> find(ProviderId id) {
        return all.stream().filter(p -> p.id() == id).findFirst();
    }
}
