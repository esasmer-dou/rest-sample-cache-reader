package com.reactor.sample.cache.reader.config;

import com.reactor.rust.cache.projection.CacheReaderProjectionSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheReaderProjectionSettingsTest {

    @AfterEach
    void clearRuntimeOverrides() {
        System.clearProperty("sample.cache.customer.projections");
        System.clearProperty("sample.cache.customer.namespace");
        System.clearProperty("sample.cache.customer.detail.namespace");
    }

    @Test
    void resolvesProjectionNamespacesFromClasspathDefaults() {
        List<CacheReaderProjectionSettings> settings =
                CacheReaderProjectionSettings.resolveAll(CacheReaderProperties.load(), "sample.cache.customer");

        assertEquals(5, settings.size());
        assertEquals("detail", settings.get(0).name());
        assertEquals("crm.customer.detail", settings.get(0).namespace());
    }

    @Test
    void resolvesProjectionListFromRuntimeOverride() {
        System.setProperty("sample.cache.customer.projections", "detail,campaign,detail");

        List<CacheReaderProjectionSettings> settings =
                CacheReaderProjectionSettings.resolveAll(CacheReaderProperties.load(), "sample.cache.customer");

        assertEquals(2, settings.size());
        assertEquals("detail", settings.get(0).name());
        assertEquals("campaign", settings.get(1).name());
    }

    @Test
    void derivesNamespacesFromRuntimeBaseNamespace() {
        System.setProperty("sample.cache.customer.namespace", "crm.customer.prod");
        System.setProperty("sample.cache.customer.projections", "detail,campaign");

        List<CacheReaderProjectionSettings> settings =
                CacheReaderProjectionSettings.resolveAll(CacheReaderProperties.load(), "sample.cache.customer");

        assertEquals("crm.customer.prod.detail", settings.get(0).namespace());
        assertEquals("crm.customer.prod.campaign", settings.get(1).namespace());
    }
}
