package com.reactor.sample.trace;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.http.RawResponse;

@RestController("/app")
public final class HealthHandler {
    private static final RawResponse UP = RawResponse.text("{\"status\":\"ok\"}", "application/json");
    @GetMapping("/health") public RawResponse health() { return UP; }
}
