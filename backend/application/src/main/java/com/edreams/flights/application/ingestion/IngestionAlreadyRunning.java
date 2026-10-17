package com.edreams.flights.application.ingestion;

/** Thrown when a run is requested while another one is still executing. */
public class IngestionAlreadyRunning extends RuntimeException {

    private final IngestionRunId runningId;

    public IngestionAlreadyRunning(IngestionRunId runningId) {
        super("Ingestion run " + runningId + " is still running");
        this.runningId = runningId;
    }

    public IngestionRunId runningId() {
        return runningId;
    }
}
