package com.reactor.sample.trace;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.tracing.Traced;
import java.util.concurrent.CompletionStage;

@RestController("/api/v1/orders")
@RequiresProperty(name = "sample.api.enabled", value = "true", matchIfMissing = true)
@RouteWorkload(value = RouteWorkload.Type.RPC_READ, budget = "inventory")
public final class OrderHandler {
    private final InventoryClient inventory;
    public OrderHandler(InventoryClient inventory) { this.inventory = inventory; }

    @GetMapping("/small") @Traced("orders.small")
    public CompletionStage<RawResponse> small() { return inventory.small().thenApply(RawResponse::json); }
    @GetMapping("/heavy") @Traced("orders.heavy")
    public CompletionStage<RawResponse> heavy() { return inventory.heavy().thenApply(RawResponse::json); }
    @GetMapping("/retry") @Traced("orders.retry")
    public CompletionStage<RawResponse> retry() { return inventory.retry().thenApply(RawResponse::json); }
}
