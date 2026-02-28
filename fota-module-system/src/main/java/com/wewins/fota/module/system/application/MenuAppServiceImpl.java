package com.wewins.fota.module.system.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.repository.rbac.PermissionRepository;
import com.wewins.fota.module.system.dto.menu.ExternalLinkDTO;
import com.wewins.fota.module.system.dto.menu.RouteMetaDTO;
import com.wewins.fota.module.system.dto.menu.UserMenuNodeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单应用服务实现
 */
@Slf4j
@Service
public class MenuAppServiceImpl implements MenuAppService {

    private static final String TYPE_MODULE = "MODULE";
    private static final String TYPE_MENU = "MENU";

    // 缓存相关常量
    private static final String CACHE_KEY_PREFIX = "user:menu:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5); // 5分钟缓存

    private final PermissionRepository permissionRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public MenuAppServiceImpl(PermissionRepository permissionRepository,
                              ObjectMapper objectMapper,
                              RedisTemplate<String, Object> redisTemplate,
                              StringRedisTemplate stringRedisTemplate) {
        this.permissionRepository = permissionRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<UserMenuNodeDTO> getUserMenuTree(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询用户菜单树: userId={}", userId);
        }

        // 1. 先从缓存读取（使用 StringRedisTemplate 绕过自动反序列化）
        String cacheKey = CACHE_KEY_PREFIX + userId;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("菜单缓存命中: userId={}", userId);
                }
                return parseCachedMenus(cachedJson);
            } catch (Exception e) {
                log.warn("菜单缓存解析失败，执行自愈删除: userId={}, rawLength={}",
                        userId, cachedJson.length(), e);
                // 读取失败则删除坏缓存并回源重建（delete 失败不影响回源）
                try {
                    stringRedisTemplate.delete(cacheKey);
                    if (log.isDebugEnabled()) {
                        log.debug("菜单坏缓存已删除: userId={}", userId);
                    }
                } catch (Exception deleteEx) {
                    log.warn("删除坏缓存失败: userId={}", userId, deleteEx);
                }
            }
        }

        // 2. 查询用户直接拥有的菜单权限（MODULE/MENU）
        // 业务约定：选中子节点必定选中父节点，所以只需要查询用户直接拥有的权限
        log.info("菜单缓存未命中，从数据库查询: userId={}", userId);
        List<Permission> menuPermissions = permissionRepository.findMenuPermissionsByUserId(userId);

        if (menuPermissions == null || menuPermissions.isEmpty()) {
            log.warn("用户没有任何菜单权限: userId={}", userId);
            return List.of();
        }

        if (log.isDebugEnabled()) {
            log.debug("查询到的菜单权限数: userId={}, count={}", userId, menuPermissions.size());
            menuPermissions.forEach(p -> log.debug("菜单权限: id={}, code={}, type={}, parentId={}",
                p.getId(), p.getCode(), p.getType(), p.getParentId()));
        }

        // 3. 在内存中根据 parentId 组织成树
        Map<Long, List<Permission>> childrenByParent = menuPermissions.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));

        // 4. 找出根节点（parentId 为 null）
        List<Permission> roots = menuPermissions.stream()
                .filter(p -> p.getParentId() == null)
                .sorted(menuComparator())
                .toList();

        log.info("根节点数量: {}, ids={}", roots.size(),
            roots.stream().map(p -> p.getId() + ":" + p.getCode()).toList());

        // 5. 递归构建菜单树（使用 flatMap 展开需要折叠的 MODULE）
        List<UserMenuNodeDTO> result = roots.stream()
                .flatMap(root -> convertToMenuNodes(root, childrenByParent).stream())
                .toList();

        if (log.isDebugEnabled()) {
            log.debug("用户菜单树构建完成: userId={}, menuCount={}, resultCount={}",
                    userId, menuPermissions.size(), result.size());
        }

        // 6. 写入缓存（统一使用 StringRedisTemplate + 手动 JSON 序列化）
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            if (log.isDebugEnabled()) {
                log.debug("菜单已缓存: userId={}, ttl={}分钟", userId, CACHE_TTL.toMinutes());
            }
        } catch (Exception e) {
            log.warn("菜单缓存写入失败: userId={}", userId, e);
        }

        return result;
    }

    @Override
    public void evictUserMenuCache(Long userId) {
        if (userId == null) {
            return;
        }

        String cacheKey = CACHE_KEY_PREFIX + userId;
        Boolean deleted = stringRedisTemplate.delete(cacheKey);

        if (log.isDebugEnabled()) {
            log.debug("清除用户菜单缓存: userId={}, deleted={}", userId, deleted);
        }
    }

    @Override
    public void evictAllUserMenusCache() {
        // 使用 Redis 的 keys 命令查找所有菜单缓存键，然后批量删除
        Set<String> keys = stringRedisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            Long deletedCount = stringRedisTemplate.delete(keys);
            log.info("清除所有用户菜单缓存: count={}", deletedCount);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("没有找到需要清除的菜单缓存");
            }
        }
    }

    /**
     * 兼容读取菜单缓存：
     * 1) 纯数组：[{...},{...}]
     * 2) GenericJackson2JsonRedisSerializer 包装数组：["java.util.ArrayList",[{...}]]
     * 3) 嵌套字符串："[{...}]"
     *
     * @param cachedJson Redis 原始字符串
     * @return 菜单节点列表
     * @throws Exception 解析失败时抛出异常
     */
    private List<UserMenuNodeDTO> parseCachedMenus(String cachedJson) throws Exception {
        JsonNode root = objectMapper.readTree(cachedJson);
        if (root == null || root.isNull()) {
            return List.of();
        }

        // 历史数据：最外层被序列化成 JSON 字符串
        if (root.isTextual()) {
            String innerJson = root.asText();
            if (innerJson == null || innerJson.isBlank()) {
                return List.of();
            }
            root = objectMapper.readTree(innerJson);
            if (log.isDebugEnabled()) {
                log.debug("检测到嵌套字符串缓存，已解包: length={}", innerJson.length());
            }
        }

        JsonNode dataNode = root;
        // GenericJackson2JsonRedisSerializer 可能使用 [typeId, data] 包装
        if (root.isArray()
                && root.size() == 2
                && root.get(0).isTextual()
                && root.get(1).isArray()) {
            dataNode = root.get(1);
            if (log.isDebugEnabled()) {
                log.debug("检测到类型包装缓存，已兼容解析: typeId={}", root.get(0).asText());
            }
        }

        if (dataNode.isArray()) {
            List<UserMenuNodeDTO> result = new ArrayList<>(dataNode.size());
            for (JsonNode item : dataNode) {
                result.add(objectMapper.convertValue(item, UserMenuNodeDTO.class));
            }
            if (log.isDebugEnabled()) {
                log.debug("菜单缓存解析成功: count={}", result.size());
            }
            return result;
        }

        if (dataNode.isObject()) {
            // 兼容极端情况：单对象缓存
            return List.of(objectMapper.convertValue(dataNode, UserMenuNodeDTO.class));
        }

        throw new IllegalStateException("unexpected cache json node type: " + dataNode.getNodeType());
    }

    /**
     * 递归构建菜单节点（支持折叠 MODULE）
     *
     * @param current 当前权限节点
     * @param childrenByParent 父子关系映射
     * @return 菜单节点列表（如果 MODULE 需要折叠，返回子节点列表；否则返回包含当前节点的单元素列表）
     */
    private List<UserMenuNodeDTO> convertToMenuNodes(Permission current,
                                                     Map<Long, List<Permission>> childrenByParent) {
        // 获取子节点
        List<Permission> children = childrenByParent.getOrDefault(current.getId(), List.of()).stream()
                .sorted(menuComparator())
                .toList();

        // 递归构建子菜单
        List<UserMenuNodeDTO> childDtos = children.stream()
                .flatMap(child -> convertToMenuNodes(child, childrenByParent).stream())
                .toList();

        // 判断是否需要折叠 MODULE：hidden=true 且 alwaysShow!=true
        if (isModule(current) && shouldFlattenModule(current)) {
            log.info("折叠 MODULE 节点: code={}, 子菜单数量={}", current.getCode(), childDtos.size());
            // 返回子节点列表，而不是包含当前节点的单元素列表
            return childDtos;
        }

        // 正常构建当前节点
        UserMenuNodeDTO node = UserMenuNodeDTO.builder()
                .path(current.getRoutePath())
                .name(current.getRouteName())
                .componentKey(current.getComponentKey())
                .redirect(current.getRedirectPath())
                .sort(current.getMenuSort())
                .meta(buildMeta(current))
                .children(childDtos)
                .build();

        return List.of(node);
    }

    /**
     * 判断是否为 MODULE 类型
     */
    private boolean isModule(Permission p) {
        return TYPE_MODULE.equalsIgnoreCase(p.getType());
    }

    /**
     * 判断 MODULE 是否需要折叠（子菜单上提）
     * 规则：hidden=true 且 alwaysShow!=true
     */
    private boolean shouldFlattenModule(Permission p) {
        if (!isModule(p)) {
            return false;
        }
        JsonNode cfg = readMenuConfig(p.getMenuConfig());
        boolean hidden = readBoolean(cfg, "hidden", false);
        boolean alwaysShow = readBoolean(cfg, "alwaysShow", false);
        return hidden && !alwaysShow;
    }

    private RouteMetaDTO buildMeta(Permission permission) {
        JsonNode configNode = readMenuConfig(permission.getMenuConfig());

        ExternalLinkDTO externalLink = null;
        if (permission.getExternalLink() != null && !permission.getExternalLink().isBlank()) {
            externalLink = ExternalLinkDTO.builder()
                    .url(permission.getExternalLink())
                    .openMode(readText(configNode, "openMode", "_blank"))
                    .build();
        }

        return RouteMetaDTO.builder()
                .i18nKey(readText(configNode, "i18nKey", permission.getCode()))
                .icon(permission.getIcon())
                .hidden(readBoolean(configNode, "hidden", false))
                .keepAlive(readBoolean(configNode, "keepAlive", false))
                .affix(readBoolean(configNode, "affix", false))
                .alwaysShow(readBoolean(configNode, "alwaysShow", false))
                .breadcrumbHidden(readBoolean(configNode, "breadcrumbHidden", false))
                .tabHidden(readBoolean(configNode, "tabHidden", false))
                .tabClosable(readBoolean(configNode, "tabClosable", true))
                .activeMenu(readText(configNode, "activeMenu", null))
                .permission(permission.getCode())
                .externalLink(externalLink)
                .build();
    }

    private JsonNode readMenuConfig(String menuConfig) {
        if (menuConfig == null || menuConfig.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(menuConfig);
        } catch (Exception e) {
            log.warn("解析 menuConfig 失败: menuConfig={}", menuConfig);
            return null;
        }
    }

    private boolean readBoolean(JsonNode node, String field, boolean defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asBoolean(defaultValue);
    }

    private String readText(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        String value = node.get(field).asText();
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private Comparator<Permission> menuComparator() {
        return Comparator
                .comparing((Permission p) -> p.getMenuSort() == null ? Integer.MAX_VALUE : p.getMenuSort())
                .thenComparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId());
    }
}
