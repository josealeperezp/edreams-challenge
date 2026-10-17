package com.edreams.mock.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.edreams.mock.config.MockProperties;

@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfiguration.class);

    @Bean
    InventorySpec inventorySpec(MockProperties properties) {
        InventorySpec spec = InventorySpec.from(properties);
        log.info("Mock inventory: seed={} size={} days={} from {} (capacity {}) corruptionRate={} shares={}",
                spec.seed(), spec.size(), spec.days(), spec.baseDate(), spec.capacity(), spec.corruptionRate(), spec.shares());
        return spec;
    }

    @Bean
    InventoryGenerator inventoryGenerator(InventorySpec spec) {
        return new InventoryGenerator(spec);
    }

    @Bean
    ProviderViews providerViews(InventorySpec spec, InventoryGenerator generator) {
        return new ProviderViews(spec, generator);
    }
}
