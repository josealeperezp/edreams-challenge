package com.edreams.mock.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A provider's view of the master inventory: which itineraries it carries, at what price, and
 * which copies are corrupted. All decisions hash the seed, the index and the provider.
 */
public final class ProviderView {

    private static final long SALT_SHARE = 11;
    private static final long SALT_PRICE = 23;
    private static final long SALT_CORRUPT = 37;
    private static final long SALT_KIND = 41;

    private final ProviderId provider;
    private final InventorySpec spec;
    private final InventoryGenerator generator;
    private final double share;
    private volatile Counts counts;

    /** Number of offers and of segment rows this provider streams over the full inventory. */
    public record Counts(long offers, long segments) {
    }

    public ProviderView(ProviderId provider, InventorySpec spec, InventoryGenerator generator) {
        this.provider = provider;
        this.spec = spec;
        this.generator = generator;
        this.share = spec.share(provider);
    }

    public ProviderId provider() {
        return provider;
    }

    public InventorySpec spec() {
        return spec;
    }

    public double share() {
        return share;
    }

    public boolean includes(long index) {
        return Hashing.unit(Hashing.mix(spec.seed(), SALT_SHARE, provider.ordinal(), index)) < share;
    }

    public ProviderOffer offer(long index) {
        MasterItinerary master = generator.itinerary(index);
        double factor = 0.90 + 0.25 * Hashing.unit(Hashing.mix(spec.seed(), SALT_PRICE, provider.ordinal(), index));
        BigDecimal price = master.basePrice().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        Corruption corruption = null;
        if (Hashing.unit(Hashing.mix(spec.seed(), SALT_CORRUPT, provider.ordinal(), index)) < spec.corruptionRate()) {
            corruption = Corruption.pick(Hashing.mix(spec.seed(), SALT_KIND, provider.ordinal(), index), master.segments().size());
        }
        List<Segment> legs = corruption == null ? master.segments() : corruption.apply(master.segments());
        String offerId = provider.offerPrefix() + "%06d".formatted(index);
        return new ProviderOffer(offerId, provider, legs, price, master.currency(), corruption);
    }

    public Stream<ProviderOffer> stream(InventoryQuery query) {
        return LongStream.range(0, spec.size())
                .filter(this::includes)
                .mapToObj(this::offer)
                .filter(query::matches);
    }

    public Counts counts() {
        Counts cached = counts;
        if (cached == null) {
            long offers = 0;
            long segments = 0;
            for (long index = 0; index < spec.size(); index++) {
                if (includes(index)) {
                    offers++;
                    segments += generator.segmentCount(index);
                }
            }
            cached = new Counts(offers, segments);
            counts = cached;
        }
        return cached;
    }
}
