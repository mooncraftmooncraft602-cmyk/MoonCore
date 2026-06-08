package com.mooncore.data.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fournit des caches Caffeine nommés et partagés. Les modules demandent un cache par nom ;
 * les paramètres par défaut (TTL, taille max) viennent de {@code config.yml > cache}.
 */
public final class CacheService {

    private final long defaultExpireSeconds;
    private final long defaultMaxSize;
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    public CacheService(ConfigurationSection cfg) {
        this.defaultExpireSeconds = cfg != null ? cfg.getLong("default-expire-after-access-seconds", 600) : 600;
        this.defaultMaxSize = cfg != null ? cfg.getLong("default-maximum-size", 50_000) : 50_000;
    }

    /** Cache nommé avec les paramètres par défaut. */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> cache(String name) {
        return (Cache<K, V>) caches.computeIfAbsent(name, n -> buildDefault());
    }

    /** Cache nommé avec paramètres personnalisés (créé une seule fois). */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> cache(String name, Duration expireAfterAccess, long maximumSize) {
        return (Cache<K, V>) caches.computeIfAbsent(name, n -> {
            Caffeine<Object, Object> b = Caffeine.newBuilder();
            if (expireAfterAccess != null && !expireAfterAccess.isZero()) {
                b.expireAfterAccess(expireAfterAccess);
            }
            if (maximumSize > 0) {
                b.maximumSize(maximumSize);
            }
            return b.build();
        });
    }

    private Cache<?, ?> buildDefault() {
        Caffeine<Object, Object> b = Caffeine.newBuilder();
        if (defaultExpireSeconds > 0) b.expireAfterAccess(Duration.ofSeconds(defaultExpireSeconds));
        if (defaultMaxSize > 0) b.maximumSize(defaultMaxSize);
        return b.build();
    }

    public void invalidateAll() {
        caches.values().forEach(Cache::invalidateAll);
    }
}
