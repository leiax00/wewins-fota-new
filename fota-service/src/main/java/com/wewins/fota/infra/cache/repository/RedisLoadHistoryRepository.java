package com.wewins.fota.infra.cache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.repository.LoadHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisLoadHistoryRepository implements LoadHistoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void save(LoadSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        String dateKey = LocalDate.now().format(DATE_FORMATTER);
        String key = String.format(RedisKeyConstants.LOAD_HISTORY_KEY_TEMPLATE, dateKey);

        try {
            String json = objectMapper.writeValueAsString(List.of(toMap(snapshot)));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, RedisKeyConstants.LOAD_HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize LoadSnapshot", e);
        }
    }

    @Override
    public List<LoadSnapshot> getHistory(LocalDate date) {
        String dateKey = date.format(DATE_FORMATTER);
        String key = String.format(RedisKeyConstants.LOAD_HISTORY_KEY_TEMPLATE, dateKey);

        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<LoadSnapshot> result = new ArrayList<>();
        for (String json : jsonList) {
            try {
                List<SnapshotMap> maps = objectMapper.readValue(json, new TypeReference<>() {});
                for (SnapshotMap map : maps) {
                    result.add(fromMap(map));
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize LoadSnapshot", e);
            }
        }
        return result;
    }

    @Override
    public List<LoadSnapshot> getRecentHistory(int days) {
        List<LoadSnapshot> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            result.addAll(getHistory(date));
        }

        return result;
    }

    private SnapshotMap toMap(LoadSnapshot snapshot) {
        SnapshotMap map = new SnapshotMap();
        map.timestamp = snapshot.timestamp().toString();
        map.totalScore = snapshot.totalScore();
        map.level = snapshot.level().name();
        map.cpuUsage = snapshot.cpuUsage();
        map.memoryUsage = snapshot.memoryUsage();
        map.qps = snapshot.qps();
        map.p50Latency = snapshot.p50Latency();
        map.p99Latency = snapshot.p99Latency();
        map.connectionPoolUsage = snapshot.connectionPoolUsage();
        return map;
    }

    private LoadSnapshot fromMap(SnapshotMap map) {
        return LoadSnapshot.builder()
                .timestamp(java.time.Instant.parse(map.timestamp))
                .totalScore(map.totalScore)
                .level(com.wewins.fota.domain.load.model.enums.LoadLevel.valueOf(map.level))
                .cpuUsage(map.cpuUsage)
                .memoryUsage(map.memoryUsage)
                .qps(map.qps)
                .p50Latency(map.p50Latency)
                .p99Latency(map.p99Latency)
                .connectionPoolUsage(map.connectionPoolUsage)
                .build();
    }

    private static class SnapshotMap {
        public String timestamp;
        public int totalScore;
        public String level;
        public double cpuUsage;
        public double memoryUsage;
        public double qps;
        public double p50Latency;
        public double p99Latency;
        public double connectionPoolUsage;
    }
}
