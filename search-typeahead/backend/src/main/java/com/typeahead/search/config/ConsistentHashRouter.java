package com.typeahead.search.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

public class ConsistentHashRouter {

    public static class RedisNode {
        private final String name;
        private final StringRedisTemplate template;

        public RedisNode(String name, StringRedisTemplate template) {
            this.name = name;
            this.template = template;
        }

        public String getName() { return name; }
        public StringRedisTemplate getTemplate() { return template; }
    }

    private final TreeMap<Long, RedisNode> ring = new TreeMap<>();
    private final int virtualNodes;

    public ConsistentHashRouter(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public void addNode(String nodeIdentifier, StringRedisTemplate redisTemplate) {
        RedisNode node = new RedisNode(nodeIdentifier, redisTemplate);
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(nodeIdentifier + "-VNODE-" + i);
            ring.put(hash, node);
        }
    }

    public void removeNode(String nodeIdentifier) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(nodeIdentifier + "-VNODE-" + i);
            ring.remove(hash);
        }
    }

    public java.util.Set<String> listNodes() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        ring.values().forEach(n -> names.add(n.getName()));
        return names;
    }

    public RedisNode route(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        Long mappedKey = ring.ceilingKey(hash);
        if (mappedKey == null) {
            mappedKey = ring.firstKey(); // Wrap around the ring
        }
        return ring.get(mappedKey);
    }

    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            // Taking 4 bytes of MD5 to form a 32-bit positive integer hash
            long hash = ((long) (digest[3] & 0xFF) << 24) |
                        ((long) (digest[2] & 0xFF) << 16) |
                        ((long) (digest[1] & 0xFF) << 8) |
                        ((long) (digest[0] & 0xFF));
            return hash & 0xffffffffL;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }
}
