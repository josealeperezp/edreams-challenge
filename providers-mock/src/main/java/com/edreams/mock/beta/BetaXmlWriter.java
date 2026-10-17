package com.edreams.mock.beta;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.stream.Stream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.stereotype.Component;

import com.edreams.mock.inventory.Carrier;
import com.edreams.mock.inventory.Carriers;
import com.edreams.mock.inventory.InventorySpec;
import com.edreams.mock.inventory.ProviderOffer;
import com.edreams.mock.inventory.Segment;

/**
 * Mock-Beta format: deeply nested XML written with StAX, one itinerary at a time. Prices are split
 * into base and taxes; {@code Total} is the comparable amount. Flight numbers are unpadded.
 */
@Component
public class BetaXmlWriter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final BigDecimal BASE_SHARE = new BigDecimal("0.88");

    private final InventorySpec spec;

    public BetaXmlWriter(InventorySpec spec) {
        this.spec = spec;
    }

    public void write(Stream<ProviderOffer> offers, OutputStream out) throws IOException {
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("BetaResponse");

            w.writeStartElement("Header");
            element(w, "Provider", "Mock-Beta");
            element(w, "GeneratedAt", ISO.format(spec.baseDate().atStartOfDay().atOffset(ZoneOffset.UTC)));
            element(w, "Seed", Long.toString(spec.seed()));
            w.writeEndElement();

            w.writeStartElement("Itineraries");
            int written = 0;
            for (Iterator<ProviderOffer> it = offers.iterator(); it.hasNext(); ) {
                itinerary(w, it.next());
                if (++written % 500 == 0) {
                    w.flush();
                }
            }
            w.writeEndElement();

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException e) {
            throw new IOException("XML streaming failed", e);
        }
    }

    private static void itinerary(XMLStreamWriter w, ProviderOffer offer) throws XMLStreamException {
        w.writeStartElement("Itinerary");
        w.writeAttribute("ref", offer.offerId());

        BigDecimal total = offer.price();
        BigDecimal base = total.multiply(BASE_SHARE).setScale(2, RoundingMode.HALF_UP);
        w.writeStartElement("Pricing");
        w.writeStartElement("Fare");
        w.writeAttribute("currency", offer.currency());
        element(w, "Base", base.toPlainString());
        element(w, "Taxes", total.subtract(base).toPlainString());
        element(w, "Total", total.toPlainString());
        w.writeEndElement();
        w.writeEndElement();

        w.writeStartElement("Legs");
        for (Segment leg : offer.legs()) {
            w.writeStartElement("Leg");
            w.writeStartElement("Carrier");
            element(w, "Code", leg.carrier());
            element(w, "Name", carrierName(leg.carrier()));
            w.writeEndElement();
            element(w, "FlightNumber", Integer.toString(leg.flightNumber()));
            w.writeStartElement("Departure");
            element(w, "Airport", leg.origin());
            element(w, "DateTime", ISO.format(leg.departure()));
            w.writeEndElement();
            w.writeStartElement("Arrival");
            element(w, "Airport", leg.destination());
            element(w, "DateTime", ISO.format(leg.arrival()));
            w.writeEndElement();
            w.writeEndElement();
        }
        w.writeEndElement();

        w.writeEndElement();
    }

    private static String carrierName(String code) {
        return Carriers.ALL.stream().filter(c -> c.code().equals(code)).map(Carrier::name).findFirst().orElse("");
    }

    private static void element(XMLStreamWriter w, String name, String text) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(text);
        w.writeEndElement();
    }
}
