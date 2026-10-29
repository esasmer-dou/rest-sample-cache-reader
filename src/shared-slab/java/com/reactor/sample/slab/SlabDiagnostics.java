package com.reactor.sample.slab;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.http.RawResponse;

@RestController("/internal/slab")
@RequiresProperty(name = "sample.diagnostics.enabled", value = "true")
public final class SlabDiagnostics {
    private final CatalogReader reader;
    public SlabDiagnostics(CatalogReader reader) { this.reader = reader; }
    @GetMapping("/metrics") public RawResponse metrics() {
        return RawResponse.text(NativeBridge.nativeMetricsPrometheus()
                + "sample_slab_active " + (reader.slabActive() ? 1 : 0)
                + "\nsample_slab_payload_bytes " + reader.slabBytes()
                + "\nsample_slab_reads_total " + reader.readCount() + '\n', "text/plain; charset=utf-8");
    }
}
