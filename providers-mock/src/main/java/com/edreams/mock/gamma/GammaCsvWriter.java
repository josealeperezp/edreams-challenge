package com.edreams.mock.gamma;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.edreams.mock.inventory.ProviderOffer;
import com.edreams.mock.inventory.Segment;

/**
 * Mock-Gamma format: CSV, one row per segment. Rows of one offer are contiguous and ordered by
 * {@code segment_index}; the offer price is repeated on each. Flight is carrier and number joined,
 * times are UTC without offset.
 */
@Component
public class GammaCsvWriter {

    public static final String HEADER =
            "offer_id,flight,origin,destination,departure_utc,arrival_utc,price,currency,segment_index,segment_count";

    private static final DateTimeFormatter UTC_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void write(Stream<ProviderOffer> offers, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        w.write(HEADER);
        w.write('\n');
        for (Iterator<ProviderOffer> it = offers.iterator(); it.hasNext(); ) {
            ProviderOffer offer = it.next();
            int count = offer.legs().size();
            int index = 0;
            for (Segment leg : offer.legs()) {
                index++;
                w.write(offer.offerId());
                w.write(',');
                w.write(leg.carrier());
                w.write(Integer.toString(leg.flightNumber()));
                w.write(',');
                w.write(leg.origin());
                w.write(',');
                w.write(leg.destination());
                w.write(',');
                w.write(UTC_MINUTES.format(leg.departure().withOffsetSameInstant(ZoneOffset.UTC)));
                w.write(',');
                w.write(UTC_MINUTES.format(leg.arrival().withOffsetSameInstant(ZoneOffset.UTC)));
                w.write(',');
                w.write(offer.price().toPlainString());
                w.write(',');
                w.write(offer.currency());
                w.write(',');
                w.write(Integer.toString(index));
                w.write(',');
                w.write(Integer.toString(count));
                w.write('\n');
            }
        }
        w.flush();
    }
}
