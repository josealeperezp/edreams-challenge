package com.edreams.mock.inventory;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Static airport catalog: 24 airports, 5 of them hubs used for one-stop itineraries. */
public final class Airports {

    public static final List<Airport> ALL = List.of(
            airport("MAD", "Madrid Barajas", "Europe/Madrid", 40.472, -3.561),
            airport("BCN", "Barcelona El Prat", "Europe/Madrid", 41.297, 2.078),
            airport("LIS", "Lisbon Humberto Delgado", "Europe/Lisbon", 38.774, -9.134),
            airport("LHR", "London Heathrow", "Europe/London", 51.470, -0.454),
            airport("DUB", "Dublin", "Europe/Dublin", 53.421, -6.270),
            airport("CDG", "Paris Charles de Gaulle", "Europe/Paris", 49.010, 2.548),
            airport("AMS", "Amsterdam Schiphol", "Europe/Amsterdam", 52.310, 4.768),
            airport("FRA", "Frankfurt", "Europe/Berlin", 50.037, 8.562),
            airport("MUC", "Munich", "Europe/Berlin", 48.354, 11.786),
            airport("ZRH", "Zurich", "Europe/Zurich", 47.458, 8.555),
            airport("VIE", "Vienna", "Europe/Vienna", 48.110, 16.570),
            airport("BRU", "Brussels", "Europe/Brussels", 50.901, 4.484),
            airport("FCO", "Rome Fiumicino", "Europe/Rome", 41.800, 12.239),
            airport("MXP", "Milan Malpensa", "Europe/Rome", 45.630, 8.723),
            airport("ATH", "Athens", "Europe/Athens", 37.936, 23.944),
            airport("IST", "Istanbul", "Europe/Istanbul", 41.275, 28.752),
            airport("CPH", "Copenhagen", "Europe/Copenhagen", 55.618, 12.656),
            airport("ARN", "Stockholm Arlanda", "Europe/Stockholm", 59.650, 17.918),
            airport("OSL", "Oslo Gardermoen", "Europe/Oslo", 60.193, 11.100),
            airport("JFK", "New York JFK", "America/New_York", 40.641, -73.778),
            airport("MIA", "Miami", "America/New_York", 25.796, -80.287),
            airport("GRU", "Sao Paulo Guarulhos", "America/Sao_Paulo", -23.435, -46.473),
            airport("MEX", "Mexico City", "America/Mexico_City", 19.436, -99.072),
            airport("DXB", "Dubai", "Asia/Dubai", 25.253, 55.366));

    private static final Map<String, Airport> BY_CODE =
            ALL.stream().collect(Collectors.toUnmodifiableMap(Airport::code, Function.identity()));

    public static final List<Airport> HUBS = List.of(
            byCode("MAD"), byCode("LHR"), byCode("CDG"), byCode("FRA"), byCode("AMS"));

    private Airports() {
    }

    public static Airport byCode(String code) {
        Airport airport = BY_CODE.get(code);
        if (airport == null) {
            throw new IllegalArgumentException("Unknown airport: " + code);
        }
        return airport;
    }

    private static Airport airport(String code, String name, String zone, double lat, double lon) {
        return new Airport(code, name, ZoneId.of(zone), lat, lon);
    }
}
