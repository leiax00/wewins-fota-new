package com.wewins.fota.cdn.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cdn.application.dto.WarmResult;
import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider.CdnWorkerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CdnWarmWorkerClient {

    private static final String HEADER_WARM_TOKEN = "X-Warm-Token";

    private final CdnWorkerConfigProvider configProvider;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public WarmResult warmViaWorker(String firmwareUrl) {
        CdnWorkerConfig config = configProvider.getConfig();

        String workerUrl = config.workerUrl();
        String warmSecret = config.warmSecret();
        int defaultTtlSeconds = config.defaultTtlSeconds();

        if (!hasText(workerUrl)) {
            log.warn("Worker URL 未配置，无法进行 Worker 预热");
            return WarmResult.builder()
                    .url(firmwareUrl)
                    .strategy(CdnWarmStrategy.WORKER)
                    .status(WarmStatus.FAILED)
                    .errorMessage("Worker URL 未配置")
                    .build();
        }
        if (!hasText(warmSecret)) {
            log.warn("Worker 鉴权密钥未配置，无法进行 Worker 预热");
            return WarmResult.builder()
                    .url(firmwareUrl)
                    .strategy(CdnWarmStrategy.WORKER)
                    .status(WarmStatus.FAILED)
                    .errorMessage("Worker 鉴权密钥未配置")
                    .build();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "firmware_url", firmwareUrl,
                    "ttl", defaultTtlSeconds
            );

            String response = restClientBuilder.build().post()
                    .uri(workerUrl)
                    .header(HEADER_WARM_TOKEN, warmSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            boolean warmed = responseJson.path("warmed").asBoolean(false);
            JsonNode resultNode = responseJson.path("result");
            return WarmResult.builder()
                    .url(firmwareUrl)
                    .strategy(CdnWarmStrategy.WORKER)
                    .status(warmed ? WarmStatus.SUCCESS : WarmStatus.FAILED)
                    .cacheStatus(resultNode.path("cache").asText(null))
                    .pop(readNullableText(resultNode, "pop"))
                    .cfRay(readNullableText(resultNode, "ray"))
                    .errorMessage(warmed ? null : "预热失败")
                    .rawResponse(response)
                    .build();
        } catch (Exception e) {
            log.error("Worker 预热异常: url={}, error={}", firmwareUrl, e.getMessage(), e);
            return WarmResult.builder()
                    .url(firmwareUrl)
                    .strategy(CdnWarmStrategy.WORKER)
                    .status(WarmStatus.FAILED)
                    .errorMessage("预热异常: " + e.getMessage())
                    .build();
        }
    }

    public boolean isWorkerConfigured() {
        CdnWorkerConfig config = configProvider.getConfig();
        return hasText(config.workerUrl()) && hasText(config.warmSecret());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String readNullableText(JsonNode source, String fieldName) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return null;
        }
        String value = source.path(fieldName).asText(null);
        return hasText(value) ? value.trim() : null;
    }
}
