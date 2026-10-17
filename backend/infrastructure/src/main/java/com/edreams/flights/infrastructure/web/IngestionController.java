package com.edreams.flights.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.edreams.flights.application.ingestion.IngestionRunId;
import com.edreams.flights.application.port.in.IngestionStatus;
import com.edreams.flights.application.port.in.RunIngestion;
import com.edreams.flights.infrastructure.json.RunJson;
import com.edreams.flights.infrastructure.web.sse.SseIngestionEvents;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final RunIngestion runIngestion;
    private final IngestionStatus status;
    private final SseIngestionEvents events;

    public IngestionController(RunIngestion runIngestion, IngestionStatus status, SseIngestionEvents events) {
        this.runIngestion = runIngestion;
        this.status = status;
        this.events = events;
    }

    /** Starts a run; 202 with its id, 409 when one is already running. */
    @PostMapping("/runs")
    public ResponseEntity<Map<String, String>> start() {
        IngestionRunId id = runIngestion.start();
        return ResponseEntity.accepted()
                .location(URI.create("/api/ingestion/runs/" + id))
                .body(Map.of("runId", id.toString()));
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<RunJson> latest() {
        return status.latest().map(RunJson::from).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<RunJson> find(@PathVariable String id) {
        return status.find(IngestionRunId.of(id)).map(RunJson::from).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs")
    public List<RunJson> list(@RequestParam(defaultValue = "10") int limit) {
        return status.list(Math.clamp(limit, 1, 100)).stream().map(RunJson::from).toList();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe();
    }
}
