package com.reactor.sample.trace;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.HeaderParam;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.rust.http.HttpStatus;
import com.reactor.rust.tracing.W3cTraceparent;
import java.util.concurrent.atomic.AtomicInteger;

/** Local downstream for this example; it has no cache, database or retry side effects. */
@RestController("/inventory")
@RequiresProperty(name = "sample.inventory.enabled", value = "true")
public final class InventoryHandler {
    private static final RawResponse SMALL = RawResponse.text("{\"stock\":42}", "application/json");
    private static final RawResponse HEAVY = RawResponse.text(
            "{\"description\":\"" + "inventory-".repeat(400) + "\",\"stock\":42}", "application/json");
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile String retryHeader;

    @GetMapping("/small") public RawResponse small(@HeaderParam("traceparent") String header) {
        validate(header); return SMALL;
    }
    @GetMapping("/heavy") public RawResponse heavy(@HeaderParam("traceparent") String header) {
        validate(header); return HEAVY;
    }
    @GetMapping("/retry") public ResponseEntity<RawResponse> retry(@HeaderParam("traceparent") String header) {
        validate(header);
        int attempt = attempts.incrementAndGet();
        if (attempt == 1) retryHeader = header;
        if (attempt <= 3 && !header.equals(retryHeader)) throw new IllegalStateException("Retry changed traceparent");
        return ResponseEntity.status(attempt < 3 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK, SMALL);
    }
    private static void validate(String header) {
        if (W3cTraceparent.parse(header) == null) throw new IllegalArgumentException("Missing valid traceparent");
    }
}
