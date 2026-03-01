package com.wewins.fota.application.policy.dto;

import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.enums.TargetMode;
import com.wewins.fota.domain.policy.enums.TriggerMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 升级策略创建/更新请求 DTO
 */
@Data
public class UpgradePolicyReqDTO {

    /**
     * 关联产品 ID
     */
    @NotNull(message = "产品 ID 不能为空")
    private Long productId;

    /**
     * 目标固件版本 ID
     */
    @NotNull(message = "目标固件版本 ID 不能为空")
    private Long firmwareVersionId;

    /**
     * 策略名称
     */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 100, message = "策略名称不能超过 100 字符")
    private String name;

    /**
     * 灰度比例（0-100）
     */
    @Min(value = 0, message = "灰度比例不能小于 0")
    @Max(value = 100, message = "灰度比例不能大于 100")
    private Integer grayRate;

    /**
     * 优先级（数值越大优先级越高）
     */
    @Min(value = 0, message = "priority 不能小于 0")
    private Integer priority;

    /**
     * 触发模式：AUTO / MANUAL
     */
    private String triggerMode;

    /**
     * 获取触发模式枚举
     */
    public TriggerMode getTriggerModeEnum() {
        return TriggerMode.of(triggerMode);
    }

    /**
     * 时间窗口配置
     */
    @Valid
    private TimeWindowDTO timeWindow;

    /**
     * 允许升级的源版本 ID 列表
     * <p>
     * 必须非空，至少包含一个版本 ID
     * </p>
     */
    @NotNull(message = "sourceVersions 不能为空")
    @Size(min = 1, max = 50, message = "sourceVersions 数量必须在 1-50 之间")
    private List<@NotNull(message = "sourceVersions 不允许包含 null") Long> sourceVersions;

    /**
     * 目标设备模式：ALL / DEVICE_IDS / DEVICE_BATCHES / DEVICE_TAGS
     * <p>
     * 目标模式互斥，只能选择一种：
     * <ul>
     *   <li>ALL: 全量设备</li>
     *   <li>DEVICE_IDS: 指定设备IMEI列表</li>
     *   <li>DEVICE_BATCHES: 指定设备批次列表</li>
     *   <li>DEVICE_TAGS: 按标签筛选（AND 逻辑）</li>
     * </ul>
     * </p>
     */
    private String targetMode;

    /**
     * 获取目标模式枚举
     */
    public TargetMode getTargetModeEnum() {
        return TargetMode.of(targetMode);
    }

    /**
     * 目标设备 IMEI 列表
     * <p>
     * 当 targetMode = DEVICE_IDS 时使用
     * </p>
     */
    @Size(max = 1000, message = "targetImeis 数量不能超过 1000")
    private List<@NotBlank(message = "targetImeis 不允许包含空字符串") String> targetImeis;

    /**
     * 目标设备批次 ID 列表
     * <p>
     * 当 targetMode = DEVICE_BATCHES 时使用
     * </p>
     */
    @Size(max = 100, message = "targetDeviceBatchIds 数量不能超过 100")
    private List<@NotBlank(message = "targetDeviceBatchIds 不允许包含空字符串") String> targetDeviceBatchIds;

    /**
     * 目标设备标签条件（AND 逻辑）
     * <p>
     * 当 targetMode = DEVICE_TAGS 时使用
     * 结构与 Device.tags 保持一致：JSON 对象 KV 匹配
     * </p>
     */
    private Map<String, Object> targetDeviceTags;

    /**
     * 策略状态
     */
    private PolicyStatus status;

    /**
     * 备注
     */
    private String remark;
}
