package com.reactor.sample.outbound;

import com.reactor.rust.bridge.RequestGuardContext;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.exception.UnauthorizedException;
import com.reactor.rust.security.SecurityRequestGuardFactory;

/** Separate JVM so immutable native policy registration cannot leak between test cases. */
public final class JwtStartupProbe {
    public static void main(String[] args) throws Exception {
        PropertiesLoader.load();
        AuthHandler_EdgeJwtPolicies.install();
        String change = System.getProperty("test.edge.policy-drift", "none");
        String value = switch (change) {
            case "hmac-secret" -> "different-012345678901234567890123456789";
            case "issuer", "audience" -> "different";
            case "clock-skew-seconds" -> "1";
            case "max-token-chars" -> "4096";
            case "require-expiration" -> "false";
            default -> null;
        };
        if (value != null) System.setProperty("reactor.security.jwt." + change, value);
        try {
            var guard = new SecurityRequestGuardFactory().create(AuthHandler.class, AuthHandler.class.getMethod("me"));
            if (value != null) throw new AssertionError("Policy drift was accepted: " + change);
            long expiry = java.time.Instant.now().getEpochSecond() + 3600;
            String token = OutboundLiveTest.token("HS256", "{\"sub\":\"user\",\"iss\":\"sample-issuer\",\"aud\":\"stock-api\",\"exp\":" + expiry + "}");
            try {
                guard.before(new RequestGuardContext(null, null, "authorization: Bearer " + token, null));
                throw new AssertionError("A valid token without a native request proof was accepted");
            } catch (UnauthorizedException expected) { System.out.println("PROOF_REQUIRED"); }
        } catch (IllegalStateException expected) {
            if (value == null || !expected.getMessage().contains("policies differ")) throw expected;
            System.out.println("POLICY_DRIFT_REJECTED");
        }
    }
}
