package com.edreams.flights.application.port.in;

import java.util.List;
import java.util.Optional;

import com.edreams.flights.application.ingestion.IngestionRun;
import com.edreams.flights.application.ingestion.IngestionRunId;

public interface IngestionStatus {

    Optional<IngestionRun> latest();

    Optional<IngestionRun> find(IngestionRunId id);

    List<IngestionRun> list(int limit);
}
