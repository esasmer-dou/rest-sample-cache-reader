package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.config.CacheProperties;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.projection.CacheReaderProjectionSettings;
import com.reactor.rust.cache.projection.VersionedJsonProjectionReaders;
import com.reactor.rust.cache.projection.VersionedJsonProjectionReaders.BoundIndex;
import com.reactor.rust.cache.projection.VersionedJsonProjectionReaders.BoundProjection;
import com.reactor.sample.model.cache.CustomerProjection;
import com.reactor.sample.model.cache.CustomerProjectionIndex;

import java.util.List;

public final class CustomerCacheService {

    private final RustCache cache;
    private final BoundProjection customerDetails;
    private final BoundIndex customersByCustomerNo;
    private final BoundIndex customersBySegment;
    private final BoundIndex customersByStatus;
    private final BoundIndex campaignCandidates;
    private final BoundProjection metadata;

    public CustomerCacheService(RustCache cache, CacheProperties properties) {
        this(cache, properties, CacheReaderProjectionSettings.resolveAll(properties, "sample.cache.customer"));
    }

    public CustomerCacheService(
            RustCache cache,
            CacheProperties properties,
            List<CacheReaderProjectionSettings> projectionSettings) {
        this.cache = cache;
        long versionCacheMillis = properties.getLong("sample.cache.customer.version-cache-ms");
        VersionedJsonProjectionReaders readers = VersionedJsonProjectionReaders.create(
                cache,
                projectionSettings,
                versionCacheMillis);
        this.customerDetails = readers.bind(CustomerProjection.DETAIL);
        this.customersByCustomerNo = customerDetails.bind(CustomerProjectionIndex.CUSTOMER_NO);
        this.customersBySegment = readers.bind(CustomerProjection.SEGMENT).bind(CustomerProjectionIndex.SEGMENT);
        this.customersByStatus = readers.bind(CustomerProjection.STATUS).bind(CustomerProjectionIndex.STATUS);
        this.campaignCandidates = readers.bind(CustomerProjection.CAMPAIGN).bind(CustomerProjectionIndex.CAMPAIGN);
        this.metadata = readers.bind(CustomerProjection.META);
    }

    public CacheReadResult customer(long id) {
        return customerDetails.getById(id);
    }

    public CacheReadResult customersBySegment(String segment) {
        return customersBySegment.get(segment == null || segment.isBlank() ? "standard" : segment);
    }

    public CacheReadResult customerByCustomerNo(String customerNo) {
        return customersByCustomerNo.get(customerNo == null ? "" : customerNo.trim());
    }

    public CacheReadResult customersByStatus(String status) {
        return customersByStatus.get(status == null || status.isBlank() ? "active" : status);
    }

    public CacheReadResult campaignCandidates(String campaign) {
        return campaignCandidates.get(campaign == null || campaign.isBlank() ? "retention" : campaign);
    }

    public CacheReadResult meta() {
        return metadata.getMeta();
    }

    public String metricsJson() {
        return cache.metricsJson();
    }

}
