package com.wewins.fota.adapter.api.internal;

import com.wewins.fota.cache.cluster.NodeInstance;
import com.wewins.fota.cache.cluster.NodeRegistryService;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点注册查询接口
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@RestController
@RequestMapping("/internal/registry")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
public class NodeRegistryController {

    private final NodeRegistryService registryService;

    @GetMapping("/nodes")
    public Map<String, Object> listNodes() {
        List<NodeInstance> nodes = registryService.listOnlineNodes();
        Map<String, Object> result = new HashMap<>();
        result.put("count", nodes.size());
        result.put("nodes", nodes);
        result.put("timestamp", Instant.now().toString());
        return result;
    }
}
