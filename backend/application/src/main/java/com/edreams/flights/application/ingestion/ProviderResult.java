package com.edreams.flights.application.ingestion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.edreams.flights.domain.rule.RejectionReason;

/** What one provider contributed to a run: counts, latency and, when it dropped out, why. */
public record ProviderResult(
        Outcome outcome,
        long latencyMs,
        long received,
        long accepted,
        Map<RejectionReason, Long> rejected,
        String error) {

    public enum Outcome { OK, FAILED }

    public ProviderResult {
        var copy = new EnumMap<RejectionReason, Long>(RejectionReason.class);
        if (rejected != null) {
            copy.putAll(rejected);
        }
        rejected = Collections.unmodifiableMap(copy);
    }

    public static ProviderResult ok(long latencyMs, long received, long accepted, Map<RejectionReason, Long> rejected) {
        return new ProviderResult(Outcome.OK, latencyMs, received, accepted, rejected, null);
    }

    public static ProviderResult failed(long latencyMs, long received, long accepted, Map<RejectionReason, Long> rejected, String error) {
        return new ProviderResult(Outcome.FAILED, latencyMs, received, accepted, rejected, error);
    }

    public boolean ok() {
        return outcome == Outcome.OK;
    }

    public long rejectedTotal() {
        return rejected.values().stream().mapToLong(Long::longValue).sum();
    }
}
