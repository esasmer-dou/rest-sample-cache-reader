package com.reactor.sample.outbound;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Local sample fixture only. Does not start the server, JNI or an authentication service. */
public final class TokenTool {
    private TokenTool() {}
    public static void main(String[] args) throws Exception {
        String secret = System.getenv("REACTOR_SECURITY_JWT_HMAC_SECRET");
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("Set REACTOR_SECURITY_JWT_HMAC_SECRET to at least 32 UTF-8 bytes");
        String mode = args.length == 0 ? "valid" : args[0];
        if (!java.util.List.of("valid", "expired", "admin").contains(mode))
            throw new IllegalArgumentException("Use valid, expired or admin");
        long expiry = Instant.now().getEpochSecond() + (mode.equals("expired") ? -60 : 3600);
        String claims = "{\"sub\":\"sample-user\",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\",\"exp\":" + expiry
                + ",\"roles\":[\"" + (mode.equals("admin") ? "admin" : "user") + "\"]}";
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String input = base64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.US_ASCII))
                + "." + base64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        System.out.println(input + "." + base64.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII))));
    }
}
