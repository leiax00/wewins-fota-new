package com.wewins.fota.adapter.api.internal;

import com.wewins.fota.infra.registry.NodeInstance;
import com.wewins.fota.infra.registry.NodeRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.mode", havingValue = "main")
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
