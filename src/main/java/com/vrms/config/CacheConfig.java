package com.vrms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory caches for the hottest reads:
 *  - "fleet": the public vehicle list behind the home and fleet pages, evicted on every vehicle
 *    or contract change (contracts change vehicle status);
 *  - "branches": rarely changes, evicted on edits;
 *  - "dashboard": staff KPIs, refreshed at most every 15 seconds.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String FLEET = "fleet";
    public static final String BRANCHES = "branches";
    public static final String DASHBOARD = "dashboard";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(FLEET, Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(10).recordStats().build());
        manager.registerCustomCache(BRANCHES, Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10).recordStats().build());
        manager.registerCustomCache(DASHBOARD, Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(15)).maximumSize(10).recordStats().build());
        return manager;
    }
}
