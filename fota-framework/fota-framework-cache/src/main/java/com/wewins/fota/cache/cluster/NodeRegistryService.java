package com.wewins.fota.cache.cluster;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Node registry service backed by Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.registry.enabled", havingValue = "true", matchIfMissing = true)
public class NodeRegistryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ClusterProperties clusterProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        register("UP");
    }

    @Scheduled(fixedDelayString = "${app.registry.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        register("UP");
    }

    @PreDestroy
    public void onShutdown() {
        unregister();
    }

    public void register(String status) {
        ClusterProperties.Node node = clusterProperties.getNode();
        if (node.getCode() == null || node.getCode().isBlank()) {
            log.warn("Node code is blank, skip register");
            return;
        }

        String nodeCode = node.getCode();
        String key = String.format(RedisKeyConstants.REGISTRY_NODE_KEY_TEMPLATE, nodeCode);
        NodeInstance existing = getInstanceByKey(key);
        NodeInstance instance = buildInstance(existing, status);
        Duration ttl = Duration.ofSeconds(clusterProperties.getRegistry().getTtlSeconds());

        redisTemplate.opsForValue().set(key, instance, ttl);
        redisTemplate.opsForSet().add(RedisKeyConstants.REGISTRY_NODE_SET_KEY, nodeCode);
    }

    public void unregister() {
        ClusterProperties.Node node = clusterProperties.getNode();
        if (node.getCode() == null || node.getCode().isBlank()) {
            return;
        }
        String key = String.format(RedisKeyConstants.REGISTRY_NODE_KEY_TEMPLATE, node.getCode());
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(RedisKeyConstants.REGISTRY_NODE_SET_KEY, node.getCode());
    }

    public List<NodeInstance> listOnlineNodes() {
        Set<Object> members = redisTemplate.opsForSet().members(RedisKeyConstants.REGISTRY_NODE_SET_KEY);
        List<NodeInstance> result = new ArrayList<>();
        if (members == null || members.isEmpty()) {
            return result;
        }
        for (Object member : members) {
            String nodeCode = String.valueOf(member);
            String key = String.format(RedisKeyConstants.REGISTRY_NODE_KEY_TEMPLATE, nodeCode);
            NodeInstance instance = getInstanceByKey(key);
            if (instance == null) {
                redisTemplate.opsForSet().remove(RedisKeyConstants.REGISTRY_NODE_SET_KEY, nodeCode);
                continue;
            }
            result.add(instance);
        }
        result.sort(Comparator.comparing(NodeInstance::getCode));
        return result;
    }

    public NodeInstance getLocalInstance() {
        ClusterProperties.Node node = clusterProperties.getNode();
        if (node.getCode() == null || node.getCode().isBlank()) {
            return null;
        }
        String key = String.format(RedisKeyConstants.REGISTRY_NODE_KEY_TEMPLATE, node.getCode());
        return getInstanceByKey(key);
    }

    private NodeInstance getInstanceByKey(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof NodeInstance instance) {
                return instance;
            }
        } catch (Exception ex) {
            // Compatibility fallback: old payload may still contain the legacy class name.
            log.warn("Failed to deserialize node instance from Redis, deleting stale key: key={}", key, ex);
            redisTemplate.delete(key);
        }
        return null;
    }

    private NodeInstance buildInstance(NodeInstance existing, String status) {
        ClusterProperties.Node node = clusterProperties.getNode();
        long now = System.currentTimeMillis();
        NodeInstance instance = new NodeInstance();
        instance.setCode(node.getCode());
        instance.setName(node.getName());
        instance.setBaseUrl(node.getBaseUrl());
        instance.setTimeZone(node.getTimeZone());
        instance.setMode(clusterProperties.getMode());
        // Keep the field for compatibility, but use node code as the only stable identity.
        instance.setInstanceId(node.getCode());
        instance.setStatus(status);
        instance.setRegisteredAt(existing != null ? existing.getRegisteredAt() : now);
        instance.setLastHeartbeatAt(now);
        return instance;
    }
}
