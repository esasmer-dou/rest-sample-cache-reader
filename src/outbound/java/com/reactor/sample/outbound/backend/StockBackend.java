package com.reactor.sample.outbound.backend;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.NativeStaticRoute;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.http.RawResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Deterministic standalone upstream fixture. No external database or Redis. */
@RestController("/stock")
@RequiresProperty(name = "sample.backend.enabled", value = "true")
public final class StockBackend {
    private final RawResponse small = RawResponse.registeredJson("{\"stock\":42}".getBytes(StandardCharsets.UTF_8));
    private final RawResponse heavy = RawResponse.registeredJson(("{\"description\":\"" + "inventory-".repeat(400) + "\",\"stock\":42}").getBytes(StandardCharsets.UTF_8));
    private final RawResponse error = RawResponse.registered("{\"error\":\"upstream_unavailable\"}".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json; charset=utf-8"), 500);
    @NativeStaticRoute @GetMapping("/small") public RawResponse small() { return small; }
    @NativeStaticRoute @GetMapping("/heavy") public RawResponse heavy() { return heavy; }
    @NativeStaticRoute @GetMapping("/error") public RawResponse error() { return error; }
}
