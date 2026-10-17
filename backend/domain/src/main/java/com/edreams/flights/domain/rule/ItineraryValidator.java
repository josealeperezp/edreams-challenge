package com.edreams.flights.domain.rule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

import com.edreams.flights.domain.model.AirportCode;
import com.edreams.flights.domain.model.Carrier;
import com.edreams.flights.domain.model.FlightNumber;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;
import com.edreams.flights.domain.model.Segment;
import com.edreams.flights.domain.rule.Validation.Rejected;
import com.edreams.flights.domain.rule.Validation.Valid;

/**
 * Normalises a raw provider offer into a typed one and filters out dead legs and invalid connections.
 * The first failing rule wins, so each rejection has exactly one reason.
 */
public final class ItineraryValidator {

    private final ConnectionPolicy policy;
    private final Currency supportedCurrency;

    public ItineraryValidator(ConnectionPolicy policy) {
        this(policy, Money.EUR);
    }

    public ItineraryValidator(ConnectionPolicy policy, Currency supportedCurrency) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.supportedCurrency = Objects.requireNonNull(supportedCurrency, "supportedCurrency");
    }

    public Validation validate(RawOffer raw) {
        if (raw == null || raw.segments() == null || raw.segments().isEmpty()) {
            return new Rejected(RejectionReason.NO_SEGMENTS, "offer has no segments");
        }
        List<Segment> segments = new ArrayList<>(raw.segments().size());
        for (RawSegment rawSegment : raw.segments()) {
            Validation failure = normalise(rawSegment, segments);
            if (failure != null) {
                return failure;
            }
        }
        for (int i = 1; i < segments.size(); i++) {
            Validation failure = checkConnection(segments.get(i - 1), segments.get(i));
            if (failure != null) {
                return failure;
            }
        }
        if (raw.price() == null || raw.price().signum() <= 0) {
            return new Rejected(RejectionReason.INVALID_PRICE, "price " + raw.price());
        }
        Currency currency;
        try {
            currency = Currency.getInstance(raw.currency() == null ? "" : raw.currency().trim());
        } catch (IllegalArgumentException e) {
            return new Rejected(RejectionReason.UNSUPPORTED_CURRENCY, "currency " + raw.currency());
        }
        if (!supportedCurrency.equals(currency)) {
            return new Rejected(RejectionReason.UNSUPPORTED_CURRENCY, "currency " + currency);
        }
        return new Valid(new ProviderOffer(raw.provider(), raw.providerOfferId(), segments, new Money(raw.price(), currency)));
    }

    /** Appends the typed segment to {@code into}, or returns the rejection. */
    private Validation normalise(RawSegment raw, List<Segment> into) {
        if (raw == null) {
            return new Rejected(RejectionReason.MALFORMED, "null segment");
        }
        if (raw.carrier() == null || raw.carrier().isBlank()) {
            return new Rejected(RejectionReason.MISSING_CARRIER, "segment without carrier");
        }
        if (raw.departure() == null || raw.arrival() == null) {
            return new Rejected(RejectionReason.MALFORMED, "segment without departure or arrival time");
        }
        Carrier carrier;
        FlightNumber flightNumber;
        AirportCode origin;
        AirportCode destination;
        try {
            carrier = Carrier.of(raw.carrier());
            flightNumber = FlightNumber.parse(raw.flightNumber());
            origin = AirportCode.of(raw.origin());
            destination = AirportCode.of(raw.destination());
        } catch (IllegalArgumentException e) {
            return new Rejected(RejectionReason.MALFORMED, e.getMessage());
        }
        if (origin.equals(destination)) {
            return new Rejected(RejectionReason.DEAD_LEG, carrier.code() + flightNumber + " " + origin + "-" + destination);
        }
        if (!raw.arrival().isAfter(raw.departure())) {
            return new Rejected(RejectionReason.NEGATIVE_DURATION, carrier.code() + flightNumber + " arrives " + raw.arrival() + " before " + raw.departure());
        }
        into.add(new Segment(carrier, flightNumber, origin, destination, raw.departure(), raw.arrival()));
        return null;
    }

    private Validation checkConnection(Segment previous, Segment next) {
        if (!previous.destination().equals(next.origin())) {
            return new Rejected(RejectionReason.BROKEN_CONNECTION,
                    previous.flightCode() + " arrives " + previous.destination() + " but " + next.flightCode() + " departs " + next.origin());
        }
        Duration layover = Duration.between(previous.arrival(), next.departure());
        if (layover.compareTo(policy.minConnection()) < 0) {
            return new Rejected(RejectionReason.CONNECTION_TOO_SHORT, layover.toMinutes() + " min at " + next.origin());
        }
        if (layover.compareTo(policy.maxLayover()) > 0) {
            return new Rejected(RejectionReason.LAYOVER_TOO_LONG, layover.toHours() + " h at " + next.origin());
        }
        return null;
    }
}
