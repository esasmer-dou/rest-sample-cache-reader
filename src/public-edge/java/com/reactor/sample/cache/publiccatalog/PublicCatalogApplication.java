package com.reactor.sample.cache.publiccatalog;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;
import com.reactor.rust.cache.integration.EnableRustCache;

@EnableRustCache(publicEdgePolicies = PublicCatalogHandler.class,
        publicEdgeEnabledProperty = "sample.public.edge.enabled")
@ReactorApplication(name = "Public Catalog Sample",
        description = "Public product snapshots with opt-in Rust edge policies",
        scanBasePackages = "com.reactor.sample.cache.publiccatalog")
public final class PublicCatalogApplication {
    private PublicCatalogApplication() {}

    public static void main(String[] args) {
        RestApplication.run(PublicCatalogApplication.class, args);
    }
}
