package com.reactor.sample.cache.reader.app;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;
import com.reactor.rust.cache.integration.EnableRustCache;

@EnableRustCache
@ReactorApplication(
        name = "Cache Reader Sample",
        version = "0.6.0",
        description = "Reads precomputed customer projections from Redis",
        scanBasePackages = "com.reactor.sample.cache.reader")
public final class RestSampleCacheReaderApplication {

    private RestSampleCacheReaderApplication() {}

    public static void main(String[] args) {
        RestApplication.run(RestSampleCacheReaderApplication.class, args);
    }
}
