package com.reactor.sample.jwt;

import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.security.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;

/** Trust files are selected by the operator at startup, never by incoming tokens. */
public final class LocalJwtProvider implements JwtVerifierProvider {
    private static volatile AsymmetricJwtVerifier source;
    private static volatile VerifiedTokenCache cache;

    @Override public JwtVerifier create() {
        Path directory = Path.of(PropertiesLoader.get("sample.jwt.public-key-directory", "keys"));
        JwtKeySet.Key[] keys = new JwtKeySet.Key[3];
        JwtAlgorithm[] algorithms = { JwtAlgorithm.RS256, JwtAlgorithm.ES256, JwtAlgorithm.EdDSA };
        String[] factories = { "RSA", "EC", "Ed25519" };
        for (int i = 0; i < keys.length; i++) {
            JwtAlgorithm algorithm = algorithms[i];
            try (var input = Files.newInputStream(directory.resolve(algorithm.name() + ".der"))) {
                byte[] encoded = input.readNBytes(4097);
                if (encoded.length == 0 || encoded.length > 4096) throw new IOException("Public key exceeds 4096 bytes");
                keys[i] = new JwtKeySet.Key(algorithm.name(), algorithm, KeyFactory.getInstance(factories[i])
                        .generatePublic(new X509EncodedKeySpec(encoded)));
            } catch (IOException | GeneralSecurityException failure) {
                throw new IllegalStateException("Cannot load pinned public key " + algorithm, failure);
            }
        }
        var policy = new JwtVerificationPolicy(
                PropertiesLoader.get("sample.jwt.issuer", "sample-issuer"),
                PropertiesLoader.get("sample.jwt.audience", "sample-api"), "sub", "roles", 0, true,
                8192, 1024, 64, 64);
        source = new AsymmetricJwtVerifier(new JwtKeySet(keys), policy, Clock.systemUTC(),
                PropertiesLoader.getInt("sample.jwt.workers", 1));
        cache = PropertiesLoader.getBoolean("sample.jwt.cache.enabled", false) ? source.withCache(
                new VerifiedTokenCache.Options(PropertiesLoader.getInt("sample.jwt.cache.slots", 256),
                        PropertiesLoader.getInt("sample.jwt.workers", 1), 0,
                        PropertiesLoader.getInt("sample.jwt.cache.max-bytes", 262144))) : null;
        return cache == null ? source : cache;
    }

    static String metrics() {
        var active = cache;
        return "sample_jwt_signature_checks_total " + source.signatureVerificationCount() + "\n"
                + "sample_jwt_key_initializations_total " + source.keyInitializationCount() + "\n"
                + "sample_jwt_cache_hits_total " + (active == null ? 0 : active.hitCount()) + "\n"
                + "sample_jwt_cache_misses_total " + (active == null ? 0 : active.missCount()) + "\n"
                + "sample_jwt_cache_retained_bytes " + (active == null ? 0 : active.retainedBytes()) + "\n"
                + "sample_jwt_cache_capacity " + (active == null ? 0 : active.capacity()) + "\n"
                + "sample_jwt_cache_entries " + (active == null ? 0 : active.retainedEntryCount()) + "\n";
    }
}
