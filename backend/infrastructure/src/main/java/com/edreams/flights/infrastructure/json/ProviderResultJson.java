package com.edreams.flights.infrastructure.json;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.edreams.flights.application.ingestion.ProviderResult;
import com.edreams.flights.domain.rule.RejectionReason;

public record ProviderResultJson(
        String outcome,
        long latencyMs,
        long received,
        long accepted,
        Map<String, Long> rejected,
        String error) {

    public static ProviderResultJson from(ProviderResult r) {
        Map<String, Long> rejected = new LinkedHashMap<>();
        r.rejected().forEach((reason, count) -> rejected.put(reason.name(), count));
        return new ProviderResultJson(r.outcome().name(), r.latencyMs(), r.received(), r.accepted(), rejected, r.error());
    }

    public ProviderResult toResult() {
        Map<RejectionReason, Long> reasons = new EnumMap<>(RejectionReason.class);
        if (rejected != null) {
            rejected.forEach((reason, count) -> reasons.put(RejectionReason.valueOf(reason), count));
        }
        return new ProviderResult(ProviderResult.Outcome.valueOf(outcome), latencyMs, received, accepted, reasons, error);
    }
}
