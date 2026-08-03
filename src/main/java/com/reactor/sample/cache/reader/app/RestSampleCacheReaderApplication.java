package com.reactor.sample.cache.reader.app;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;
import com.reactor.rust.cache.integration.EnableRustCache;

@EnableRustCache
@ReactorApplication(scanBasePackages = "com.reactor.sample.cache.reader")
public final class RestSampleCacheReaderApplication {

    private RestSampleCacheReaderApplication() {}

    public static void main(String[] args) {
        RestApplication.run(RestSampleCacheReaderApplication.class, args);
    }
}
