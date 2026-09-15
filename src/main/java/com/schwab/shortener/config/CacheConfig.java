package com.schwab.shortener.config;

import com.schwab.shortener.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String URL_MAPPINGS = "url-mappings";
    public static final String FEATURE_FLAGS = "feature-flags";
    public static final String ANALYTICS_COUNTS = "analytics-counts";

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory, AppProperties properties) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.shortener().cacheTtl())
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.java()))
                .disableCachingNullValues();
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(FEATURE_FLAGS, defaults.entryTtl(Duration.ofMinutes(1)))
                .withCacheConfiguration(ANALYTICS_COUNTS, defaults.entryTtl(Duration.ofMinutes(2)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager(URL_MAPPINGS, FEATURE_FLAGS, ANALYTICS_COUNTS);
    }
}
