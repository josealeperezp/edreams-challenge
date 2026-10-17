package com.edreams.flights.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.model.ProviderOffer;

/** Per-run, per-provider validated offers. Inserts are idempotent per (run, signature, provider). */
public interface ProviderOfferRepository {

    void insertAll(IngestionRunId run, List<ProviderOffer> offers);

    void deleteForRun(IngestionRunId run, ProviderId provider);

    /** Streams every offer of the run ordered by signature, so equal itineraries arrive consecutively. */
    void forEachOfRunOrderedBySignature(IngestionRunId run, Consumer<ProviderOffer> consumer);

    int purgeRunsNotIn(Collection<IngestionRunId> keep);
}
