package com.reactor.sample.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Keys and token fixtures exist only in test output, never in the production sample JAR. */
public final class JwtHttpTest {
    @TempDir Path output;
    static final String[] ALGORITHMS = { "RS256", "ES256", "EdDSA" };

    public static void main(String[] args) throws Exception { fixtures(Path.of(args[0])); }

    static Map<String, KeyPair> fixtures(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("keys"));
        var pairs = new HashMap<String, KeyPair>();
        for (String algorithm : ALGORITHMS) {
            String factory = switch (algorithm) { case "RS256" -> "RSA"; case "ES256" -> "EC"; default -> "Ed25519"; };
            var generator = KeyPairGenerator.getInstance(factory);
            if (algorithm.equals("RS256")) generator.initialize(2048);
            if (algorithm.equals("ES256")) generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();
            pairs.put(algorithm, pair);
            Files.write(directory.resolve("keys/" + algorithm + ".der"), pair.getPublic().getEncoded());
            long now = System.currentTimeMillis() / 1000;
            Files.writeString(directory.resolve(algorithm + ".token"), token(algorithm, pair, now + 7200, 0));
            Files.writeString(directory.resolve(algorithm + ".expired"), token(algorithm, pair, now - 10, 0));
            if (algorithm.equals("EdDSA")) {
                var tokens = new ArrayList<String>();
                for (int i = 0; i < 512; i++) tokens.add(token(algorithm, pair, now + 7200, i + 1));
                Files.write(directory.resolve("churn.tokens"), tokens);
            }
        }
        return pairs;
    }

    static String token(String algorithm, KeyPair pair, long expiry, int id) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(("{\"alg\":\"" + algorithm + "\",\"kid\":\"" + algorithm + "\"}").getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"sample-user\",\"iss\":\"sample-issuer\",\"aud\":\"sample-api\",\"roles\":[\"reader\"],\"exp\":" + expiry + ",\"jti\":" + id + "}").getBytes(StandardCharsets.UTF_8));
        var signer = Signature.getInstance(switch (algorithm) { case "RS256" -> "SHA256withRSA"; case "ES256" -> "SHA256withECDSAinP1363Format"; default -> "Ed25519"; });
        signer.initSign(pair.getPrivate());
        String message = header + "." + payload;
        signer.update(message.getBytes(StandardCharsets.US_ASCII));
        return message + "." + encoder.encodeToString(signer.sign());
    }

    @Test void realHttpReusesOnlyVerifiedClaimsAndStillRejectsErrorsAndExpiredHits() throws Exception {
        var pairs = fixtures(output);
        for (boolean cached : new boolean[] {false, true}) {
            Path log = output.resolve("app-" + cached + ".log");
            var command = List.of(System.getProperty("java.home") + "/bin/java", "-Xms8m", "-Xmx48m", "-Xshareclasses:none",
                    "-Drust.lib.path=" + System.getProperty("rust.lib.path"), "-Dserver.port=8096",
                    "-Dsample.jwt.cache.enabled=" + cached, "-Dsample.jwt.cache.slots=8", "-Dsample.jwt.cache.max-bytes=8192",
                    "-Dsample.jwt.public-key-directory=" + output.resolve("keys"), "-Dsample.diagnostics.enabled=true",
                    "-Dreactor.rust.jni.queue-capacity=512", "-cp", System.getProperty("java.class.path"), JwtApplication.class.getName());
            var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            Process process = builder.start();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                boolean ready = false;
                for (int i = 0; i < 100; i++) {
                    assertTrue(process.isAlive(), () -> readLog(log));
                    try { if (get(client, "/health", null).statusCode() == 200) { ready = true; break; } }
                    catch (java.io.IOException notReady) { /* startup only */ }
                    Thread.sleep(100);
                }
                assertTrue(ready, () -> readLog(log));
                for (String algorithm : ALGORITHMS) {
                    String signed = Files.readString(output.resolve(algorithm + ".token"));
                    long before = checks(client);
                    for (int i = 0; i < 100; i++) {
                        var response = get(client, "/me", signed);
                        assertEquals(200, response.statusCode(), response.body());
                        assertEquals("{\"status\":\"ok\"}", response.body());
                    }
                    assertEquals(cached ? 1 : 100, checks(client) - before);
                    assertEquals(403, get(client, "/admin", signed).statusCode());
                    assertEquals(401, get(client, "/me", Files.readString(output.resolve(algorithm + ".expired"))).statusCode());
                    int dot = signed.lastIndexOf('.');
                    String bad = signed.substring(0, dot + 1) + (signed.charAt(dot + 1) == 'A' ? "B" : "A") + signed.substring(dot + 2);
                    assertEquals(401, get(client, "/me", bad).statusCode());
                    assertEquals(200, get(client, "/me", signed).statusCode());
                }
                assertEquals(401, get(client, "/me", null).statusCode());
                assertEquals(401, get(client, "/me", "eyJhbGciOiJub25lIn0.e30.").statusCode());
                // A live cached token must expire without a new signature verification.
                if (cached) {
                    String expiring = token("EdDSA", pairs.get("EdDSA"), System.currentTimeMillis() / 1000 + 2, 999);
                    assertEquals(200, get(client, "/me", expiring).statusCode());
                    long verified = checks(client);
                    Thread.sleep(2100);
                    assertEquals(401, get(client, "/me", expiring).statusCode());
                    assertEquals(verified, checks(client), "Cached expiry must fail before a second signature check");
                }
                String metrics = get(client, "/metrics", null).body();
                assertTrue(metrics.contains("sample_jwt_cache_capacity " + (cached ? 8 : 0)));
            } finally {
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Child server did not stop"); }
                System.out.println("JWT HTTP cached=" + cached + " lifecycle stopped; " + readLog(log).lines().count() + " log lines");
            }
        }
    }

    static HttpResponse<String> get(HttpClient client, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8096/api/v1/jwt" + path)).timeout(Duration.ofSeconds(4));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    static long checks(HttpClient client) throws Exception {
        return get(client, "/metrics", null).body().lines().filter(s -> s.startsWith("sample_jwt_signature_checks_total "))
                .mapToLong(s -> Long.parseLong(s.substring(s.indexOf(' ') + 1))).findFirst().orElseThrow();
    }
    static String readLog(Path path) { try { return Files.readString(path); } catch (Exception failure) { return failure.toString(); } }
}
