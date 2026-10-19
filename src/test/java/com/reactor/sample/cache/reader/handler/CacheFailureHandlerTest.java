package com.reactor.sample.cache.reader.handler;

import org.junit.jupiter.api.Test;
import com.reactor.rust.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheFailureHandlerTest {
    @Test
    void dependencyFailureHasUnavailableStatus() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, new CacheFailureHandler().unavailable().getStatus());
    }
}
