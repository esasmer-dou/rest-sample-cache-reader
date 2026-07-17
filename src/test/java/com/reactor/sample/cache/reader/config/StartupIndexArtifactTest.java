package com.reactor.sample.cache.reader.config;

import com.reactor.rust.startup.StartupIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupIndexArtifactTest {

    @Test
    void buildGeneratesRoutesForExplicitHandlerInstances() {
        StartupIndex.IndexResult routes = StartupIndex.routeKeys();
        assertTrue(routes.present(), "Build-time route index must be present");
        assertTrue(routes.entries().contains("GET /app/health"));
        assertTrue(routes.entries().contains("GET /api/v1/cache/customers/{id}"));
        assertTrue(routes.entries().contains("GET /api/v1/cache/customers/segments/{segment}"));
    }
}
