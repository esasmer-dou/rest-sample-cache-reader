package com.reactor.sample.outbound;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "reactor.test.outbound-http", matches = "true")
class OutboundLiveTest {
    static final String SECRET = "test-only-012345678901234567890123456789";
    @TempDir Path directory;

    @Test void nativeRejectsBeforeJniButJavaStillOwnsValidIdentitiesAndRoles() throws Exception {
        int port = port();
        var log = directory.resolve("auth.log");
        var app = start(log, "com.reactor.sample.outbound.OutboundApplication", port, "-Dsample.edge.jwt.enabled=true", "-Dsample.outbound.enabled=false");
        try (var http = HttpClient.newHttpClient()) {
            ready(http, app, log, port, "/api/v1/account/health");
            for (String route : List.of("path", "async-path")) {
                for (String values : List.of("alpha/beta", "%C4%B0zmir/%C3%87a%C4%9Fr%C4%B1")) {
                    var response = get(http, port, "/api/v1/account/" + route + "/" + values, null);
                    assertEquals(200, response.statusCode(), response.body());
                    assertEquals(values.startsWith("alpha") ? "alpha|beta" : "\u0130zmir|\u00c7a\u011fr\u0131", response.body());
                }
            }
            long before = callbacks(http, port);
            for (int i = 0; i < 100; i++) {
                var response = get(http, port, "/api/v1/account/me", "Bearer invalid");
                assertEquals(401, response.statusCode(), response.body());
                assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
                assertTrue(response.body().contains("\"status\":401"));
            }
            assertEquals(1, callbacks(http, port) - before, "Only the metrics request may enter JNI");
            long now = java.time.Instant.now().getEpochSecond();
            String valid = token("HS256", "{\"sub\":\"\u0130pek\",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\",\"exp\":" + (now+3600) + ",\"roles\":[\"user\"]}");
            assertEquals(200, get(http, port, "/api/v1/account/me", "Bearer " + valid).statusCode());
            assertEquals(403, get(http, port, "/api/v1/account/admin", "Bearer " + valid).statusCode(), "Roles remain enforced by Java");
            assertEquals(200, get(http, port, "/api/v1/account/async-me", "Bearer " + valid).statusCode());
            assertEquals(3, metric(http, port, "reactor_native_edge_jwt_proof_accepted_total"), "Both sync and async guards must consume a scoped Rust proof");
            assertEquals(0, metric(http, port, "reactor_native_edge_jwt_proof_rejected_total"));
            for (String claims : List.of(
                    "{\"sub\":\"a\",\"exp\":"+(now-1)+",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\"}",
                    "{\"sub\":\"a\",\"nbf\":"+(now+3600)+",\"exp\":"+(now+7200)+",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\"}",
                    "{\"sub\":\"a\",\"exp\":"+(now+3600)+",\"iss\":\"wrong\",\"aud\":\"stock-api\"}")) {
                assertEquals(401, get(http, port, "/api/v1/account/me", "Bearer " + token("HS256", claims)).statusCode());
            }
            var duplicate = HttpRequest.newBuilder(uri(port,"/api/v1/account/me")).header("Authorization", "Bearer "+valid)
                    .header("Authorization", "Bearer "+valid).GET().build();
            assertEquals(401, http.send(duplicate, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, get(http, port, "/api/v1/account/me", null).statusCode());
            var spoof = HttpRequest.newBuilder(uri(port, "/api/v1/account/me"))
                    .header("Authorization", "Bearer invalid").header("X-Reactor-Verified-Jwt", "true").GET().build();
            assertEquals(401, http.send(spoof, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, get(http, port, "/api/v1/account/me", "Bearer " + valid).statusCode());
        } finally { stop(app); }
    }

    @Test void startupRejectsPolicyDriftAndAValidTokenWithoutANativeRequestProof() throws Exception {
        for (String change : List.of("none", "hmac-secret", "issuer", "audience", "clock-skew-seconds", "max-token-chars", "require-expiration")) {
            Path log = directory.resolve("policy-" + change + ".log");
            Process probe = start(log, JwtStartupProbe.class.getName(), port(), "-Dtest.edge.policy-drift=" + change);
            try {
                assertTrue(probe.waitFor(20, TimeUnit.SECONDS), "Startup probe stalled");
                assertEquals(0, probe.exitValue(), Files.readString(log));
                assertTrue(Files.readString(log).contains(change.equals("none") ? "PROOF_REQUIRED" : "POLICY_DRIFT_REJECTED"), Files.readString(log));
            } finally { if (probe.isAlive()) stop(probe); }
        }
    }

    @Test void expiryAfterRustVerificationIsStillRejectedByJavaAtTheGuardBoundary() throws Exception {
        int port = port();
        Path log = directory.resolve("queue-expiry.log");
        Process app = start(log, OutboundApplication.class.getName(), port, "-Dsample.edge.jwt.enabled=true",
                "-Dsample.outbound.enabled=false", "-Dtest.edge.expire-in-queue=true");
        try (var http = HttpClient.newHttpClient()) {
            ready(http, app, log, port, "/api/v1/account/health");
            long expiry = java.time.Instant.now().getEpochSecond() + 2;
            String token = token("HS256", "{\"sub\":\"user\",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\",\"exp\":" + expiry + "}");
            assertEquals(401, get(http, port, "/api/v1/account/me", "Bearer " + token).statusCode());
            assertEquals(1, metric(http, port, "reactor_native_edge_jwt_proof_accepted_total"), "Rust accepted first; Java must reject expiry after the queue delay");
        } finally { stop(app); }
    }

    @Test void generatedNativeClientPreservesBodiesAndOpensCircuitOnFinalFailures() throws Exception {
        int backendPort=port(), appPort=port();
        var backendLog=directory.resolve("backend.log");
        var appLog=directory.resolve("outbound.log");
        var backend=start(backendLog, "com.reactor.sample.outbound.OutboundApplication", backendPort,
                "-Dreactor.security.enabled=false", "-Dsample.backend.enabled=true", "-Dsample.api.enabled=false", "-Dsample.outbound.enabled=false");
        Process app=null;
        try (var http=HttpClient.newHttpClient()) {
            ready(http,backend,backendLog,backendPort,"/stock/small");
            app=start(appLog,"com.reactor.sample.outbound.OutboundApplication",appPort,"-Dsample.stock.base-url=http://127.0.0.1:"+backendPort);
            ready(http,app,appLog,appPort,"/api/v1/account/health");
            for(String kind:List.of("small","heavy")) {
                String expected=get(http,backendPort,"/stock/"+kind,null).body();
                for(int i=0;i<20;i++) {
                    var response=get(http,appPort,"/api/v1/stock/"+kind,null);
                    assertEquals(200,response.statusCode(),response.body()); assertEquals(expected,response.body());
                }
            }
            var first=get(http,appPort,"/api/v1/stock/error",null);
            assertEquals(500,first.statusCode(),first.body());
            // Second call is rejected by the generated method breaker, not by the dependency.
            var second=get(http,appPort,"/api/v1/stock/error",null);
            assertEquals(503,second.statusCode(),second.body());
            assertEquals(200,get(http,appPort,"/api/v1/stock/small",null).statusCode(),"Other method remains independent");
        } finally { if(app!=null)stop(app);stop(backend); }
    }

    private Process start(Path log,String main,int port,String... extra) throws Exception {
        var args=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xms8m","-Xmx32m","-Xss256k",
                "-Xshareclasses:none","-XX:ActiveProcessorCount=1","-Xgc:threads=1","-Dserver.port="+port,
                "-Drust.lib.path="+System.getProperty("rust.lib.path"),
                "-Dreactor.security.jwt.hmac-secret="+SECRET,"-Dsample.diagnostics.enabled=true","-Dreactor.metrics.enabled=true"));
        args.addAll(List.of(extra)); args.addAll(List.of("-cp",System.getProperty("java.class.path"),main));
        return new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static void ready(HttpClient http,Process process,Path log,int port,String path) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(process.isAlive()&&System.nanoTime()<deadline) {
            try { if(get(http,port,path,null).statusCode()==200)return; } catch(java.io.IOException starting) { }
            Thread.sleep(100);
        }
        fail("Application not ready: "+Files.readString(log));
    }
    private static HttpResponse<String> get(HttpClient http,int port,String path,String authorization) throws Exception {
        var request=HttpRequest.newBuilder(uri(port,path)).timeout(Duration.ofSeconds(5));
        if(authorization!=null)request.header("Authorization",authorization);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private static long callbacks(HttpClient http,int port) throws Exception {
        return metric(http, port, "reactor_native_jni_requests_total");
    }
    private static long metric(HttpClient http,int port,String name) throws Exception {
        String metrics=get(http,port,"/api/v1/account/metrics",null).body();
        for(String line:metrics.split("\n"))if(line.startsWith(name+" "))return Long.parseLong(line.substring(line.indexOf(' ')+1).trim());
        throw new AssertionError(metrics);
    }
    private static URI uri(int port,String path){return URI.create("http://127.0.0.1:"+port+path);}
    private static int port()throws Exception{try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    private static void stop(Process process)throws Exception{process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}
    static String token(String algorithm,String claims)throws Exception {
        var base64=Base64.getUrlEncoder().withoutPadding();
        String input=base64.encodeToString(("{\"alg\":\""+algorithm+"\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8))+"."+base64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return input+"."+base64.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }
}
