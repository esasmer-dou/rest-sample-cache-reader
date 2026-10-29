package com.reactor.sample.cache.reader.config;

import com.reactor.rust.cache.config.CacheProperties;
import com.reactor.rust.cache.config.RustCacheConfig;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.core.RustCaches;
import com.reactor.sample.cache.reader.service.CustomerCacheReader;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "reactor.cache.redis.integration", matches = "true")
class GeneratedL1ReaderLiveTest {
    @Test
    void generatedCustomerFlowPreservesNestedUtf8JsonDefaultsMissingDataAndVersionRotation() {
        String ns = "sample-l1-it-" + UUID.randomUUID();
        var properties = properties(ns, true);
        byte[] body = "{\"customer\":{\"id\":1,\"name\":\"\u0130pek \u015eahin\",\"address\":{\"city\":\"\u0130stanbul\"}}}"
                .getBytes(StandardCharsets.UTF_8);
        try (var cache = cache(); var reader = CustomerCacheReader.create(cache, CacheProperties.from(properties))) {
            assertTrue(reader.customer(1).isCacheNotReady());
            cache.setString(ns + ".detail:current", "1|1000-1", 60000);
            cache.setBytes(ns + ".detail:v:1000-1:id:1", body, 60000);
            cache.setString(ns + ".segment:current", "1|1000-1", 60000);
            cache.setBytes(ns + ".segment:v:1000-1:idx:segment:standard", body, 60000);
            assertArrayEquals(body, reader.customer(1).bytes());
            byte[] changed = reader.customer(1).bytes(); changed[0] = 0;
            assertArrayEquals(body, reader.customer(1).bytes());
            assertArrayEquals(body, reader.customersBySegment("  ").bytes());
            assertArrayEquals(body, reader.customersBySegment(null).bytes());
            assertTrue(reader.customer(999).isMiss());
            assertTrue(reader.customersByStatus("active").isCacheNotReady());
            assertTrue(reader.metricsJson().contains("\"enabled_projections\":2"));
            assertTrue(reader.metricsJson().contains("\"hits\":3"));
            try (var snapshot = reader.customerSnapshot(1); var segment = reader.segmentSnapshot(null)) {
                var out = ByteBuffer.allocateDirect(body.length);
                assertEquals(body.length, snapshot.writeTo(out, 0));
                byte[] actual = new byte[body.length]; out.get(0, actual);
                assertArrayEquals(body, actual);
                assertEquals(body.length, segment.writeTo(out, 0));
            }
            byte[] next = "{\"customer\":{\"id\":1,\"name\":\"Yeni\"}}".getBytes(StandardCharsets.UTF_8);
            cache.setBytes(ns + ".detail:v:2000-2:id:1", next, 60000);
            cache.setString(ns + ".detail:current", "2|2000-2", 60000);
            assertArrayEquals(next, reader.customer(1).bytes());
            reader.close();
            assertThrows(IllegalStateException.class, () -> reader.customer(1));
            assertEquals("2|2000-2", cache.getString(ns + ".detail:current"));
        }
    }

    @Test
    void theSameGeneratedContractCanRunRedisOnlyWithoutAllocatingAnL1Slab() {
        String ns = "sample-l1-off-it-" + UUID.randomUUID();
        try (var cache = cache(); var reader = CustomerCacheReader.create(cache, CacheProperties.from(properties(ns, false)))) {
            assertTrue(reader.metricsJson().contains("\"enabled_projections\":0"));
            assertTrue(reader.metricsJson().contains("\"slab_bytes\":0"));
            cache.setString(ns + ".detail:current", "1000-1", 60000);
            cache.setBytes(ns + ".detail:v:1000-1:id:1", new byte[]{1}, 60000);
            assertArrayEquals(new byte[]{1}, reader.customer(1).bytes());
            cache.setBytes(ns + ".detail:v:1000-1:id:1", new byte[]{2}, 60000);
            assertArrayEquals(new byte[]{2}, reader.customer(1).bytes());
        }
    }

    private static RustCache cache() {
        return RustCaches.create(RustCacheConfig.builder()
                .host(System.getProperty("reactor.cache.redis.integration.host", "127.0.0.1"))
                .port(Integer.getInteger("reactor.cache.redis.integration.port", 6379)).build());
    }

    private static Properties properties(String namespace, boolean enabled) {
        var values = new Properties();
        values.setProperty("sample.cache.customer.namespace", namespace);
        values.setProperty("sample.cache.customer.projections", "detail,segment,status,campaign,meta");
        values.setProperty("sample.cache.customer.version-cache-ms", "0");
        if (enabled) {
            for (String projection : new String[]{"detail", "segment"}) {
                String prefix = "sample.cache.customer." + projection + ".l1.";
                values.setProperty(prefix + "max-entries", "4");
                values.setProperty(prefix + "max-entry-bytes", "128");
                values.setProperty(prefix + "max-key-chars", "32");
                values.setProperty(prefix + "max-bytes", "16384");
            }
        }
        return values;
    }
}
