package com.edreams.flights.application.port.out;

import com.edreams.flights.application.ingestion.IngestionEvent;

public interface IngestionEvents {

    void publish(IngestionEvent event);
}
