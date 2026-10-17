package com.edreams.flights.infrastructure.web.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.edreams.flights.application.ingestion.IngestionEvent;
import com.edreams.flights.application.port.out.IngestionEvents;
import com.edreams.flights.infrastructure.json.RunJson;

/**
 * Relays ingestion progress to dashboard clients over Server-Sent Events. New subscribers get the
 * last event immediately so they never start blind.
 */
@Component
public class SseIngestionEvents implements IngestionEvents {

    private static final Logger log = LoggerFactory.getLogger(SseIngestionEvents.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicReference<IngestionEvent> last = new AtomicReference<>();

    public record EventJson(String type, String provider, RunJson run) {
        static EventJson from(IngestionEvent event) {
            return new EventJson(eventName(event), event.provider() == null ? null : event.provider().name(), RunJson.from(event.run()));
        }
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Duration.ofHours(6).toMillis());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        IngestionEvent replay = last.get();
        if (replay != null) {
            send(emitter, replay);
        }
        return emitter;
    }

    @Override
    public void publish(IngestionEvent event) {
        last.set(event);
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
    }

    public int subscribers() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, IngestionEvent event) {
        try {
            emitter.send(SseEmitter.event().name(eventName(event)).data(EventJson.from(event)));
        } catch (IOException | IllegalStateException e) {
            log.warn("Dropping SSE subscriber after {} on {}: {}", e.getClass().getSimpleName(), eventName(event), e.getMessage());
            emitters.remove(emitter);
        }
    }

    static String eventName(IngestionEvent event) {
        return event.type().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
