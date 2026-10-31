package com.reactor.sample.cache.reader.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.api.CacheReadValue;
import com.reactor.rust.cache.http.CacheResponses;
import com.reactor.rust.http.MediaType;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.cache.reader.service.CustomerCacheService;

@RestController("/api/v1/cache/customers")
@RouteWorkload(RouteWorkload.Type.CACHE_READ)
public final class CustomerCacheHandler {
    private static final boolean COMMON_ERRORS = com.reactor.rust.config.PropertiesLoader.getBoolean("sample.errors.common-contract", false);

    private final CustomerCacheService customerCache;

    public CustomerCacheHandler(CustomerCacheService customerCache) {
        this.customerCache = customerCache;
    }

    @GetMapping("/{id}")
    @RouteWorkload(value = RouteWorkload.Type.CACHE_READ, budget = "cache-read-point")
    public ResponseEntity<?> customer(@PathVariable("id") long id) {
        return valueResponse(customerCache.customerValue(id), "customer_not_cached");
    }

    @GetMapping("/by-customer-no")
    @RouteWorkload(value = RouteWorkload.Type.CACHE_READ, budget = "cache-read-point")
    public ResponseEntity<RawResponse> byCustomerNo(@RequestParam("customerNo") String customerNo) {
        return toResponse(customerCache.customerByCustomerNo(customerNo), "customer_no_not_cached");
    }

    @GetMapping("/segments/{segment}")
    public ResponseEntity<?> bySegment(@PathVariable("segment") String segment) {
        return valueResponse(customerCache.segmentValue(segment), "customer_segment_not_cached");
    }

    @GetMapping("/statuses/{status}")
    public ResponseEntity<RawResponse> byStatus(@PathVariable("status") String status) {
        return toResponse(customerCache.customersByStatus(status), "customer_status_not_cached");
    }

    @GetMapping("/campaigns/{campaign}/candidates")
    public ResponseEntity<RawResponse> campaignCandidates(@PathVariable("campaign") String campaign) {
        return toResponse(customerCache.campaignCandidates(campaign), "campaign_candidates_not_cached");
    }

    @GetMapping("/meta")
    @RouteWorkload(RouteWorkload.Type.STANDARD)
    public ResponseEntity<RawResponse> meta() {
        return toResponse(customerCache.meta(), "customer_cache_not_ready");
    }

    @GetMapping("/cache-metrics")
    @RouteWorkload(RouteWorkload.Type.STANDARD)
    public ResponseEntity<RawResponse> cacheMetrics() {
        return ResponseEntity.ok(RawResponse.text(customerCache.metricsJson(), MediaType.APPLICATION_JSON_UTF8));
    }

    private static ResponseEntity<RawResponse> toResponse(CacheReadResult result, String missCode) {
        return CacheResponses.reply(result, missCode, "customer_cache_not_ready", COMMON_ERRORS);
    }

    private static ResponseEntity<?> valueResponse(CacheReadValue value, String missCode) {
        return CacheResponses.reply(value, missCode, "customer_cache_not_ready", COMMON_ERRORS);
    }
}
