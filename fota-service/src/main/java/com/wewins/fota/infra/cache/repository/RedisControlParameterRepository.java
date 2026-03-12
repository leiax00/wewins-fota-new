package com.wewins.fota.infra.cache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisControlParameterRepository implements ControlParameterRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ControlParameter> getGlobal() {
        String json = redisTemplate.opsForValue().get(RedisKeyConstants.CTRL_GLOBAL_KEY);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ControlParameter.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize global control parameter", e);
            return Optional.empty();
        }
    }

    @Override
    public void saveGlobal(ControlParameter param) {
        try {
            String json = objectMapper.writeValueAsString(param);
            redisTemplate.opsForValue().set(RedisKeyConstants.CTRL_GLOBAL_KEY, json);
        } catch (Exception e) {
            log.error("Failed to save global control parameter", e);
            throw new IllegalStateException("Failed to save global control parameter", e);
        }
    }

    @Override
    public Optional<ControlParameter> getByProduct(Long productId) {
        String key = String.format(RedisKeyConstants.CTRL_PRODUCT_KEY_TEMPLATE, productId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ControlParameter.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize product control parameter: productId={}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveByProduct(Long productId, ControlParameter param) {
        String key = String.format(RedisKeyConstants.CTRL_PRODUCT_KEY_TEMPLATE, productId);
        try {
            String json = objectMapper.writeValueAsString(param);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            log.error("Failed to save product control parameter: productId={}", productId, e);
            throw new IllegalStateException("Failed to save product control parameter", e);
        }
    }
}
