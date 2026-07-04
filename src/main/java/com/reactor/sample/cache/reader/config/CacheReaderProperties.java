package com.reactor.sample.cache.reader.config;

import com.reactor.rust.cache.projection.ProjectionPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public final class CacheReaderProperties implements ProjectionPropertySource {

    private static final String RESOURCE = "rust-spring.properties";

    private final Properties properties;

    private CacheReaderProperties(Properties properties) {
        this.properties = properties;
    }

    public static CacheReaderProperties load() {
        Properties loaded = new Properties();
        try (InputStream input = CacheReaderProperties.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            loaded.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
        return new CacheReaderProperties(loaded);
    }

    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    public String get(String key) {
        String value = getRuntimeOverride(key);
        if (value == null) {
            value = properties.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value.trim();
    }

    @Override
    public String getOptional(String key) {
        String value = getRuntimeOverride(key);
        if (value == null) {
            value = properties.getProperty(key);
        }
        return value == null ? "" : value.trim();
    }

    @Override
    public String getRuntimeOverride(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(toEnvKey(key));
        }
        return value == null ? null : value.trim();
    }

    @Override
    public String getFileOptional(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    public int getInt(String key) {
        String value = get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property must be an integer: " + key + "=" + value, e);
        }
    }

    public long getLong(String key) {
        String value = get(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property must be a long: " + key + "=" + value, e);
        }
    }

    private static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }
}
