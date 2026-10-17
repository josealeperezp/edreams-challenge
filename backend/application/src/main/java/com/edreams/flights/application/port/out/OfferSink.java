package com.edreams.flights.application.port.out;

import com.edreams.flights.domain.model.RawOffer;

/** Receives offers as a provider adapter decodes them, so a whole response never sits in memory. */
public interface OfferSink {

    void offer(RawOffer offer);

    /** A record the adapter could not decode at all; counted, never fatal. */
    void malformed(String detail);
}
