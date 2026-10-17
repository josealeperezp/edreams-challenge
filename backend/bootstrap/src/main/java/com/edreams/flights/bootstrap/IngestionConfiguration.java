package com.edreams.flights.bootstrap;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.edreams.flights.application.ingestion.IngestionConfig;
import com.edreams.flights.application.ingestion.IngestionService;
import com.edreams.flights.application.port.out.FlightProviderPort;
import com.edreams.flights.application.port.out.IngestionEvents;
import com.edreams.flights.application.port.out.IngestionRunRepository;
import com.edreams.flights.application.port.out.ItineraryRepository;
import com.edreams.flights.application.port.out.ProviderOfferRepository;
import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.domain.model.ProviderId;
import com.edreams.flights.domain.rule.BestOfferSelector;
import com.edreams.flights.domain.rule.ItineraryValidator;
import com.edreams.flights.infrastructure.config.ProvidersProperties;
import com.edreams.flights.infrastructure.provider.alpha.AlphaFlightProvider;
import com.edreams.flights.infrastructure.provider.beta.BetaFlightProvider;
import com.edreams.flights.infrastructure.provider.gamma.GammaFlightProvider;
import com.edreams.flights.infrastructure.provider.http.ProviderHttpClients;
import com.edreams.flights.infrastructure.provider.resilience.ResilienceSettings;
import com.edreams.flights.infrastructure.provider.resilience.ResilientFlightProvider;
import com.edreams.flights.infrastructure.provider.resilience.ResilientProviders;

/** Wires the provider adapters behind their resilience decorators and the ingestion use case. */
@Configuration(proxyBeanMethods = false)
public class IngestionConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService ingestionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ResilientProviders resilientProviders(ProvidersProperties providers, FlightsProperties flights) {
        var r = flights.resilience();
        ResilienceSettings settings = new ResilienceSettings(r.retryAttempts(), r.retryInitialWait(), r.breakerWindow(),
                r.breakerFailureRatePercent(), r.breakerOpenDuration());
        var byId = providers.byId();
        RestClient alpha = client(byId.get(ProviderId.ALPHA));
        RestClient beta = client(byId.get(ProviderId.BETA));
        RestClient gamma = client(byId.get(ProviderId.GAMMA));
        return new ResilientProviders(List.of(
                new ResilientFlightProvider(new AlphaFlightProvider(alpha), settings),
                new ResilientFlightProvider(new BetaFlightProvider(beta), settings),
                new ResilientFlightProvider(new GammaFlightProvider(gamma), settings)));
    }

    @Bean
    IngestionService ingestionService(
            ResilientProviders providers,
            ItineraryValidator validator,
            BestOfferSelector selector,
            ProviderOfferRepository offers,
            ItineraryRepository itineraries,
            IngestionRunRepository runs,
            SearchCache cache,
            IngestionEvents events,
            ExecutorService ingestionExecutor,
            FlightsProperties flights,
            Clock clock) {
        var i = flights.ingestion();
        List<FlightProviderPort> ports = providers.asPorts();
        return new IngestionService(ports, validator, selector, offers, itineraries, runs, cache, events, ingestionExecutor,
                new IngestionConfig(i.batchSize(), i.providerBudget(), i.keepRuns()), clock);
    }

    private static RestClient client(ProvidersProperties.ProviderSettings settings) {
        return ProviderHttpClients.build(settings.baseUrl(), settings.timeout());
    }
}
