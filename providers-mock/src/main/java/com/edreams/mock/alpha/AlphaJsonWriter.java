package com.edreams.mock.alpha;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.edreams.mock.inventory.ProviderOffer;
import com.edreams.mock.inventory.Segment;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mock-Alpha format: a flat JSON array of offers written with the Jackson streaming API, one offer
 * at a time. Prices are plain doubles and flight numbers are zero-padded to four digits.
 */
@Component
public class AlphaJsonWriter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JsonMapper mapper = JsonMapper.builder().build();

    public void write(Stream<ProviderOffer> offers, OutputStream out) {
        try (JsonGenerator g = mapper.createGenerator(out)) {
            g.writeStartArray();
            int written = 0;
            for (Iterator<ProviderOffer> it = offers.iterator(); it.hasNext(); ) {
                offer(g, it.next());
                if (++written % 1000 == 0) {
                    g.flush();
                }
            }
            g.writeEndArray();
        }
    }

    private static void offer(JsonGenerator g, ProviderOffer offer) {
        g.writeStartObject();
        g.writeStringProperty("offerId", offer.offerId());
        g.writeNumberProperty("price", offer.price().doubleValue());
        g.writeStringProperty("currency", offer.currency());
        g.writeArrayPropertyStart("legs");
        for (Segment leg : offer.legs()) {
            g.writeStartObject();
            g.writeStringProperty("carrier", leg.carrier());
            g.writeStringProperty("flightNumber", "%04d".formatted(leg.flightNumber()));
            g.writeStringProperty("from", leg.origin());
            g.writeStringProperty("to", leg.destination());
            g.writeStringProperty("dep", ISO.format(leg.departure()));
            g.writeStringProperty("arr", ISO.format(leg.arrival()));
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }
}
