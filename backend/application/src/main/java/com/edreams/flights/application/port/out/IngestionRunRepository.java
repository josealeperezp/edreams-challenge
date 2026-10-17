package com.edreams.flights.application.port.out;

import java.util.List;
import java.util.Optional;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;

public interface IngestionRunRepository {

    void save(IngestionRun run);

    Optional<IngestionRun> find(IngestionRunId id);

    Optional<IngestionRun> latest();

    /** Most recent first. */
    List<IngestionRun> latest(int limit);
}
