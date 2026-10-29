package com.reactor.sample.nativeview;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "reactor.test.native-view-http", matches = "true")
class NativeViewHttpTest {
    @TempDir Path directory;

    @Test void realRustRequestBuffersSupportUtf8ErrorsRetryConcurrencyAndNoCrossRequestRetention() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var log = directory.resolve("app.log");
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms8m", "-Xmx32m", "-Xss256k", "-Xshareclasses:none", "-Dserver.port=" + port,
                "-cp", System.getProperty("java.class.path"), NativeViewApplication.class.getName()));
        var child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            ByteBuffer record = AccountHandlerTest.record("\u0130pek \ud83d\ude80\n\"acc\"");
            byte[] body = new byte[record.capacity()]; record.get(0, body);
            HttpResponse<String> first = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline && child.isAlive()) {
                try { first = send(http, port, "view", body); break; }
                catch (java.io.IOException starting) { Thread.sleep(100); }
            }
            assertNotNull(first, () -> read(log));
            assertEquals(200, first.statusCode(), first.body());
            assertEquals("application/json; charset=utf-8", first.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(first.body().replace("\\u000a", "\\n"), send(http, port, "dto", body).body());
            for (String route : List.of("view", "dto")) {
                assertEquals(400, send(http, port, route, new byte[]{1}).statusCode());
                byte[] malformed = body.clone(); malformed[16] = (byte) 99;
                assertEquals(400, send(http, port, route, malformed).statusCode());
                malformed = body.clone(); malformed[33] = (byte) 0xff;
                assertEquals(400, send(http, port, route, malformed).statusCode());
            }
            // Large escaped code exceeds the initial native response capacity; retry must not leak a borrow.
            var large = AccountHandlerTest.record("\"".repeat(900));
            byte[] largeBytes = new byte[large.capacity()]; large.get(0, largeBytes);
            var largeView = send(http, port, "view", largeBytes);
            assertEquals(200, largeView.statusCode(), largeView.body());
            assertEquals(send(http, port, "dto", largeBytes).body(), largeView.body());
            for (int batch = 0; batch < 4; batch++) {
                var requests = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
                for (int i = 0; i < 64; i++) requests.add(http.sendAsync(request(port, "view", body), HttpResponse.BodyHandlers.ofString()));
                for (var pending : requests) {
                    var response = pending.get(10, TimeUnit.SECONDS);
                    assertEquals(200, response.statusCode(), response.body());
                    assertEquals(first.body(), response.body());
                }
            }
        } finally {
            child.destroy();
            if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); assertTrue(child.waitFor(5, TimeUnit.SECONDS)); }
            assertFalse(read(log).contains("UnsatisfiedLinkError"), () -> read(log));
        }
    }

    private static HttpRequest request(int port, String route, byte[] body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/accounts/" + route))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }
    private static HttpResponse<String> send(HttpClient client, int port, String route, byte[] body) throws Exception {
        return client.send(request(port, route, body), HttpResponse.BodyHandlers.ofString());
    }
    private static String read(Path log) {
        try { return Files.readString(log); } catch (Exception failure) { return failure.toString(); }
    }
}
