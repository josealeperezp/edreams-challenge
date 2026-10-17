package com.edreams.flights.infrastructure.json;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.edreams.flights.domain.model.ProviderQuote;
import com.edreams.flights.domain.model.Segment;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** One Jackson 3 mapper for the JSONB columns and the cache; the API uses Spring's own mapper. */
@Component
public class JsonCodec {

    private static final TypeReference<List<SegmentJson>> SEGMENTS = new TypeReference<>() { };
    private static final TypeReference<List<QuoteJson>> QUOTES = new TypeReference<>() { };
    private static final TypeReference<Map<String, ProviderResultJson>> RESULTS = new TypeReference<>() { };

    /** Offsets are part of the data (local departure times); Jackson must not shift them to UTC on read. */
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    public String writeSegments(List<Segment> segments) {
        return mapper.writeValueAsString(segments.stream().map(SegmentJson::from).toList());
    }

    public List<Segment> readSegments(String json) {
        return mapper.readValue(json, SEGMENTS).stream().map(SegmentJson::toSegment).toList();
    }

    public String writeQuotes(List<ProviderQuote> quotes) {
        return mapper.writeValueAsString(quotes.stream().map(QuoteJson::from).toList());
    }

    public List<ProviderQuote> readQuotes(String json) {
        return mapper.readValue(json, QUOTES).stream().map(QuoteJson::toQuote).toList();
    }

    public String writeProviderResults(Map<String, ProviderResultJson> results) {
        return mapper.writeValueAsString(results);
    }

    public Map<String, ProviderResultJson> readProviderResults(String json) {
        return mapper.readValue(json, RESULTS);
    }

    public String writePage(PageJson page) {
        return mapper.writeValueAsString(page);
    }

    public PageJson readPage(String json) {
        return mapper.readValue(json, PageJson.class);
    }

    public String writeStats(StatsJson stats) {
        return mapper.writeValueAsString(stats);
    }

    public StatsJson readStats(String json) {
        return mapper.readValue(json, StatsJson.class);
    }
}
