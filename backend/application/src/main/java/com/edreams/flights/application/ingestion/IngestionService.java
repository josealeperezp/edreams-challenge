package com.edreams.flights.application.ingestion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.edreams.flights.application.port.in.IngestionStatus;
import com.edreams.flights.application.port.in.RunIngestion;
import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.IngestionEvents;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.OfferSink;
import com.edreams.flights.application.port.out.ProviderOfferRepository;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.ItinerarySignature;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;
import com.edreams.flights.domain.model.RawOffer;
import com.edreams.flights.domain.rule.BestOfferSelector;
import com.edreams.flights.domain.rule.ItineraryValidator;
import com.edreams.flights.domain.rule.RejectionReason;
import com.edreams.flights.domain.rule.Validation;

/**
 * Orchestrates one run: fan out to every provider in parallel, validate and store each provider's
 * offers as they stream in, then read the run back ordered by signature and let the domain pick the
 * best offer per itinerary. A provider dropping out never fails the run; only its rows are dropped.
 */
public final class IngestionService implements RunIngestion, IngestionStatus {

    private final List<FlightProviderPort> providers;
    private final ItineraryValidator validator;
    private final BestOfferSelector selector;
    private final ProviderOfferRepository offers;
    private final ItineraryRepository itineraries;
    private final IngestionRunRepository runs;
    private final SearchCache cache;
    private final IngestionEvents events;
    private final Executor executor;
    private final IngestionConfig config;
    private final Clock clock;
    private final AtomicReference<IngestionRunId> running = new AtomicReference<>();

    public IngestionService(
            List<FlightProviderPort> providers,
            ItineraryValidator validator,
            BestOfferSelector selector,
            ProviderOfferRepository offers,
            ItineraryRepository itineraries,
            IngestionRunRepository runs,
            SearchCache cache,
            IngestionEvents events,
            Executor executor,
            IngestionConfig config,
            Clock clock) {
        this.providers = List.copyOf(providers);
        this.validator = Objects.requireNonNull(validator);
        this.selector = Objects.requireNonNull(selector);
        this.offers = Objects.requireNonNull(offers);
        this.itineraries = Objects.requireNonNull(itineraries);
        this.runs = Objects.requireNonNull(runs);
        this.cache = Objects.requireNonNull(cache);
        this.events = Objects.requireNonNull(events);
        this.executor = Objects.requireNonNull(executor);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public IngestionRunId start() {
        IngestionRunId id = IngestionRunId.newId();
        if (!running.compareAndSet(null, id)) {
            throw new IngestionAlreadyRunning(running.get());
        }
        IngestionRun run = IngestionRun.started(id, clock.instant());
        runs.save(run);
        events.publish(IngestionEvent.runStarted(run));
        executor.execute(() -> execute(run));
        return id;
    }

    @Override
    public java.util.Optional<IngestionRun> latest() {
        return runs.latest();
    }

    @Override
    public java.util.Optional<IngestionRun> find(IngestionRunId id) {
        return runs.find(id);
    }

    @Override
    public List<IngestionRun> list(int limit) {
        return runs.latest(limit);
    }

    private void execute(IngestionRun started) {
        IngestionRun run = started;
        try {
            long deadline = System.nanoTime() + config.providerBudget().toNanos();
            Map<ProviderId, CompletableFuture<ProviderResult>> futures = new EnumMap<>(ProviderId.class);
            for (FlightProviderPort provider : providers) {
                futures.put(provider.id(), CompletableFuture.supplyAsync(() -> ingest(started.id(), provider), executor));
            }
            for (var entry : futures.entrySet()) {
                ProviderResult result = await(run.id(), entry.getKey(), entry.getValue(), deadline);
                run = run.withProvider(entry.getKey(), result);
                runs.save(run);
                events.publish(IngestionEvent.providerFinished(run, entry.getKey()));
            }

            long okCount = run.providers().values().stream().filter(ProviderResult::ok).count();
            int resolved = okCount > 0 ? resolve(run.id()) : 0;
            purge(run.id());
            cache.bumpVersion();

            IngestionRun.Status status = okCount == providers.size() ? IngestionRun.Status.COMPLETED
                    : okCount > 0 ? IngestionRun.Status.PARTIAL
                    : IngestionRun.Status.FAILED;
            run = run.finished(status, resolved, clock.instant());
        } catch (RuntimeException e) {
            run = run.failed(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant());
        } finally {
            runs.save(run);
            events.publish(IngestionEvent.runCompleted(run));
            running.set(null);
        }
    }

    private ProviderResult await(IngestionRunId runId, ProviderId provider, CompletableFuture<ProviderResult> future, long deadline) {
        try {
            long remaining = Math.max(1L, deadline - System.nanoTime());
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            offers.deleteForRun(runId, provider);
            return ProviderResult.failed(config.providerBudget().toMillis(), 0, 0, Map.of(),
                    "budget of " + config.providerBudget() + " exceeded");
        } catch (ExecutionException e) {
            offers.deleteForRun(runId, provider);
            return ProviderResult.failed(0, 0, 0, Map.of(), String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            offers.deleteForRun(runId, provider);
            return ProviderResult.failed(0, 0, 0, Map.of(), "interrupted");
        }
    }

    /** Streams one provider through validation into the store. Never throws; failure is a result. */
    private ProviderResult ingest(IngestionRunId runId, FlightProviderPort provider) {
        long startedAt = System.nanoTime();
        Counters counters = new Counters();
        List<ProviderOffer> batch = new ArrayList<>(config.batchSize());
        try {
            provider.fetch(new OfferSink() {
                @Override
                public void offer(RawOffer raw) {
                    counters.received++;
                    switch (validator.validate(raw)) {
                        case Validation.Valid valid -> {
                            counters.accepted++;
                            batch.add(valid.offer());
                            if (batch.size() >= config.batchSize()) {
                                offers.insertAll(runId, batch);
                                batch.clear();
                            }
                        }
                        case Validation.Rejected rejected -> counters.reject(rejected.reason());
                    }
                }

                @Override
                public void malformed(String detail) {
                    counters.received++;
                    counters.reject(RejectionReason.MALFORMED);
                }
            });
            if (!batch.isEmpty()) {
                offers.insertAll(runId, batch);
            }
            return ProviderResult.ok(elapsedMs(startedAt), counters.received, counters.accepted, counters.rejected);
        } catch (Exception e) {
            offers.deleteForRun(runId, provider.id());
            String error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return ProviderResult.failed(elapsedMs(startedAt), counters.received, counters.accepted, counters.rejected, error);
        }
    }

    /** Reads the run back ordered by signature and resolves each consecutive group to one itinerary. */
    private int resolve(IngestionRunId runId) {
        List<Itinerary> batch = new ArrayList<>(config.batchSize());
        List<ProviderOffer> group = new ArrayList<>(3);
        ItinerarySignature[] current = new ItinerarySignature[1];
        int[] resolved = {0};

        offers.forEachOfRunOrderedBySignature(runId, offer -> {
            ItinerarySignature signature = offer.signature();
            if (current[0] != null && !signature.equals(current[0])) {
                batch.add(selector.select(group));
                group.clear();
                if (batch.size() >= config.batchSize()) {
                    itineraries.upsertAll(runId, batch);
                    resolved[0] += batch.size();
                    batch.clear();
                }
            }
            current[0] = signature;
            group.add(offer);
        });
        if (!group.isEmpty()) {
            batch.add(selector.select(group));
        }
        if (!batch.isEmpty()) {
            itineraries.upsertAll(runId, batch);
            resolved[0] += batch.size();
        }
        return resolved[0];
    }

    private void purge(IngestionRunId current) {
        Set<IngestionRunId> keep = new LinkedHashSet<>();
        keep.add(current);
        runs.latest(config.keepRuns()).stream()
                .filter(r -> r.status() != IngestionRun.Status.FAILED)
                .map(IngestionRun::id)
                .forEach(keep::add);
        offers.purgeRunsNotIn(keep);
        itineraries.purgeNotConfirmedBy(keep);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static final class Counters {
        long received;
        long accepted;
        final Map<RejectionReason, Long> rejected = new EnumMap<>(RejectionReason.class);

        void reject(RejectionReason reason) {
            rejected.merge(reason, 1L, Long::sum);
        }
    }
}
