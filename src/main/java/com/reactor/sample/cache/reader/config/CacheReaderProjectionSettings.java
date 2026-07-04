package com.reactor.sample.cache.reader.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CacheReaderProjectionSettings(String name, String namespace) {

    public static List<String> projectionNames(CacheReaderProperties properties) {
        return parseProjectionNames(properties.get("sample.cache.customer.projections"));
    }

    public static List<CacheReaderProjectionSettings> resolveAll(CacheReaderProperties properties) {
        return projectionNames(properties).stream()
                .map(projection -> new CacheReaderProjectionSettings(projection, namespace(properties, projection)))
                .toList();
    }

    private static String namespace(CacheReaderProperties properties, String projection) {
        String specificKey = "sample.cache.customer." + projection + ".namespace";
        String specificRuntime = properties.getRuntimeOverride(specificKey);
        if (hasText(specificRuntime)) {
            return specificRuntime;
        }
        String baseRuntime = properties.getRuntimeOverride("sample.cache.customer.namespace");
        if (hasText(baseRuntime)) {
            return baseRuntime + "." + projection;
        }
        String specificFile = properties.getFileOptional(specificKey);
        if (hasText(specificFile)) {
            return specificFile;
        }
        return properties.get("sample.cache.customer.namespace") + "." + projection;
    }

    private static List<String> parseProjectionNames(String value) {
        Set<String> names = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String name = item.trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("sample.cache.customer.projections must contain at least one projection");
        }
        return List.copyOf(names);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
