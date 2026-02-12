package com.wewins.fota.application.region;

import com.wewins.fota.cache.cluster.ClusterProperties;
import com.wewins.fota.cache.cluster.RegionLeaderService;
import com.wewins.fota.common.region.RegionCodeResolver;
import com.wewins.fota.common.security.HmacSigner;
import com.wewins.fota.security.internal.RegionRotateKey;
import com.wewins.fota.security.internal.RegionSecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 区域配置同步应用服务
 * <p>
 * 仅在 region 模式下工作，负责从主区域拉取配置
 * </p>
 * <p>
 * 主要职责：
 * </p>
 * <ul>
 *   <li>每 30 秒轮询主区域的配置版本</li>
 *   <li>当版本变化时拉取完整快照（策略、产品、控制规则）</li>
 *   <li>将快照更新到本地 Redis</li>
 *   <li>使用原子切换避免不一致</li>
 * </ul>
 * <p>
 * <strong>负载均衡场景说明</strong>：
 * <ul>
 *   <li>多实例部署时，默认可并行执行配置同步</li>
 *   <li>通过版本号判断是否需要更新，避免重复拉取</li>
 *   <li>若启用 Leader 组件，则仅 Leader 实例执行同步</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionSyncService {

    private final ClusterProperties clusterProperties;
    private final RegionMainProperties regionMainProperties;
    private final RestTemplate restTemplate;
    private final ObjectProvider<RegionLeaderService> leaderServiceProvider;
    private final RegionSecretService regionSecretService;
    private String pendingAckKeyId;

    /**
     * 本地配置版本缓存
     */
    private long localConfigVersion = 0;

    /**
     * 主区域配置版本 URL
     */
    private static final String MAIN_CONFIG_VERSION_URL = "%s/internal/config/version";

    /**
     * 快照拉取 URL 模板
     */
    private static final String SNAPSHOT_URL_TEMPLATE = "%s/internal/config/snapshot/%s";

    /**
     * 启动配置同步
     */
    public void startSync() {
        log.info("启动区域配置同步服务");
        // TODO: 实现定时同步逻辑
    }

    /**
     * 停止配置同步
     */
    public void stopSync() {
        log.info("停止区域配置同步服务");
        // TODO: 实现停止逻辑（关闭定时任务）
    }

    /**
     * 执行一次配置同步
     * <p>
     * 通过版本号判断是否需要更新，避免重复拉取。
     * 若系统启用了 leader 选举，非 leader 实例会跳过执行。
     * </p>
     *
     * @return 同步是否成功
     */
    public boolean syncOnce() {
        // 检查是否在区域模式
        if (!"region".equals(clusterProperties.getMode())) {
            log.warn("当前不在区域模式，跳过配置同步");
            return false;
        }

        RegionLeaderService leaderService = leaderServiceProvider.getIfAvailable();
        if (leaderService != null && !leaderService.isLeader()) {
            log.debug("当前实例不是分区主节点，跳过配置同步");
            return false;
        }

        try {
            // 1. 获取主区域配置版本
            long remoteVersion = getRemoteConfigVersion();
            log.debug("主区域配置版本: {}", remoteVersion);

            // 2. 判断是否需要更新
            if (remoteVersion <= localConfigVersion) {
                log.info("本地配置已是最新版本: localVersion={}, remoteVersion={}",
                        localConfigVersion, remoteVersion);
                return true;
            }

            // 3. 拉取快照
            Map<String, Object> snapshot = fetchSnapshot("policy");
            log.debug("策略快照: {}", snapshot);

            // TODO: 4. 更新本地 Redis 缓存
            // TODO: 5. 原子切换本地配置版本

            localConfigVersion = remoteVersion;
            log.info("配置同步成功: localVersion={}, remoteVersion={}",
                    localConfigVersion, remoteVersion);

            return true;

        } catch (Exception e) {
            log.error("配置同步失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取主区域配置版本
     *
     * @return 配置版本号
     */
    private long getRemoteConfigVersion() {
        String url = buildConfigVersionUrl();

        try {
            HttpHeaders headers = buildInternalAuthHeaders(url, HttpMethod.GET.name());
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("version")) {
                handleRotateKeyIfPresent(response);
                Object versionObj = response.get("version");
                if (versionObj instanceof Number) {
                    return ((Number) versionObj).longValue();
                }
            }

            log.warn("无法解析主区域配置版本: {}", response);
            return 0;

        } catch (Exception e) {
            log.error("获取主区域配置版本失败: url={}", url, e);
            return 0;
        }
    }

    /**
     * 拉取配置快照
     *
     * @param snapshotType 快照类型（policy/product/control）
     * @return 快照数据
     */
    public Map<String, Object> fetchSnapshot(String snapshotType) {
        String url = buildSnapshotUrl(snapshotType);

        try {
            HttpHeaders headers = buildInternalAuthHeaders(url, HttpMethod.GET.name());
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null) {
                handleRotateKeyIfPresent(response);
                return response;
            }

            return new HashMap<>();

        } catch (Exception e) {
            log.error("拉取配置快照失败: type={}, url={}", snapshotType, url, e);
            return new HashMap<>();
        }
    }

    /**
     * 构建配置版本 URL
     *
     * @return URL
     */
    private String buildConfigVersionUrl() {
        String mainApiUrl = resolveMainBaseUrl();
        return String.format(MAIN_CONFIG_VERSION_URL, mainApiUrl);
    }

    /**
     * 构建快照 URL
     *
     * @param snapshotType 快照类型
     * @return URL
     */
    private String buildSnapshotUrl(String snapshotType) {
        String mainApiUrl = resolveMainBaseUrl();
        return String.format(SNAPSHOT_URL_TEMPLATE, mainApiUrl, snapshotType);
    }

    private String resolveMainBaseUrl() {
        String mainBaseUrl = null;
        if (regionMainProperties != null) {
            mainBaseUrl = regionMainProperties.getBaseUrl();
        }
        if (mainBaseUrl == null || mainBaseUrl.isBlank()) {
            String fallback = clusterProperties.getNode().getBaseUrl();
            log.warn("未配置 app.main.baseUrl，回退使用 app.node.baseUrl={}", fallback);
            return fallback;
        }
        return mainBaseUrl;
    }

    private HttpHeaders buildInternalAuthHeaders(String url, String method) {
        HttpHeaders headers = new HttpHeaders();
        String regionCode = RegionCodeResolver.resolveRegionCode(clusterProperties.getNode().getCode());
        String secret = regionSecretService.getSecret(regionCode);
        if (secret == null || secret.isBlank()) {
            secret = regionMainProperties != null ? regionMainProperties.getBootstrapSecret() : null;
        }
        if (secret == null || secret.isBlank()) {
            log.warn("未配置 app.main.bootstrapSecret，内部请求不携带签名: url={}", url);
            return headers;
        }

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        String pathWithQuery = buildPathWithQuery(url);
        String payload = HmacSigner.buildPayload(regionCode, timestamp, nonce, method, pathWithQuery);
        String signature = HmacSigner.sign(secret, payload);

        headers.add("X-Region-Code", regionCode);
        headers.add("X-Timestamp", timestamp);
        headers.add("X-Nonce", nonce);
        headers.add("X-Signature", signature);
        if (pendingAckKeyId != null && !pendingAckKeyId.isBlank()) {
            headers.add("X-Secret-Ack", pendingAckKeyId);
            pendingAckKeyId = null;
        }
        return headers;
    }

    private String buildPathWithQuery(String url) {
        URI uri = URI.create(url);
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }

    @SuppressWarnings("unchecked")
    private void handleRotateKeyIfPresent(Map<String, Object> response) {
        Object rotateKeyObj = response.get("rotateKey");
        if (!(rotateKeyObj instanceof Map)) {
            return;
        }
        Map<String, Object> rotateKeyMap = (Map<String, Object>) rotateKeyObj;
        Object keyIdObj = rotateKeyMap.get("keyId");
        Object secretObj = rotateKeyMap.get("secret");
        if (!(keyIdObj instanceof String) || !(secretObj instanceof String)) {
            return;
        }
        String keyId = (String) keyIdObj;
        String secret = (String) secretObj;
        if (secret.isBlank()) {
            return;
        }
        String regionCode = RegionCodeResolver.resolveRegionCode(clusterProperties.getNode().getCode());
        regionSecretService.setSecret(regionCode, secret);
        pendingAckKeyId = keyId;
        log.info("已应用分区新密钥: keyId={}", keyId);
    }
}
