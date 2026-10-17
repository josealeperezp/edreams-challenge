package com.edreams.flights.infrastructure.provider.alpha;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.model.RawSegment;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.http.ProviderParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anti-corruption layer for Mock-Alpha: a JSON array of offers with legs. Reads token by token so a
 * 170 MB inventory never sits in memory. Quirks handled here: prices as JSON doubles, zero-padded
 * flight numbers (left to the domain's FlightNumber.parse), ISO-8601 times with offset.
 */
public final class AlphaFlightProvider implements FlightProviderPort {

    private final RestClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public AlphaFlightProvider(RestClient client) {
        this.client = client;
    }

    @Override
    public ProviderId id() {
        return ProviderId.ALPHA;
    }

    @Override
    public void fetch(OfferSink sink) {
        ProviderHttpClients.stream(client, id(), MediaType.APPLICATION_JSON, body -> parse(body, sink));
    }

    void parse(InputStream body, OfferSink sink) {
        try (JsonParser parser = mapper.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new ProviderParseException("Alpha body is not a JSON array");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                OfferJson offer = readOffer(parser);
                emit(offer, sink);
            }
        } catch (JacksonException e) {
            throw new ProviderParseException("Alpha body is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Consumes exactly one offer object, whatever its content, so decoding errors cannot desync the stream. */
    private static OfferJson readOffer(JsonParser p) {
        OfferJson offer = new OfferJson();
        while (p.nextToken() == JsonToken.PROPERTY_NAME) {
            String name = p.currentName();
            JsonToken value = p.nextToken();
            switch (name) {
                case "offerId" -> offer.offerId = p.getValueAsString();
                case "price" -> offer.price = value == JsonToken.VALUE_NULL ? null : p.getDecimalValue();
                case "currency" -> offer.currency = p.getValueAsString();
                case "legs" -> {
                    if (value == JsonToken.START_ARRAY) {
                        while (p.nextToken() == JsonToken.START_OBJECT) {
                            offer.legs.add(readLeg(p));
                        }
                    } else {
                        p.skipChildren();
                    }
                }
                default -> p.skipChildren();
            }
        }
        return offer;
    }

    private static LegJson readLeg(JsonParser p) {
        LegJson leg = new LegJson();
        while (p.nextToken() == JsonToken.PROPERTY_NAME) {
            String name = p.currentName();
            p.nextToken();
            switch (name) {
                case "carrier" -> leg.carrier = p.getValueAsString();
                case "flightNumber" -> leg.flightNumber = p.getValueAsString();
                case "from" -> leg.from = p.getValueAsString();
                case "to" -> leg.to = p.getValueAsString();
                case "dep" -> leg.dep = p.getValueAsString();
                case "arr" -> leg.arr = p.getValueAsString();
                default -> p.skipChildren();
            }
        }
        return leg;
    }

    private static void emit(OfferJson offer, OfferSink sink) {
        try {
            List<RawSegment> segments = new ArrayList<>(offer.legs.size());
            for (LegJson leg : offer.legs) {
                segments.add(new RawSegment(leg.carrier, leg.flightNumber, leg.from, leg.to,
                        leg.dep == null ? null : OffsetDateTime.parse(leg.dep),
                        leg.arr == null ? null : OffsetDateTime.parse(leg.arr)));
            }
            sink.offer(new RawOffer(ProviderId.ALPHA, offer.offerId, segments, offer.price, offer.currency));
        } catch (RuntimeException e) {
            sink.malformed("offer " + offer.offerId + ": " + e.getMessage());
        }
    }

    private static final class OfferJson {
        String offerId;
        BigDecimal price;
        String currency;
        final List<LegJson> legs = new ArrayList<>(2);
    }

    private static final class LegJson {
        String carrier;
        String flightNumber;
        String from;
        String to;
        String dep;
        String arr;
    }
}
