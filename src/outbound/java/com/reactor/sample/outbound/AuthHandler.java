package com.reactor.sample.outbound;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RustEdgeJwt;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.security.Authenticated;
import java.nio.charset.StandardCharsets;

@RestController("/api/v1/account")
@RequiresProperty(name = "sample.api.enabled", value = "true", matchIfMissing = true)
public final class AuthHandler {
    private static final RawResponse OK = RawResponse.json("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
    @Authenticated @RustEdgeJwt @GetMapping("/me") public RawResponse me() { return OK; }
    @Authenticated(roles = "admin") @RustEdgeJwt @GetMapping("/admin") public RawResponse admin() { return OK; }
    @Authenticated @RustEdgeJwt @GetMapping("/async-me")
    public java.util.concurrent.CompletionStage<RawResponse> asyncMe() {
        return java.util.concurrent.CompletableFuture.completedFuture(OK);
    }
    @GetMapping("/health") public RawResponse health() { return OK; }
    @GetMapping("/path/{first}/{second}")
    public RawResponse path(@com.reactor.rust.annotations.PathVariable("first") String first,
                            @com.reactor.rust.annotations.PathVariable("second") String second) {
        return RawResponse.text(first + "|" + second, "text/plain; charset=utf-8");
    }
    @GetMapping("/async-path/{first}/{second}")
    public java.util.concurrent.CompletionStage<RawResponse> asyncPath(
            @com.reactor.rust.annotations.PathVariable("first") String first,
            @com.reactor.rust.annotations.PathVariable("second") String second) {
        return java.util.concurrent.CompletableFuture.completedFuture(path(first, second));
    }
}
