package com.edreams.flights.application.port.in;

import com.edreams.flights.application.ingestion.IngestionAlreadyRunning;
import com.edreams.flights.application.ingestion.IngestionRunId;

/** Starts an ingestion run asynchronously and returns its id. */
public interface RunIngestion {

    IngestionRunId start() throws IngestionAlreadyRunning;
}
