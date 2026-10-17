package com.edreams.flights.infrastructure.provider.beta;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
 * Anti-corruption layer for Mock-Beta: deeply nested XML read with a StAX pull parser, one
 * itinerary at a time. Quirks handled here: price split into Base, Taxes and Total (Total is the
 * comparable amount), carrier code nested under Carrier/Code, times with offset.
 */
public final class BetaFlightProvider implements FlightProviderPort {

    private final RestClient client;

    public BetaFlightProvider(RestClient client) {
        this.client = client;
    }

    @Override
    public ProviderId id() {
        return ProviderId.BETA;
    }

    @Override
    public void fetch(OfferSink sink) {
        ProviderHttpClients.stream(client, id(), MediaType.APPLICATION_XML, body -> parse(body, sink));
    }

    void parse(InputStream body, OfferSink sink) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(body);
            new Walker(reader, sink).walk();
        } catch (XMLStreamException e) {
            throw new ProviderParseException("Beta body is not valid XML: " + e.getMessage(), e);
        }
    }

    /** Tracks the element path so Airport and DateTime are attributed to Departure or Arrival. */
    private static final class Walker {
        private final XMLStreamReader reader;
        private final OfferSink sink;
        private final Deque<String> path = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();
        private ItineraryXml itinerary;
        private LegXml leg;
        private boolean rootSeen;

        Walker(XMLStreamReader reader, OfferSink sink) {
            this.reader = reader;
            this.sink = sink;
        }

        void walk() throws XMLStreamException {
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> start();
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
                    case XMLStreamConstants.END_ELEMENT -> end();
                    default -> { }
                }
            }
            if (!rootSeen) {
                throw new ProviderParseException("Beta body has no BetaResponse root element");
            }
        }

        private void start() {
            String name = reader.getLocalName();
            if (path.isEmpty()) {
                if (!"BetaResponse".equals(name)) {
                    throw new ProviderParseException("Beta root element is " + name + ", expected BetaResponse");
                }
                rootSeen = true;
            }
            path.push(name);
            text.setLength(0);
            switch (name) {
                case "Itinerary" -> {
                    itinerary = new ItineraryXml();
                    itinerary.ref = reader.getAttributeValue(null, "ref");
                }
                case "Fare" -> {
                    if (itinerary != null) {
                        itinerary.currency = reader.getAttributeValue(null, "currency");
                    }
                }
                case "Leg" -> leg = new LegXml();
                default -> { }
            }
        }

        private void end() {
            String name = path.pop();
            String parent = path.peek();
            String value = text.toString().trim();
            text.setLength(0);
            if (itinerary == null) {
                return;
            }
            switch (name) {
                case "Total" -> itinerary.total = value;
                case "Code" -> {
                    if ("Carrier".equals(parent) && leg != null) {
                        leg.carrier = value;
                    }
                }
                case "FlightNumber" -> {
                    if (leg != null) {
                        leg.flightNumber = value;
                    }
                }
                case "Airport" -> {
                    if (leg != null) {
                        if ("Departure".equals(parent)) {
                            leg.origin = value;
                        } else if ("Arrival".equals(parent)) {
                            leg.destination = value;
                        }
                    }
                }
                case "DateTime" -> {
                    if (leg != null) {
                        if ("Departure".equals(parent)) {
                            leg.departure = value;
                        } else if ("Arrival".equals(parent)) {
                            leg.arrival = value;
                        }
                    }
                }
                case "Leg" -> {
                    itinerary.legs.add(leg);
                    leg = null;
                }
                case "Itinerary" -> {
                    emit(itinerary);
                    itinerary = null;
                }
                default -> { }
            }
        }

        private void emit(ItineraryXml it) {
            try {
                List<RawSegment> segments = new ArrayList<>(it.legs.size());
                for (LegXml l : it.legs) {
                    segments.add(new RawSegment(l.carrier, l.flightNumber, l.origin, l.destination,
                            l.departure == null ? null : OffsetDateTime.parse(l.departure),
                            l.arrival == null ? null : OffsetDateTime.parse(l.arrival)));
                }
                BigDecimal total = it.total == null || it.total.isBlank() ? null : new BigDecimal(it.total);
                sink.offer(new RawOffer(ProviderId.BETA, it.ref, segments, total, it.currency));
            } catch (RuntimeException e) {
                sink.malformed("itinerary " + it.ref + ": " + e.getMessage());
            }
        }
    }

    private static final class ItineraryXml {
        String ref;
        String currency;
        String total;
        final List<LegXml> legs = new ArrayList<>(2);
    }

    private static final class LegXml {
        String carrier;
        String flightNumber;
        String origin;
        String destination;
        String departure;
        String arrival;
    }
}
