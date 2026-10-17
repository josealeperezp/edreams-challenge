package com.edreams.flights.infrastructure.provider;

import java.util.ArrayList;
import java.util.List;

import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.domain.model.RawOffer;

/** Sample bodies copied from the mock contracts, each with one deliberately broken record. */
public final class ProviderPayloads {

    private ProviderPayloads() {
    }

    public static final String ALPHA_JSON = """
            [
              {"offerId":"ALPHA-000001","price":89.9,"currency":"EUR","legs":[
                {"carrier":"IB","flightNumber":"0845","from":"MAD","to":"BCN","dep":"2026-11-02T07:15:00+01:00","arr":"2026-11-02T08:35:00+01:00"}]},
              {"offerId":"ALPHA-000002","price":245.0,"currency":"EUR","extra":{"ignored":[1,2]},"legs":[
                {"carrier":"IB","flightNumber":"6251","from":"MAD","to":"LHR","dep":"2026-11-02T09:40:00+01:00","arr":"2026-11-02T11:05:00Z"},
                {"carrier":"BA","flightNumber":"2845","from":"LHR","to":"DUB","dep":"2026-11-02T13:20:00Z","arr":"2026-11-02T14:45:00Z"}]},
              {"offerId":"ALPHA-000003","price":10.0,"currency":"EUR","legs":[
                {"carrier":"VY","flightNumber":"0212","from":"MAD","to":"BCN","dep":"not-a-date","arr":"2026-11-03T19:25:00+01:00"}]}
            ]
            """;

    public static final String BETA_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <BetaResponse>
              <Header><Provider>Mock-Beta</Provider><GeneratedAt>2026-10-16T00:00:00Z</GeneratedAt><Seed>42</Seed></Header>
              <Itineraries>
                <Itinerary ref="BETA-000001">
                  <Pricing><Fare currency="EUR"><Base>75.24</Base><Taxes>10.26</Taxes><Total>85.50</Total></Fare></Pricing>
                  <Legs><Leg>
                    <Carrier><Code>IB</Code><Name>Iberia</Name></Carrier><FlightNumber>845</FlightNumber>
                    <Departure><Airport>MAD</Airport><DateTime>2026-11-02T07:15:00+01:00</DateTime></Departure>
                    <Arrival><Airport>BCN</Airport><DateTime>2026-11-02T08:35:00+01:00</DateTime></Arrival>
                  </Leg></Legs>
                </Itinerary>
                <Itinerary ref="BETA-000002">
                  <Pricing><Fare currency="EUR"><Base>151.62</Base><Taxes>20.68</Taxes><Total>172.30</Total></Fare></Pricing>
                  <Legs>
                    <Leg><Carrier><Code>AF</Code><Name>Air France</Name></Carrier><FlightNumber>1801</FlightNumber>
                      <Departure><Airport>MAD</Airport><DateTime>2026-11-02T06:50:00+01:00</DateTime></Departure>
                      <Arrival><Airport>CDG</Airport><DateTime>2026-11-02T08:55:00+01:00</DateTime></Arrival></Leg>
                    <Leg><Carrier><Code>AF</Code><Name>Air France</Name></Carrier><FlightNumber>1180</FlightNumber>
                      <Departure><Airport>CDG</Airport><DateTime>2026-11-02T10:30:00+01:00</DateTime></Departure>
                      <Arrival><Airport>LHR</Airport><DateTime>2026-11-02T10:50:00+00:00</DateTime></Arrival></Leg>
                  </Legs>
                </Itinerary>
                <Itinerary ref="BETA-000003">
                  <Pricing><Fare currency="EUR"><Base>1</Base><Taxes>1</Taxes><Total>two</Total></Fare></Pricing>
                  <Legs><Leg><Carrier><Code>IB</Code></Carrier><FlightNumber>1</FlightNumber>
                    <Departure><Airport>MAD</Airport><DateTime>2026-11-02T07:15:00+01:00</DateTime></Departure>
                    <Arrival><Airport>BCN</Airport><DateTime>2026-11-02T08:35:00+01:00</DateTime></Arrival></Leg></Legs>
                </Itinerary>
              </Itineraries>
            </BetaResponse>
            """;

    public static final String GAMMA_CSV = """
            offer_id,flight,origin,destination,departure_utc,arrival_utc,price,currency,segment_index,segment_count
            GAMMA-000001,IB845,MAD,BCN,2026-11-02 06:15,2026-11-02 07:35,92.00,EUR,1,1
            GAMMA-000002,BA2845,LHR,DUB,2026-11-02 13:20,2026-11-02 14:45,239.00,EUR,2,2
            GAMMA-000002,IB6251,MAD,LHR,2026-11-02 08:40,2026-11-02 11:05,239.00,EUR,1,2
            GAMMA-000003,845,MAD,BCN,2026-11-02 06:15,2026-11-02 07:35,70.00,EUR,1,1
            GAMMA-000004,FR8392,BCN,DUB,2026-11-04T12:10,2026-11-04 13:55,64.99,EUR,1,1
            """;

    public static final class RecordingSink implements OfferSink {
        public final List<RawOffer> offers = new ArrayList<>();
        public final List<String> malformed = new ArrayList<>();

        @Override
        public void offer(RawOffer offer) {
            offers.add(offer);
        }

        @Override
        public void malformed(String detail) {
            malformed.add(detail);
        }
    }
}
