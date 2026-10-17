package com.edreams.flights.application.ingestion;

import static com.edreams.flights.application.ingestion.TestOffers.bcnLhr;
import static com.edreams.flights.application.ingestion.TestOffers.deadLeg;
import static com.edreams.flights.application.ingestion.TestOffers.madBcn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.edreams.flights.application.ingestion.Fakes.FakeCache;
import com.edreams.flights.application.ingestion.Fakes.FakeProvider;
import com.edreams.flights.application.ingestion.Fakes.InMemoryItineraries;
import com.edreams.flights.application.ingestion.Fakes.InMemoryProviderOffers;
import com.edreams.flights.application.ingestion.Fakes.InMemoryRuns;
import com.edreams.flights.application.ingestion.Fakes.RecordingEvents;
import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.domain.model.Itinerary;
import com.edreams.flights.domain.model.Money;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.rule.BestOfferSelector;
import com.edreams.flights.domain.rule.ConnectionPolicy;
import com.edreams.flights.domain.rule.ItineraryValidator;
import com.edreams.flights.domain.rule.PricingPolicy;
import com.edreams.flights.domain.rule.RejectionReason;

class IngestionServiceTest {

    private static final String MAD_BCN = "IB845|MAD|BCN|2026-11-02T06:15:00Z";

    private final InMemoryProviderOffers offers = new InMemoryProviderOffers();
    private final InMemoryItineraries itineraries = new InMemoryItineraries();
    private final InMemoryRuns runs = new InMemoryRuns();
    private final FakeCache cache = new FakeCache();
    private final RecordingEvents events = new RecordingEvents();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-17T10:00:00Z"));

    private IngestionService service(int keepRuns, FlightProviderPort... providers) {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.getAndUpdate(t -> t.plusSeconds(1)); }
        };
        return new IngestionService(
                List.of(providers),
                new ItineraryValidator(new ConnectionPolicy(Duration.ofMinutes(30), Duration.ofHours(24))),
                new BestOfferSelector(PricingPolicy.ofPercent("5")),
                offers, itineraries, runs, cache, events,
                Runnable::run,
                new IngestionConfig(2, Duration.ofSeconds(30), keepRuns),
                clock);
    }

    @Test
    void completedRunResolvesTheCheapestOfferPerItinerary() {
        IngestionService service = service(3,
                new FakeProvider(ProviderId.ALPHA, List.of(madBcn(ProviderId.ALPHA, "89.90"), bcnLhr(ProviderId.ALPHA, "100.00"))),
                new FakeProvider(ProviderId.BETA, List.of(madBcn(ProviderId.BETA, "85.50"), deadLeg(ProviderId.BETA))),
                new FakeProvider(ProviderId.GAMMA, List.of(madBcn(ProviderId.GAMMA, "92.00"))).withMalformed(1));

        IngestionRunId id = service.start();

        IngestionRun run = runs.find(id).orElseThrow();
        assertThat(run.status()).isEqualTo(IngestionRun.Status.COMPLETED);
        assertThat(run.itineraries()).isEqualTo(2);
        assertThat(run.finishedAt()).isAfter(run.startedAt());

        ProviderResult beta = run.providers().get(ProviderId.BETA);
        assertThat(beta.ok()).isTrue();
        assertThat(beta.received()).isEqualTo(2);
        assertThat(beta.accepted()).isEqualTo(1);
        assertThat(beta.rejected()).containsEntry(RejectionReason.DEAD_LEG, 1L);
        assertThat(run.providers().get(ProviderId.GAMMA).rejected()).containsEntry(RejectionReason.MALFORMED, 1L);

        Itinerary shared = itineraries.get(MAD_BCN);
        assertThat(shared.bestProvider()).isEqualTo(ProviderId.BETA);
        assertThat(shared.basePrice()).isEqualTo(Money.eur("85.50"));
        assertThat(shared.price()).isEqualTo(Money.eur("89.78"));
        assertThat(shared.quotes()).hasSize(3);

        Itinerary alphaOnly = itineraries.get("VY1002|BCN|LHR|2026-11-02T11:15:00Z");
        assertThat(alphaOnly.bestProvider()).isEqualTo(ProviderId.ALPHA);
        assertThat(alphaOnly.price()).isEqualTo(Money.eur("105.00"));

        assertThat(cache.version).isEqualTo(1);
        assertThat(events.types()).containsExactly(
                IngestionEvent.Type.RUN_STARTED,
                IngestionEvent.Type.PROVIDER_FINISHED, IngestionEvent.Type.PROVIDER_FINISHED, IngestionEvent.Type.PROVIDER_FINISHED,
                IngestionEvent.Type.RUN_COMPLETED);
    }

    @Test
    void aFailingProviderGivesPartialAndItsRowsAreDropped() {
        IngestionService service = service(3,
                new FakeProvider(ProviderId.ALPHA, List.of(madBcn(ProviderId.ALPHA, "89.90"))),
                new FakeProvider(ProviderId.BETA, List.of(madBcn(ProviderId.BETA, "85.50"), bcnLhr(ProviderId.BETA, "70")))
                        .failingAfter(1, new IllegalStateException("upstream 500")),
                new FakeProvider(ProviderId.GAMMA, List.of(madBcn(ProviderId.GAMMA, "92.00"))));

        IngestionRunId id = service.start();

        IngestionRun run = runs.find(id).orElseThrow();
        assertThat(run.status()).isEqualTo(IngestionRun.Status.PARTIAL);
        ProviderResult beta = run.providers().get(ProviderId.BETA);
        assertThat(beta.ok()).isFalse();
        assertThat(beta.error()).contains("IllegalStateException").contains("upstream 500");
        assertThat(offers.count(id, ProviderId.BETA)).isZero();

        Itinerary shared = itineraries.get(MAD_BCN);
        assertThat(shared.bestProvider()).isEqualTo(ProviderId.ALPHA);
        assertThat(shared.price()).isEqualTo(Money.eur("94.40"));
        assertThat(shared.quotes()).hasSize(2);
        assertThat(run.itineraries()).isEqualTo(1);
    }

    @Test
    void everyProviderFailingGivesFailedWithoutResolving() {
        IngestionService service = service(3,
                new FakeProvider(ProviderId.ALPHA, List.of()).failingAfter(0, new RuntimeException("down")),
                new FakeProvider(ProviderId.BETA, List.of()).failingAfter(0, new RuntimeException("down")),
                new FakeProvider(ProviderId.GAMMA, List.of()).failingAfter(0, new RuntimeException("down")));

        IngestionRunId id = service.start();

        IngestionRun run = runs.find(id).orElseThrow();
        assertThat(run.status()).isEqualTo(IngestionRun.Status.FAILED);
        assertThat(run.itineraries()).isZero();
        assertThat(itineraries.bySignature).isEmpty();
        assertThat(events.types()).endsWith(IngestionEvent.Type.RUN_COMPLETED);
    }

    @Test
    void aSecondStartWhileRunningIsRejectedAndTheGuardIsReleasedAfterwards() {
        AtomicReference<IngestionService> holder = new AtomicReference<>();
        AtomicReference<Throwable> secondStart = new AtomicReference<>();
        FakeProvider alpha = new FakeProvider(ProviderId.ALPHA, List.of(madBcn(ProviderId.ALPHA, "89.90")))
                .beforeFetch(() -> {
                    try {
                        holder.get().start();
                    } catch (RuntimeException e) {
                        secondStart.set(e);
                    }
                });
        IngestionService service = service(3, alpha);
        holder.set(service);

        IngestionRunId first = service.start();

        assertThat(secondStart.get()).isInstanceOf(IngestionAlreadyRunning.class);
        assertThat(((IngestionAlreadyRunning) secondStart.get()).runningId()).isEqualTo(first);
        assertThat(runs.find(first).orElseThrow().status()).isEqualTo(IngestionRun.Status.COMPLETED);
        assertThat(service.start()).isNotEqualTo(first);
    }

    @Test
    void purgeKeepsOnlyTheConfiguredNumberOfRuns() {
        FakeProvider withBoth = new FakeProvider(ProviderId.ALPHA, List.of(madBcn(ProviderId.ALPHA, "89.90"), bcnLhr(ProviderId.ALPHA, "100")));
        IngestionService first = service(1, withBoth);
        IngestionRunId firstRun = first.start();
        assertThat(itineraries.bySignature).hasSize(2);

        FakeProvider onlyShared = new FakeProvider(ProviderId.ALPHA, List.of(madBcn(ProviderId.ALPHA, "80.00")));
        IngestionService second = service(1, onlyShared);
        IngestionRunId secondRun = second.start();

        assertThat(offers.store).containsOnlyKeys(secondRun);
        assertThat(itineraries.bySignature).containsOnlyKeys(MAD_BCN);
        assertThat(itineraries.get(MAD_BCN).price()).isEqualTo(Money.eur("84.00"));
        assertThat(runs.find(firstRun)).isPresent();
    }

    @Test
    void configRejectsNonsense() {
        assertThatThrownBy(() -> new IngestionConfig(0, Duration.ofSeconds(1), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IngestionConfig(1, Duration.ZERO, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
