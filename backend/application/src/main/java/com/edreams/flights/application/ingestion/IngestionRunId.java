package com.edreams.flights.application.ingestion;

import java.util.Objects;
import java.util.UUID;

public record IngestionRunId(UUID value) {

    public IngestionRunId {
        Objects.requireNonNull(value, "value");
    }

    public static IngestionRunId newId() {
        return new IngestionRunId(UUID.randomUUID());
    }

    public static IngestionRunId of(String raw) {
        return new IngestionRunId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
