package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.versioned.VersionedJsonCacheReader;
import com.reactor.sample.cache.reader.config.CacheReaderProperties;

public final class CustomerCacheService {

    private final RustCache cache;
    private final VersionedJsonCacheReader customers;

    public CustomerCacheService(RustCache cache, CacheReaderProperties properties) {
        this.cache = cache;
        this.customers = cache.versionedJson(properties.get("sample.cache.customer.namespace")).reader();
    }

    public CacheReadResult customer(long id) {
        return customers.getById(id);
    }

    public CacheReadResult customersBySegment(String segment) {
        return customers.getIndex("segment", segment == null || segment.isBlank() ? "standard" : segment);
    }

    public CacheReadResult customerByCustomerNo(String customerNo) {
        return customers.getIndex("customer-no", customerNo == null ? "" : customerNo.trim());
    }

    public CacheReadResult customersByStatus(String status) {
        return customers.getIndex("status", status == null || status.isBlank() ? "active" : status);
    }

    public CacheReadResult campaignCandidates(String campaign) {
        return customers.getIndex("campaign", campaign == null || campaign.isBlank() ? "retention" : campaign);
    }

    public CacheReadResult meta() {
        return customers.getMeta();
    }

    public String metricsJson() {
        return cache.metricsJson();
    }
}
