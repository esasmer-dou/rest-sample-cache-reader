package com.reactor.sample.jwt;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.security.Authenticated;
import com.reactor.rust.security.SecurityContext;
import java.nio.charset.StandardCharsets;

@RestController("/api/v1/jwt")
public final class AccountHandler {
    private static final RawResponse OK = RawResponse.json("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
    @GetMapping("/health") public RawResponse health() { return OK; }
    @Authenticated(roles = "reader") @GetMapping("/me") public RawResponse me() {
        if (!"sample-user".equals(SecurityContext.requirePrincipal().subject())) {
            throw new com.reactor.rust.exception.ForbiddenException("Unexpected account");
        }
        return OK;
    }
    @Authenticated(roles = "admin") @GetMapping("/admin") public RawResponse admin() { return OK; }
}
