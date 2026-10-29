package com.reactor.sample.outbound;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.http.RawResponse;

/** Explicit sample diagnostics; keep disabled on public application ingress. */
@RestController("/api/v1/account")
@RequiresProperty(name = "sample.diagnostics.enabled", value = "true")
@RequiresProperty(name = "sample.api.enabled", value = "true", matchIfMissing = true)
public final class DiagnosticsHandler {
    @GetMapping("/metrics") public RawResponse metrics() {
        return RawResponse.text(NativeBridge.nativeMetricsPrometheus(), "text/plain; charset=utf-8");
    }
}
