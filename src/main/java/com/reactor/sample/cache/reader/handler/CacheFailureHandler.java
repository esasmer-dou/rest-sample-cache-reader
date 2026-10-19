package com.reactor.sample.cache.reader.handler;

import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.cache.core.RedisCacheException;
import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.exception.ExceptionHandler;
import com.reactor.rust.exception.HttpErrorMapper;
import com.reactor.rust.http.ErrorResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

@Component
@RequiresProperty(name = "sample.errors.common-contract", value = "true")
public final class CacheFailureHandler {
    private static final HttpErrorMapper.MappedError UNAVAILABLE =
            new HttpErrorMapper.MappedError(503, "cache_unavailable", "Cache is temporarily unavailable");

    @ExceptionHandler(RedisCacheException.class)
    public ResponseEntity<RawResponse> unavailable() {
        return ErrorResponses.from(UNAVAILABLE);
    }
}
