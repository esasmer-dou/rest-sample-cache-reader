package com.reactor.sample.outbound;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.http.RawResponse;
import java.util.concurrent.CompletionStage;
import java.util.Optional;

@RestController("/api/v1/stock")
@RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = "stock")
@RequiresProperty(name = "sample.outbound.enabled", value = "true", matchIfMissing = true)
public final class StockHandler {
    private final StockClient plain;
    private final ProtectedStockClient protectedClient;
    public StockHandler(Optional<StockClient> plain, Optional<ProtectedStockClient> protectedClient) {
        this.plain = plain.orElse(null); this.protectedClient = protectedClient.orElse(null);
        if ((this.plain == null) == (this.protectedClient == null)) throw new IllegalStateException("Select exactly one stock client");
    }
    @GetMapping("/small") public CompletionStage<RawResponse> small() {
        return (protectedClient != null ? protectedClient.small() : plain.small()).thenApply(RawResponse::json);
    }
    @GetMapping("/heavy") public CompletionStage<RawResponse> heavy() {
        return (protectedClient != null ? protectedClient.heavy() : plain.heavy()).thenApply(RawResponse::json);
    }
    @GetMapping("/error") public CompletionStage<RawResponse> unavailable() {
        if (protectedClient == null) throw new IllegalStateException("Enable sample.outbound.breaker.enabled for the error example");
        return protectedClient.unavailable().thenApply(RawResponse::json);
    }
}
