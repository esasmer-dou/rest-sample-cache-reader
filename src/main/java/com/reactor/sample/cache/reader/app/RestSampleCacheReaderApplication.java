package com.reactor.sample.cache.reader.app;

import com.reactor.rust.app.RestApplication;

public final class RestSampleCacheReaderApplication {

    private RestSampleCacheReaderApplication() {}

    public static void main(String[] args) {
        RestApplication.run(CacheReaderModule.INSTANCE);
    }
}
