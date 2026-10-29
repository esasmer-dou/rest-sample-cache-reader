package com.reactor.sample.cache.publiccatalog;

import com.reactor.rust.cache.config.RustCacheConfig;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.core.RustCaches;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real generated sample application, real Redis and real Rust transport; opt-in remote gate. */
@EnabledIfSystemProperty(named = "reactor.test.public-edge", matches = "true")
class PublicCatalogHttpTest {
    private static final String BODY = "{\"sku\":\"TR-01\",\"name\":\"\u0130stanbul \u00c7ay\","
            + "\"details\":{\"description\":\"\u0130\u0131\u015e\u015f\u011e\u011f\u00dc\u00fc\u00d6\u00f6\u00c7\u00e7 \ud83d\ude80\",\"stock\":7}}";
    @TempDir Path directory;

    @Test void generatedApplicationPreservesPayloadFallbackRolloverAndOptionalRateLimits() throws Exception {
        try (RustCache cache = RustCaches.create(RustCacheConfig.builder()
                .host(System.getProperty("reactor.test.redis.host", "127.0.0.1"))
                .port(Integer.getInteger("reactor.test.redis.port", 6379))
                .readConnections(1).writeConnections(1).maxResponseBytes(16384).build());
             HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            for (boolean enabled : new boolean[]{false, true}) {
                String version = System.currentTimeMillis() + "-sample";
                publish(cache, version, BODY);
                int port;
                try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
                Path log = directory.resolve("sample-" + enabled + ".log");
                var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Xms8m", "-Xmx32m", "-Xss256k", "-Xshareclasses:none",
                        "-Dserver.port=" + port, "-Dsample.public.edge.enabled=" + enabled,
                        "-Dreactor.cache.redis.host=" + cache.config().host(),
                        "-Dreactor.cache.redis.port=" + cache.config().port(),
                        "-cp", System.getProperty("java.class.path"), PublicCatalogApplication.class.getName()));
                Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    HttpResponse<String> response = null;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while (System.nanoTime() < deadline && child.isAlive()) {
                        try { response = get(http, port, "/products/TR-01", false); break; }
                        catch (java.io.IOException notReady) { Thread.sleep(100); }
                    }
                    assertNotNull(response, () -> read(log));
                    for (int i = 0; i < 20; i++) {
                        response = get(http, port, "/products/TR-01", false);
                        assertEquals(200, response.statusCode(), response.body());
                        assertEquals(BODY, response.body());
                        assertEquals("application/json; charset=utf-8",
                                response.headers().firstValue("Content-Type").orElseThrow());
                    }
                    assertEquals(BODY, get(http, port, "/products/TR-01?locale=tr", false).body());
                    assertEquals(BODY, get(http, port, "/products/TR-01", true).body());
                    assertEquals(BODY, get(http, port, "/products/TR-%30%31", false).body());
                    assertEquals(BODY, get(http, port, "/products//TR-01", false).body(),
                            "Deferred fallback must preserve the router's segment normalization");
                    assertEquals(404, get(http, port, "/products/missing", false).statusCode());
                    String large = "{\"description\":\"" + "x".repeat(9000) + "\"}";
                    assertTrue(cache.setString("public.catalog.product:v:" + version + ":idx:sku:BIG", large, 60000));
                    assertEquals(large, get(http, port, "/products/BIG", false).body(),
                            "An oversized native entry must fall back without truncating the HTTP body");
                    String unicode = "{\"sku\":\"\u0130\u00c7\"}";
                    assertTrue(cache.setString("public.catalog.product:v:" + version + ":idx:sku:%C4%B0%C3%87", unicode, 60000));
                    assertEquals(unicode, get(http, port, "/products/%C4%B0%C3%87", false).body());
                    int limited = 0;
                    for (int i = 0; i < 10; i++) {
                        var result = get(http, port, "/limited/products/TR-01", false);
                        if (result.statusCode() == 429) {
                            limited++;
                            assertTrue(result.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
                        } else { assertEquals(200, result.statusCode(), result.body()); }
                    }
                    assertEquals(enabled, limited > 0, "Rate limiting must be active only with explicit opt-in");
                    var probe = get(http, port, "/rate-check", false);
                    assertEquals(200, probe.statusCode());
                    assertEquals("{\"status\":\"available\"}", probe.body());
                    String updated = BODY.replace("\"stock\":7", "\"stock\":6");
                    publish(cache, System.currentTimeMillis() + "-next", updated);
                    Thread.sleep(1150);
                    assertEquals(updated, get(http, port, "/products/TR-01", false).body());
                    publish(cache, "1-too-old", BODY);
                    Thread.sleep(1150);
                    assertEquals(503, get(http, port, "/products/TR-01", false).statusCode(),
                            "Both native and Java paths must reject stale snapshots");
                } finally {
                    child.destroy();
                    if (!child.waitFor(5, TimeUnit.SECONDS)) {
                        child.destroyForcibly();
                        assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Child process did not stop");
                    }
                    assertFalse(read(log).contains("UnsatisfiedLinkError"), () -> read(log));
                }
            }
        }
    }

    private static void publish(RustCache cache, String version, String json) {
        assertTrue(cache.setString("public.catalog.product:v:" + version + ":idx:sku:TR-01", json, 60000));
        assertTrue(cache.setString("public.catalog.product:current", "1|" + version, 60000));
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path, boolean credentials) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/public/catalog" + path))
                .timeout(Duration.ofSeconds(5)).GET();
        if (credentials) request.header("Authorization", "Bearer intentionally-not-cached");
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String read(Path log) {
        try { return Files.readString(log); } catch (java.io.IOException failure) { return failure.toString(); }
    }
}
