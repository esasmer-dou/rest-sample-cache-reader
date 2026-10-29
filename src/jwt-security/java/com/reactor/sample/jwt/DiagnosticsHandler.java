package com.reactor.sample.jwt;

import com.reactor.rust.annotations.*;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.http.RawResponse;

@RestController("/api/v1/jwt")
@RequiresProperty(name = "sample.diagnostics.enabled", value = "true")
public final class DiagnosticsHandler {
    @GetMapping("/metrics") public RawResponse metrics() {
        return RawResponse.text(LocalJwtProvider.metrics() + NativeBridge.nativeMetricsPrometheus(), "text/plain; charset=utf-8");
    }
}
