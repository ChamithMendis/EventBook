package com.chamith.eventbook.config;

import com.chamith.eventbook.dto.EventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Each cache holds one known value shape, so we give it its own typed
 * Jackson serializer instead of relying on GenericJackson2JsonRedisSerializer's
 * embedded-"@class" polymorphic typing, which gets awkward with generic
 * collections (List<Record>) and buys us nothing here since the type
 * per cache is already fixed.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        RedisCacheConfiguration eventListCacheConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper,
                                mapper.getTypeFactory().constructCollectionType(List.class, EventResponse.class))));

        RedisCacheConfiguration eventCacheConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, EventResponse.class)));

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                "events", eventListCacheConfig,
                "event", eventCacheConfig
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
