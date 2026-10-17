package com.edreams.flights.infrastructure.provider.gamma;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;

/**
 * Anti-corruption layer for Mock-Gamma: CSV with one row per segment, read line by line. Quirks
 * handled here: carrier and number joined in one column, UTC times without offset, the offer
 * price repeated on every row, rows of one offer contiguous and ordered by segment_index.
 */
public final class GammaFlightProvider implements FlightProviderPort {

    static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");
    private static final DateTimeFormatter UTC_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Carrier designators are two characters with at least one letter, so a digits-only value is a missing carrier. */
    private static final Pattern FLIGHT = Pattern.compile("^([A-Z][A-Z0-9]|[0-9][A-Z])([0-9]{1,4})$");
    private static final Pattern NUMBER_ONLY = Pattern.compile("^[0-9]{1,4}$");
    private static final List<String> REQUIRED = List.of("offer_id", "flight", "origin", "destination",
            "departure_utc", "arrival_utc", "price", "currency", "segment_index", "segment_count");

    private final RestClient client;

    public GammaFlightProvider(RestClient client) {
        this.client = client;
    }

    @Override
    public ProviderId id() {
        return ProviderId.GAMMA;
    }

    @Override
    public void fetch(OfferSink sink) {
        ProviderHttpClients.stream(client, id(), TEXT_CSV, body -> parse(body, sink));
    }

    void parse(InputStream body, OfferSink sink) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8), 1 << 16);
        Map<String, Integer> columns = header(reader.readLine());
        OfferRows current = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            String offerId = field(fields, columns, "offer_id");
            if (current != null && !current.offerId.equals(offerId)) {
                current.emit(sink);
                current = null;
            }
            if (current == null) {
                current = new OfferRows(offerId);
            }
            current.add(fields, columns, sink);
        }
        if (current != null) {
            current.emit(sink);
        }
    }

    private static Map<String, Integer> header(String headerLine) {
        if (headerLine == null) {
            throw new ProviderParseException("Gamma body is empty");
        }
        String[] names = headerLine.strip().split(",");
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            columns.put(names[i].trim(), i);
        }
        for (String required : REQUIRED) {
            if (!columns.containsKey(required)) {
                throw new ProviderParseException("Gamma header lacks column " + required + ": " + headerLine);
            }
        }
        return columns;
    }

    private static String field(String[] fields, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        if (index >= fields.length) {
            throw new IllegalArgumentException("missing column " + name);
        }
        return fields[index].trim();
    }

    private static OffsetDateTime utc(String value) {
        return LocalDateTime.parse(value, UTC_MINUTES).atOffset(ZoneOffset.UTC);
    }

    /** Accumulates the rows of one offer; a bad row poisons the whole offer, which is then dropped. */
    private static final class OfferRows {
        private final String offerId;
        private final List<IndexedSegment> segments = new ArrayList<>(2);
        private BigDecimal price;
        private String currency;
        private boolean broken;

        OfferRows(String offerId) {
            this.offerId = offerId;
        }

        void add(String[] fields, Map<String, Integer> columns, OfferSink sink) {
            try {
                String flight = field(fields, columns, "flight");
                String carrier;
                String number;
                Matcher m = FLIGHT.matcher(flight);
                if (NUMBER_ONLY.matcher(flight).matches()) {
                    carrier = "";
                    number = flight;
                } else if (m.matches()) {
                    carrier = m.group(1);
                    number = m.group(2);
                } else {
                    throw new IllegalArgumentException("unreadable flight '" + flight + "'");
                }
                int index = Integer.parseInt(field(fields, columns, "segment_index"));
                segments.add(new IndexedSegment(index, new RawSegment(carrier, number,
                        field(fields, columns, "origin"), field(fields, columns, "destination"),
                        utc(field(fields, columns, "departure_utc")), utc(field(fields, columns, "arrival_utc")))));
                price = new BigDecimal(field(fields, columns, "price"));
                currency = field(fields, columns, "currency");
            } catch (RuntimeException e) {
                broken = true;
                sink.malformed("offer " + offerId + ": " + e.getMessage());
            }
        }

        void emit(OfferSink sink) {
            if (broken) {
                return;
            }
            List<RawSegment> ordered = segments.stream()
                    .sorted(Comparator.comparingInt(IndexedSegment::index))
                    .map(IndexedSegment::segment)
                    .toList();
            sink.offer(new RawOffer(ProviderId.GAMMA, offerId, ordered, price, currency));
        }

        private record IndexedSegment(int index, RawSegment segment) {
        }
    }
}
