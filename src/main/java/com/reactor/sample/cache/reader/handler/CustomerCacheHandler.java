package com.reactor.sample.cache.reader.handler;

import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.PathVariable;
import com.reactor.rust.annotations.RequestMapping;
import com.reactor.rust.annotations.RequestParam;
import com.reactor.rust.annotations.RouteAdmission;
import com.reactor.rust.cache.CacheReadResult;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.http.ResponseEntity;
import com.reactor.sample.cache.reader.service.CustomerCacheService;

@RequestMapping("/api/v1/cache/customers")
public final class CustomerCacheHandler {

    private static final String JSON = "application/json; charset=utf-8";

    private final CustomerCacheService customerCache;

    public CustomerCacheHandler(CustomerCacheService customerCache) {
        this.customerCache = customerCache;
    }

    @GetMapping(value = "/{id}", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 128, queueTimeoutMs = 25)
    public ResponseEntity<RawResponse> customer(@PathVariable("id") long id) {
        return toResponse(customerCache.customer(id), "customer_not_cached");
    }

    @GetMapping(value = "/by-customer-no", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 128, queueTimeoutMs = 25)
    public ResponseEntity<RawResponse> byCustomerNo(@RequestParam("customerNo") String customerNo) {
        return toResponse(customerCache.customerByCustomerNo(customerNo), "customer_no_not_cached");
    }

    @GetMapping(value = "/segments/{segment}", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 64, queueTimeoutMs = 25)
    public ResponseEntity<RawResponse> bySegment(@PathVariable("segment") String segment) {
        return toResponse(customerCache.customersBySegment(segment), "customer_segment_not_cached");
    }

    @GetMapping(value = "/statuses/{status}", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 64, queueTimeoutMs = 25)
    public ResponseEntity<RawResponse> byStatus(@PathVariable("status") String status) {
        return toResponse(customerCache.customersByStatus(status), "customer_status_not_cached");
    }

    @GetMapping(value = "/campaigns/{campaign}/candidates", responseType = RawResponse.class)
    @RouteAdmission(maxConcurrent = 64, queueTimeoutMs = 25)
    public ResponseEntity<RawResponse> campaignCandidates(@PathVariable("campaign") String campaign) {
        return toResponse(customerCache.campaignCandidates(campaign), "campaign_candidates_not_cached");
    }

    @GetMapping(value = "/meta", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> meta() {
        return toResponse(customerCache.meta(), "customer_cache_not_ready");
    }

    @GetMapping(value = "/cache-metrics", responseType = RawResponse.class)
    public ResponseEntity<RawResponse> cacheMetrics() {
        return ResponseEntity.ok(RawResponse.text(customerCache.metricsJson(), JSON));
    }

    private static ResponseEntity<RawResponse> toResponse(CacheReadResult result, String missCode) {
        if (result.hit()) {
            return ResponseEntity.ok(RawResponse.json(result.bytes()));
        }
        String code = result.isCacheNotReady() ? "customer_cache_not_ready" : missCode;
        return ResponseEntity.notFound(RawResponse.text(
                "{\"code\":\"" + code + "\",\"message\":\"Redis snapshot is not available yet\"}",
                JSON));
    }
}
