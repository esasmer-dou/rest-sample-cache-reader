package com.reactor.sample.cache.reader.app;

import com.reactor.rust.app.RestApplication;
import com.reactor.rust.cache.config.CacheProperties;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.core.RustCaches;
import com.reactor.sample.cache.reader.handler.CustomerCacheHandler;
import com.reactor.sample.cache.reader.service.CustomerCacheService;
import com.reactor.rust.health.HealthStarter;

public final class CacheReaderModule implements RestApplication.Module {

    public static final CacheReaderModule INSTANCE = new CacheReaderModule();

    private CacheReaderModule() {}

    @Override
    public void configure(RestApplication.ModuleContext context) {
        CacheProperties properties = CacheProperties.from(context.properties());
        RustCache cache = context.manage(RustCaches.create(properties.asProperties()));
        CustomerCacheService service = new CustomerCacheService(cache, properties);
        context.handlers(
                HealthStarter.application("rest-sample-cache-reader")
                        .required("redis-snapshot", 250, () -> service.meta().hit())
                        .build(),
                new CustomerCacheHandler(service));
    }
}
