package com.reactor.sample.cache.reader.app;

import com.reactor.rust.bridge.HandlerRegistry;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.bridge.RouteScanner;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.core.RustCaches;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.config.RuntimeProfiles;
import com.reactor.sample.cache.reader.config.CacheReaderProperties;
import com.reactor.sample.cache.reader.handler.CustomerCacheHandler;
import com.reactor.sample.cache.reader.handler.HealthHandler;
import com.reactor.sample.cache.reader.service.CustomerCacheService;

public final class RestSampleCacheReaderApplication {

    private RestSampleCacheReaderApplication() {}

    public static void main(String[] args) {
        PropertiesLoader.load();
        RuntimeProfiles.apply();

        CacheReaderProperties properties = CacheReaderProperties.load();
        RustCache cache = RustCaches.create(properties.asProperties());
        CustomerCacheService customerCache = new CustomerCacheService(cache, properties);

        HandlerRegistry registry = HandlerRegistry.getInstance();
        registry.registerBean(new HealthHandler());
        registry.registerBean(new CustomerCacheHandler(customerCache));

        RouteScanner.scanAndRegister();
        NativeBridge.configureRuntimeFromProperties();

        int port = properties.getInt("server.port");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(cache), "cache-reader-shutdown"));
        NativeBridge.startHttpServer(port);

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown(RustCache cache) {
        try {
            NativeBridge.stopHttpServer();
        } catch (UnsatisfiedLinkError ignored) {
            // Native library may be unavailable during failed startup.
        } finally {
            cache.close();
        }
    }
}
