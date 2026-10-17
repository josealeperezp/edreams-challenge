package com.edreams.flights.domain.rule;

import com.edreams.flights.domain.model.ProviderOffer;

/** Outcome of validating a raw offer. */
public sealed interface Validation {

    record Valid(ProviderOffer offer) implements Validation {
    }

    record Rejected(RejectionReason reason, String detail) implements Validation {
    }
}
