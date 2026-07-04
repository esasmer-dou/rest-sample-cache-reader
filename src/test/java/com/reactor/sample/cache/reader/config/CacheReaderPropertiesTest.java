package com.reactor.sample.cache.reader.config;

import com.reactor.rust.cache.projection.CacheReaderProjectionSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheReaderPropertiesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearOverlayProperty() {
        System.clearProperty("reactor.config.file");
    }

    @Test
    void loadsClasspathDefaults() {
        CacheReaderProperties properties = CacheReaderProperties.load();

        assertEquals("crm.customer", properties.get("sample.cache.customer.namespace"));
        assertEquals("detail,segment,status,campaign,meta", properties.get("sample.cache.customer.projections"));
        assertEquals(8080, properties.getInt("server.port"));
    }

    @Test
    void derivesProjectionNamespacesFromMinimumDefaults() {
        List<CacheReaderProjectionSettings> settings =
                CacheReaderProjectionSettings.resolveAll(CacheReaderProperties.load(), "sample.cache.customer");

        assertEquals("crm.customer.detail", settings.get(0).namespace());
        assertEquals("crm.customer.campaign", settings.get(3).namespace());
    }

    @Test
    void configuredOverlayOverridesMinimumDefaults() throws Exception {
        Path overlay = tempDir.resolve("production.properties");
        Files.writeString(overlay, String.join(System.lineSeparator(),
                "server.port=8090",
                "sample.cache.customer.namespace=crm.customer.prod",
                "sample.cache.customer.version-cache-ms=1000"));
        System.setProperty("reactor.config.file", overlay.toString());

        CacheReaderProperties properties = CacheReaderProperties.load();
        List<CacheReaderProjectionSettings> settings =
                CacheReaderProjectionSettings.resolveAll(properties, "sample.cache.customer");

        assertEquals(8090, properties.getInt("server.port"));
        assertEquals("crm.customer.prod", properties.get("sample.cache.customer.namespace"));
        assertEquals(1000L, properties.getLong("sample.cache.customer.version-cache-ms"));
        assertEquals("crm.customer.prod.detail", settings.get(0).namespace());
    }
}
