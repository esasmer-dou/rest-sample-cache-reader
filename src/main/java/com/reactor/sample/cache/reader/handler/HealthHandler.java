package com.reactor.sample.cache.reader.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

public final class HealthHandler {

    @GetMapping(value = "/app/health", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> health() {
        return ResponseEntity.ok(RawResponse.text(
                "{\"status\":\"UP\",\"app\":\"rest-sample-cache-reader\"}",
                "application/json; charset=utf-8"));
    }
}
