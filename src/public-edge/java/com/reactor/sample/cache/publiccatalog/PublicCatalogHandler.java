package com.reactor.sample.cache.publiccatalog;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.NativeStaticRoute;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RustEdgeCache;
import com.reactor.rust.annotations.RustEdgeRateLimit;
import com.reactor.rust.cache.http.CacheResponses;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

/** Published, anonymous product data only; private customer routes stay in the ordinary sample. */
@RestController("/api/v1/public/catalog")
public final class PublicCatalogHandler {
    private final PublicCatalogReader catalog;
    private final RawResponse rateProbe = RawResponse.registeredJson(
            "{\"status\":\"available\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    public PublicCatalogHandler(PublicCatalogReader catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products/{sku}")
    @RustEdgeCache(versionKey = "public.catalog.product:current",
            dataKeyPrefix = "public.catalog.product:v:{version}:idx:sku:",
            pathParam = "sku", maxAgeMs = 60_000)
    @RustEdgeRateLimit(permitsPerSecond = 100_000, burst = 1_000)
    public ResponseEntity<RawResponse> product(@PathVariable("sku") String sku) {
        return CacheResponses.problem(catalog.product(sku));
    }

    @GetMapping("/limited/products/{sku}")
    @RustEdgeRateLimit(permitsPerSecond = 2, burst = 2)
    public ResponseEntity<RawResponse> limitedProduct(@PathVariable("sku") String sku) {
        return CacheResponses.problem(catalog.product(sku));
    }

    /** Rate-only path: no cache/Redis or per-request Java work can hide limiter overhead. */
    @GetMapping("/rate-check")
    @NativeStaticRoute
    @RustEdgeRateLimit(permitsPerSecond = 1_000_000, burst = 1_000_000)
    public RawResponse rateCheck() {
        return rateProbe;
    }
}
