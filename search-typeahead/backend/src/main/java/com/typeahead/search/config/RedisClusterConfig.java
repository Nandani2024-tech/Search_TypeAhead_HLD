package com.typeahead.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisClusterConfig {

    private StringRedisTemplate createTemplate(String host, int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }

    @Bean
    public ConsistentHashRouter consistentHashRouter() {
        // Standard practice: 160 virtual nodes per physical node for balanced distribution
        ConsistentHashRouter router = new ConsistentHashRouter(160);
        
        // Registering the three nodes based on docker-compose ports
        router.addNode("redis-node-1", createTemplate("localhost", 6379));
        router.addNode("redis-node-2", createTemplate("localhost", 6380));
        router.addNode("redis-node-3", createTemplate("localhost", 6381));
        
        return router;
    }

    // We keep a dummy primary bean to satisfy the existing autowiring locally if needed, 
    // although we will inject ConsistentHashRouter directly now.
    @Bean
    @Primary
    public StringRedisTemplate defaultStringRedisTemplate() {
        return createTemplate("localhost", 6379);
    }
}
