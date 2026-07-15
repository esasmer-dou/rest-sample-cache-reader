package com.reactor.sample.cache.reader.app;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.cache.config.CacheProperties;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.core.RustCaches;
import com.reactor.sample.cache.reader.handler.CustomerCacheHandler;
import com.reactor.sample.cache.reader.handler.HealthHandler;
import com.reactor.sample.cache.reader.service.CustomerCacheService;

public final class RestSampleCacheReaderApplication {

    private RestSampleCacheReaderApplication() {}

    public static void main(String[] args) {
        RestApplication.builder()
                .shutdownThreadName("cache-reader-shutdown")
                .module(context -> {
                    CacheProperties properties = CacheProperties.from(context.properties());
                    RustCache cache = context.manage(RustCaches.create(properties.asProperties()));
                    CustomerCacheService service = new CustomerCacheService(cache, properties);
                    context.handlers(new HealthHandler(), new CustomerCacheHandler(service));
                })
                .start();
    }
}
