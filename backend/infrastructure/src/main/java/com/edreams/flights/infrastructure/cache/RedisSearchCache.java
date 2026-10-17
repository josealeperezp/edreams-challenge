package com.edreams.flights.infrastructure.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.edreams.flights.application.port.out.SearchCache;
import com.edreams.flights.application.search.SearchPage;
import com.edreams.flights.application.search.SearchStats;
import com.edreams.flights.infrastructure.json.JsonCodec;
import com.edreams.flights.infrastructure.json.PageJson;
import com.edreams.flights.infrastructure.json.StatsJson;

/**
 * Search pages and stats in Redis, keyed by inventory version so a completed run invalidates everything in
 * O(1): new version, new keys, old keys expire by TTL. Any Redis failure is logged and treated as
 * a miss; the cache can disappear without a search ever failing.
 */
public class RedisSearchCache implements SearchCache {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchCache.class);
    private static final String VERSION_KEY = "inventory:version";
    private static final String PAGE_PREFIX = "search:";
    private static final String STATS_PREFIX = "stats:";

    /** After a failure Redis is skipped for this long, so an outage costs one timeout per window, not per call. */
    private static final long BYPASS_NANOS = Duration.ofSeconds(10).toNanos();

    private final StringRedisTemplate redis;
    private final JsonCodec json;
    private final Duration ttl;
    private volatile long bypassUntil;

    public RedisSearchCache(StringRedisTemplate redis, JsonCodec json, Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    @Override
    public long currentVersion() {
        if (bypassed()) {
            return 0L;
        }
        try {
            String value = redis.opsForValue().get(VERSION_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException e) {
            fail("read version", e);
            return 0L;
        }
    }

    @Override
    public long bumpVersion() {
        try {
            Long value = redis.opsForValue().increment(VERSION_KEY);
            bypassUntil = 0;
            return value == null ? 0L : value;
        } catch (RuntimeException e) {
            fail("bump version", e);
            return 0L;
        }
    }

    @Override
    public Optional<SearchPage> get(String key) {
        if (bypassed()) {
            return Optional.empty();
        }
        try {
            String cached = redis.opsForValue().get(pageKey(key));
            return cached == null ? Optional.empty() : Optional.of(json.readPage(cached).toPage());
        } catch (RuntimeException e) {
            fail("read page", e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, SearchPage page) {
        if (bypassed()) {
            return;
        }
        try {
            redis.opsForValue().set(pageKey(key), json.writePage(PageJson.from(page)), ttl);
        } catch (RuntimeException e) {
            fail("write page", e);
        }
    }

    @Override
    public Optional<SearchStats> getStats(String key) {
        if (bypassed()) {
            return Optional.empty();
        }
        try {
            String cached = redis.opsForValue().get(hashed(STATS_PREFIX, key));
            return cached == null ? Optional.empty() : Optional.of(json.readStats(cached).toStats());
        } catch (RuntimeException e) {
            fail("read stats", e);
            return Optional.empty();
        }
    }

    @Override
    public void putStats(String key, SearchStats stats) {
        if (bypassed()) {
            return;
        }
        try {
            redis.opsForValue().set(hashed(STATS_PREFIX, key), json.writeStats(StatsJson.from(stats)), ttl);
        } catch (RuntimeException e) {
            fail("write stats", e);
        }
    }

    private boolean bypassed() {
        return System.nanoTime() - bypassUntil < 0;
    }

    private void fail(String operation, RuntimeException e) {
        bypassUntil = System.nanoTime() + BYPASS_NANOS;
        log.warn("Redis {} failed, cache bypassed for {}s: {}", operation, BYPASS_NANOS / 1_000_000_000, e.getMessage());
    }

    static String pageKey(String key) {
        return hashed(PAGE_PREFIX, key);
    }

    private static String hashed(String prefix, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
