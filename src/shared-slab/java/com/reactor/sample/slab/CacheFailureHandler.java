package com.reactor.sample.slab;

import com.reactor.rust.cache.core.RedisCacheException;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.exception.ExceptionHandler;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.rust.ffm.cache.FfmReadException;

@Component
public final class CacheFailureHandler {
    @ExceptionHandler({RedisCacheException.class, FfmReadException.class})
    public ResponseEntity<RawResponse> unavailable(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE,
                RawResponse.text("{\"code\":\"cache_unavailable\"}", "application/json"));
    }
}
