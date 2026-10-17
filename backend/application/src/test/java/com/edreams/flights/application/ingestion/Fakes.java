package com.edreams.flights.application.ingestion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.IngestionEvents;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.application.port.out.ProviderOfferRepository;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.application.search.ItineraryRow;
import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchQuery;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.RawOffer;

/** In-memory ports so the orchestration is tested without Spring, HTTP or a database. */
final class Fakes {

    private Fakes() {
    }

    static final class FakeProvider implements FlightProviderPort {
        private final ProviderId id;
        private final List<RawOffer> offers;
        private int malformed;
        private int failAfter = Integer.MAX_VALUE;
        private RuntimeException failure;
        private Runnable beforeFetch;

        FakeProvider(ProviderId id, List<RawOffer> offers) {
            this.id = id;
            this.offers = offers;
        }

        FakeProvider withMalformed(int count) {
            this.malformed = count;
            return this;
        }

        FakeProvider failingAfter(int offersSent, RuntimeException failure) {
            this.failAfter = offersSent;
            this.failure = failure;
            return this;
        }

        FakeProvider beforeFetch(Runnable hook) {
            this.beforeFetch = hook;
            return this;
        }

        @Override
        public ProviderId id() {
            return id;
        }

        @Override
        public void fetch(OfferSink sink) {
            if (beforeFetch != null) {
                beforeFetch.run();
            }
            int sent = 0;
            for (RawOffer offer : offers) {
                if (sent == failAfter) {
                    throw failure;
                }
                sink.offer(offer);
                sent++;
            }
            for (int i = 0; i < malformed; i++) {
                sink.malformed("unparseable row " + i);
            }
            if (failure != null && failAfter >= offers.size()) {
                throw failure;
            }
        }
    }

    static final class InMemoryProviderOffers implements ProviderOfferRepository {
        /** run -> provider -> signature -> offer */
        final Map<IngestionRunId, Map<ProviderId, Map<String, ProviderOffer>>> store = new HashMap<>();

        @Override
        public void insertAll(IngestionRunId run, List<ProviderOffer> offers) {
            for (ProviderOffer offer : offers) {
                store.computeIfAbsent(run, r -> new HashMap<>())
                        .computeIfAbsent(offer.provider(), p -> new HashMap<>())
                        .putIfAbsent(offer.signature().value(), offer);
            }
        }

        @Override
        public void deleteForRun(IngestionRunId run, ProviderId provider) {
            Map<ProviderId, Map<String, ProviderOffer>> byProvider = store.get(run);
            if (byProvider != null) {
                byProvider.remove(provider);
            }
        }

        @Override
        public void forEachOfRunOrderedBySignature(IngestionRunId run, Consumer<ProviderOffer> consumer) {
            store.getOrDefault(run, Map.of()).values().stream()
                    .flatMap(m -> m.values().stream())
                    .sorted(Comparator.comparing((ProviderOffer o) -> o.signature().value()).thenComparing(ProviderOffer::provider))
                    .forEach(consumer);
        }

        @Override
        public int purgeRunsNotIn(Collection<IngestionRunId> keep) {
            int before = store.size();
            store.keySet().retainAll(keep);
            return before - store.size();
        }

        int count(IngestionRunId run, ProviderId provider) {
            return store.getOrDefault(run, Map.of()).getOrDefault(provider, Map.of()).size();
        }
    }

    static final class InMemoryItineraries implements ItineraryRepository {
        record Stored(long id, Itinerary itinerary, IngestionRunId confirmedBy) {
        }

        final Map<String, Stored> bySignature = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public void upsertAll(IngestionRunId run, List<Itinerary> itineraries) {
            for (Itinerary itinerary : itineraries) {
                Stored existing = bySignature.get(itinerary.signature().value());
                long id = existing == null ? nextId++ : existing.id();
                bySignature.put(itinerary.signature().value(), new Stored(id, itinerary, run));
            }
        }

        @Override
        public int purgeNotConfirmedBy(Collection<IngestionRunId> keep) {
            int before = bySignature.size();
            bySignature.values().removeIf(s -> !keep.contains(s.confirmedBy()));
            return before - bySignature.size();
        }

        @Override
        public List<ItineraryRow> search(SearchQuery query, int limit) {
            throw new UnsupportedOperationException("not needed by ingestion tests");
        }

        @Override
        public SearchStats stats(SearchQuery query) {
            throw new UnsupportedOperationException("not needed by ingestion tests");
        }

        @Override
        public long approximateCount() {
            return bySignature.size();
        }

        Itinerary get(String signature) {
            return bySignature.get(signature).itinerary();
        }
    }

    static final class InMemoryRuns implements IngestionRunRepository {
        final Map<IngestionRunId, IngestionRun> runs = new LinkedHashMap<>();

        @Override
        public void save(IngestionRun run) {
            runs.put(run.id(), run);
        }

        @Override
        public Optional<IngestionRun> find(IngestionRunId id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public Optional<IngestionRun> latest() {
            return latest(1).stream().findFirst();
        }

        @Override
        public List<IngestionRun> latest(int limit) {
            List<IngestionRun> all = new ArrayList<>(runs.values());
            all.sort(Comparator.comparing(IngestionRun::startedAt).reversed());
            return all.subList(0, Math.min(limit, all.size()));
        }
    }

    static final class FakeCache implements SearchCache {
        long version;
        final Map<String, SearchPage> pages = new HashMap<>();
        final Map<String, SearchStats> stats = new HashMap<>();

        @Override
        public long currentVersion() {
            return version;
        }

        @Override
        public long bumpVersion() {
            return ++version;
        }

        @Override
        public Optional<SearchPage> get(String key) {
            return Optional.ofNullable(pages.get(key));
        }

        @Override
        public void put(String key, SearchPage page) {
            pages.put(key, page);
        }

        @Override
        public Optional<SearchStats> getStats(String key) {
            return Optional.ofNullable(stats.get(key));
        }

        @Override
        public void putStats(String key, SearchStats value) {
            stats.put(key, value);
        }
    }

    static final class RecordingEvents implements IngestionEvents {
        final List<IngestionEvent> events = new ArrayList<>();

        @Override
        public void publish(IngestionEvent event) {
            events.add(event);
        }

        List<IngestionEvent.Type> types() {
            return events.stream().map(IngestionEvent::type).toList();
        }
    }
}
