package com.edreams.flights.domain.rule;

/** Why an offer was filtered out before aggregation. Counted per provider in every ingestion run. */
public enum RejectionReason {
    NO_SEGMENTS,
    DEAD_LEG,
    NEGATIVE_DURATION,
    BROKEN_CONNECTION,
    CONNECTION_TOO_SHORT,
    LAYOVER_TOO_LONG,
    MISSING_CARRIER,
    INVALID_PRICE,
    UNSUPPORTED_CURRENCY,
    /** Could not be decoded or normalised at all: bad code, bad number, missing time. */
    MALFORMED
}
