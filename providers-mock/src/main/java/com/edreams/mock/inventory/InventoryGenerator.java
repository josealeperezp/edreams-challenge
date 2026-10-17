package com.edreams.mock.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Pure function from an inventory index to a valid itinerary. Nothing is cached, so streaming a
 * million itineraries costs constant memory and the output is identical on every run.
 */
public final class InventoryGenerator {

    private static final LocalTime[] SLOTS = {
            LocalTime.of(6, 30), LocalTime.of(8, 15), LocalTime.of(10, 0), LocalTime.of(12, 30),
            LocalTime.of(14, 45), LocalTime.of(16, 30), LocalTime.of(18, 40), LocalTime.of(21, 10)
    };
    private static final double[] FARE_FACTORS = {0.80, 1.00, 1.35, 1.90};

    private static final long SALT_CARRIER = 101;
    private static final long SALT_JITTER = 103;
    private static final long SALT_FLIGHT = 107;
    private static final long SALT_HUB = 109;
    private static final long SALT_LAYOVER = 113;
    private static final long SALT_FARE = 127;

    private final InventorySpec spec;

    public InventoryGenerator(InventorySpec spec) {
        this.spec = spec;
    }

    public InventorySpec spec() {
        return spec;
    }

    public MasterItinerary itinerary(long index) {
        if (index < 0 || index >= spec.size()) {
            throw new IndexOutOfBoundsException("index " + index + " outside [0, " + spec.size() + ")");
        }
        int day = (int) (index % spec.days());
        int gridIndex = (int) (index / spec.days());
        LocalDate date = spec.baseDate().plusDays(day);
        // Cells interleave as two direct flights then one connection, so any prefix of the
        // inventory holds one-stop itineraries. The ratio matches directPerDay : connectionsPerDay.
        int block = gridIndex / 3;
        int kind = gridIndex % 3;
        List<Segment> segments = kind < 2
                ? List.of(direct(block * 2 + kind, date))
                : connection(block, date);
        return new MasterItinerary(index, segments, price(index, segments), "EUR");
    }

    /** Segment count without building the itinerary, for cheap row counting. */
    public int segmentCount(long index) {
        return (index / spec.days()) % 3 == 2 ? 2 : 1;
    }

    private Segment direct(int gridIndex, LocalDate date) {
        int variant = gridIndex % InventorySpec.CARRIER_VARIANTS;
        int rest = gridIndex / InventorySpec.CARRIER_VARIANTS;
        int slot = rest % InventorySpec.SLOTS_PER_DAY;
        int route = rest / InventorySpec.SLOTS_PER_DAY;
        return flight(carrier(route, variant), Routes.origin(route), Routes.destination(route), date, slot);
    }

    private List<Segment> connection(int gridIndex, LocalDate date) {
        int slot = gridIndex % InventorySpec.SLOTS_PER_DAY;
        int route = gridIndex / InventorySpec.SLOTS_PER_DAY;
        Airport origin = Routes.origin(route);
        Airport destination = Routes.destination(route);
        Airport hub = hub(origin, destination, route);
        int firstRoute = Routes.index(origin, hub);
        int secondRoute = Routes.index(hub, destination);
        Segment first = flight(carrier(firstRoute, 0), origin, hub, date, slot);
        int layoverMinutes = 50 + Hashing.bounded(Hashing.mix(spec.seed(), SALT_LAYOVER, route, slot), 131);
        OffsetDateTime secondDeparture = first.arrival().plusMinutes(layoverMinutes);
        Segment second = flightAt(carrier(secondRoute, 0), hub, destination, secondDeparture, slot);
        return List.of(first, second);
    }

    private Segment flight(Carrier carrier, Airport origin, Airport destination, LocalDate date, int slot) {
        int jitter = Hashing.bounded(Hashing.mix(spec.seed(), SALT_JITTER, Routes.index(origin, destination), slot), 15);
        LocalTime time = SLOTS[slot].plusMinutes(jitter);
        OffsetDateTime departure = ZonedDateTime.of(date, time, origin.zone()).toOffsetDateTime();
        return flightAt(carrier, origin, destination, departure, slot);
    }

    private Segment flightAt(Carrier carrier, Airport origin, Airport destination, OffsetDateTime departure, int slot) {
        OffsetDateTime arrival = departure.toInstant()
                .plus(duration(origin, destination))
                .atZone(destination.zone())
                .toOffsetDateTime();
        int number = 100 + slot + Hashing.bounded(
                Hashing.mix(spec.seed(), SALT_FLIGHT, carrier.code().hashCode(), Routes.index(origin, destination)), 1200);
        return new Segment(carrier.code(), number, origin.code(), destination.code(), departure, arrival);
    }

    private Carrier carrier(int route, int variant) {
        int n = Carriers.ALL.size();
        int first = Hashing.bounded(Hashing.mix(spec.seed(), SALT_CARRIER, route), n);
        if (variant == 0) {
            return Carriers.ALL.get(first);
        }
        int offset = 1 + Hashing.bounded(Hashing.mix(spec.seed(), SALT_CARRIER, route, variant), n - 1);
        return Carriers.ALL.get((first + offset) % n);
    }

    private Airport hub(Airport origin, Airport destination, int route) {
        List<Airport> candidates = Airports.HUBS.stream()
                .filter(h -> !h.equals(origin) && !h.equals(destination))
                .toList();
        return candidates.get(Hashing.bounded(Hashing.mix(spec.seed(), SALT_HUB, route), candidates.size()));
    }

    static Duration duration(Airport origin, Airport destination) {
        double minutes = 30 + origin.distanceKmTo(destination) / 800.0 * 60;
        long roundedToFive = (long) Math.ceil(minutes / 5) * 5;
        return Duration.ofMinutes(roundedToFive);
    }

    private BigDecimal price(long index, List<Segment> segments) {
        double km = 0;
        for (Segment segment : segments) {
            km += Airports.byCode(segment.origin()).distanceKmTo(Airports.byCode(segment.destination()));
        }
        double fare = FARE_FACTORS[Hashing.bounded(Hashing.mix(spec.seed(), SALT_FARE, index), FARE_FACTORS.length)];
        double dayOfWeek = switch (segments.getFirst().departure().getDayOfWeek()) {
            case FRIDAY, SUNDAY -> 1.15;
            case TUESDAY, WEDNESDAY -> 0.90;
            default -> 1.0;
        };
        double connectionDiscount = segments.size() > 1 ? 0.85 : 1.0;
        double value = (25 + 0.085 * km) * fare * dayOfWeek * connectionDiscount;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
