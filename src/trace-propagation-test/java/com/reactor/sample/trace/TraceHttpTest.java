package com.reactor.sample.trace;

import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TraceHttpTest {
    @Test
    void incomingParentSurvivesNativeRetriesAndAsyncCompletionWithoutLeakingIntoNextRequest() throws Exception {
        int upstreamPort = port(), apiPort = port();
        while (apiPort == upstreamPort) apiPort = port();
        Process backend = start(upstreamPort, upstreamPort, true);
        Process api = null;
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            ready(http, upstreamPort, backend);
            api = start(apiPort, upstreamPort, false);
            ready(http, apiPort, api);
            String trace = "0123456789abcdef0123456789abcdef", parent = "fedcba9876543210";
            var response = get(http, apiPort, "/api/v1/orders/retry", "fe-" + trace + '-' + parent + "-01-opaque");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("{\"stock\":42}", response.body());
            String span = get(http, apiPort, "/internal/trace/last", null).body();
            assertTrue(span.contains("\"traceId\":\"" + trace + "\""), span);
            assertTrue(span.contains("\"parentSpanId\":\"" + parent + "\""), span);
            assertTrue(span.contains("\"success\":true"), span);
            assertFalse(span.contains("\"spanId\":\"" + parent + "\""), span);
            String metrics = get(http, apiPort, "/internal/trace/metrics", null).body();
            assertTrue(metrics.contains("sample_trace_http_attempts 3"), metrics);
            assertTrue(metrics.contains("sample_trace_http_retries 2"), metrics);
            assertEquals(200, get(http, apiPort, "/api/v1/orders/retry", null).statusCode(),
                    "Later logical requests must not be compared against the first request's parent");

            assertEquals(200, get(http, apiPort, "/api/v1/orders/small", null).statusCode());
            span = get(http, apiPort, "/internal/trace/last", null).body();
            assertTrue(span.contains("\"parentSpanId\":\"null\""), span);
            assertFalse(span.contains(trace), span);
            assertEquals(200, get(http, apiPort, "/api/v1/orders/small",
                    "00-" + trace + '-' + parent + "-00").statusCode());
            assertEquals(span, get(http, apiPort, "/internal/trace/last", null).body(),
                    "Unsampled request must not export over the previous completed span");
        } finally {
            stop(api); stop(backend);
        }
    }

    private static Process start(int port, int upstream, boolean backend) throws Exception {
        var args = new ArrayList<String>();
        args.add(ProcessHandle.current().info().command().orElseThrow());
        args.addAll(java.util.List.of("-Xms8m", "-Xmx48m", "-Xss256k", "-Xquickstart", "-Xshareclasses:none",
                "-XX:ActiveProcessorCount=1", "-Xgc:threads=1", "-Drust.lib.path=" + System.getProperty("rust.lib.path"),
                "-Dserver.port=" + port, "-Dsample.inventory.base-url=http://127.0.0.1:" + upstream,
                "-Dsample.inventory.enabled=" + backend, "-Dsample.api.enabled=" + !backend,
                "-Dsample.diagnostics.enabled=true", "-Dreactor.metrics.collection-enabled=true",
                "-Dreactor.tracing.sample-ratio=1.0", "-cp",
                System.getProperty("java.class.path"), TraceApplication.class.getName()));
        var log = Files.createTempFile("trace-http-", ".log");
        return new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static void ready(HttpClient http, int port, Process child) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            assertTrue(child.isAlive(), "Application exited before readiness");
            try { if (get(http, port, "/app/health", null).statusCode() == 200) return; }
            catch (java.io.IOException ignored) { }
            Thread.sleep(100);
        }
        fail("Application did not become ready");
    }
    private static HttpResponse<String> get(HttpClient http, int port, String path, String trace) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5));
        if (trace != null) request.header("traceparent", trace);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static int port() throws Exception { try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    private static void stop(Process child) throws Exception {
        if (child == null) return;
        child.destroy();
        if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
    }
}
