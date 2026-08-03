package com.reactor.sample.cache.reader.config;

import com.reactor.rust.di.annotation.Bean;
import com.reactor.rust.di.annotation.Configuration;
import com.reactor.rust.health.HealthEndpoint;
import com.reactor.rust.health.HealthStarter;
import com.reactor.sample.cache.reader.service.CustomerCacheService;

@Configuration
public final class CacheReaderConfiguration {

    @Bean
    public HealthEndpoint healthEndpoint(CustomerCacheService customerCache) {
        return HealthStarter.application("rest-sample-cache-reader")
                .required("redis-snapshot", 250, () -> customerCache.meta().hit())
                .build();
    }
}
