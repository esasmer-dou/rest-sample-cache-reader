package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.projection.CacheMetricsRead;
import com.reactor.rust.cache.projection.GenerateProjectionReader;
import com.reactor.rust.cache.projection.ProjectionIdRead;
import com.reactor.rust.cache.projection.ProjectionIndexRead;
import com.reactor.rust.cache.projection.ProjectionMetaRead;

@GenerateProjectionReader(
        rootPrefix = "sample.cache.customer",
        generatedName = "CustomerCacheReader",
        restBean = true)
public interface CustomerCacheService {

    @ProjectionIdRead(projection = "detail")
    CacheReadResult customer(long id);

    @ProjectionIndexRead(projection = "segment", index = "segment", defaultValue = "standard")
    CacheReadResult customersBySegment(String segment);

    @ProjectionIndexRead(projection = "detail", index = "customer-no")
    CacheReadResult customerByCustomerNo(String customerNo);

    @ProjectionIndexRead(projection = "status", index = "status", defaultValue = "active")
    CacheReadResult customersByStatus(String status);

    @ProjectionIndexRead(projection = "campaign", index = "campaign", defaultValue = "retention")
    CacheReadResult campaignCandidates(String campaign);

    @ProjectionMetaRead(projection = "meta")
    CacheReadResult meta();

    @CacheMetricsRead
    String metricsJson();
}
