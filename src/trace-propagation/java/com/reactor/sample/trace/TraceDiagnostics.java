package com.reactor.sample.trace;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.bridge.NativeBridge;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.metrics.Metrics;
import com.reactor.rust.tracing.TraceExporter;
import com.reactor.rust.tracing.TraceSpan;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Internal demonstration exporter: retains only the most recent span, not an unbounded trace list. */
@RestController("/internal/trace")
@RequiresProperty(name = "sample.diagnostics.enabled", value = "true")
public final class TraceDiagnostics implements TraceExporter {
    private static final AtomicReference<TraceSpan> LAST = new AtomicReference<>();
    private static final AtomicLong SPANS = new AtomicLong();
    @Override public void export(TraceSpan span) { LAST.set(span); SPANS.incrementAndGet(); }
    @GetMapping("/metrics") public RawResponse metrics() {
        TraceSpan span = LAST.get();
        return RawResponse.text(NativeBridge.nativeMetricsPrometheus() + "sample_trace_spans_total " + SPANS.get()
                + "\nsample_trace_parent_present " + (span != null && span.parentSpanId() != null ? 1 : 0)
                + "\nsample_trace_http_attempts " + Metrics.getInstance().getCounter("reactor_http_client_requests_total")
                + "\nsample_trace_http_retries " + Metrics.getInstance().getCounter("reactor_http_client_retries_total") + '\n',
                "text/plain; charset=utf-8");
    }
    @GetMapping("/last") public RawResponse last() {
        TraceSpan span = LAST.get();
        return RawResponse.text(span == null ? "{}"
                : "{\"traceId\":\"" + span.traceId() + "\",\"spanId\":\"" + span.spanId()
                + "\",\"parentSpanId\":\"" + span.parentSpanId() + "\",\"success\":" + span.success() + '}',
                "application/json");
    }
}
