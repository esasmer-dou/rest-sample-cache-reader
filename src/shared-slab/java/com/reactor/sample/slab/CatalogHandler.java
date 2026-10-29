package com.reactor.sample.slab;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.cache.http.CacheResponses;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;

@RestController("/api/v1/catalog")
public final class CatalogHandler {
    private static final RawResponse UP = RawResponse.text("{\"status\":\"ok\"}", "application/json");
    private final CatalogReader reader;
    private final String small = PropertiesLoader.require("sample.slab.key.small");
    private final String heavy = PropertiesLoader.require("sample.slab.key.heavy");
    public CatalogHandler(CatalogReader reader) { this.reader = reader; }
    @GetMapping("/health") public RawResponse health() { return UP; }
    @GetMapping("/small") public ResponseEntity<?> small() { return CacheResponses.ownedProblem(reader.read(small)); }
    @GetMapping("/heavy") public ResponseEntity<?> heavy() { return CacheResponses.ownedProblem(reader.read(heavy)); }
}
