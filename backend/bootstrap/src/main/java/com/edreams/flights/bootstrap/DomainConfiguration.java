package com.edreams.flights.bootstrap;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.edreams.flights.domain.rule.BestOfferSelector;
import com.edreams.flights.domain.rule.ConnectionPolicy;
import com.edreams.flights.domain.rule.ItineraryValidator;
import com.edreams.flights.domain.rule.PricingPolicy;

/** The domain has no framework annotations; its rules are instantiated here from configuration. */
@Configuration(proxyBeanMethods = false)
public class DomainConfiguration {

    @Bean
    ConnectionPolicy connectionPolicy(FlightsProperties properties) {
        return new ConnectionPolicy(properties.validation().minConnection(), properties.validation().maxLayover());
    }

    @Bean
    PricingPolicy pricingPolicy(FlightsProperties properties) {
        return new PricingPolicy(properties.pricing().markupPercent());
    }

    @Bean
    ItineraryValidator itineraryValidator(ConnectionPolicy policy) {
        return new ItineraryValidator(policy);
    }

    @Bean
    BestOfferSelector bestOfferSelector(PricingPolicy pricing) {
        return new BestOfferSelector(pricing);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
