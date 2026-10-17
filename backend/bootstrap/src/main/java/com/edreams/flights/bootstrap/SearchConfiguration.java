package com.edreams.flights.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.application.search.SearchService;
import com.edreams.flights.infrastructure.cache.RedisSearchCache;
import com.edreams.flights.infrastructure.json.JsonCodec;

@Configuration(proxyBeanMethods = false)
public class SearchConfiguration {

    @Bean
    SearchCache searchCache(StringRedisTemplate redis, JsonCodec json, FlightsProperties flights) {
        return new RedisSearchCache(redis, json, flights.search().cacheTtl());
    }

    @Bean
    SearchService searchService(ItineraryRepository itineraries, SearchCache cache, IngestionRunRepository runs) {
        return new SearchService(itineraries, cache, runs);
    }
}
