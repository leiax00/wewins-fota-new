package com.wewins.fota.application.upgrade;

import com.wewins.fota.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 升级请求参数校验器
 * <p>
 * 负责校验升级检查请求的参数有效性
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
public class UpgradeRequestValidator {

    /**
     * IMEI 格式正则表达式：15 位数字
     */
    private static final String IMEI_REGEX = "\\d{15}";

    /**
     * 校验产品型号
     *
     * @param productModel 产品型号
     * @throws BizException 如果产品型号为空
     */
    public void validateProductModel(String productModel) {
        if (!StringUtils.hasText(productModel)) {
            throw new BizException(400, "product 参数不能为空");
        }
    }

    /**
     * 校验 IMEI
     * <p>
     * 校验规则：
     * </p>
     * <ul>
     *   <li>不能为空</li>
     *   <li>必须是 15 位数字</li>
     * </ul>
     *
     * @param imei 设备 IMEI
     * @throws BizException 如果 IMEI 为空或格式错误
     */
    public void validateImei(String imei) {
        if (!StringUtils.hasText(imei)) {
            throw new BizException(400, "imei 参数不能为空");
        }

        if (!imei.matches(IMEI_REGEX)) {
            throw new BizException(400, "imei 格式错误：必须是15位数字");
        }
    }

    /**
     * 校验版本号
     *
     * @param version 版本号
     * @throws BizException 如果版本号为空
     */
    public void validateVersion(String version) {
        if (!StringUtils.hasText(version)) {
            throw new BizException(400, "version 参数不能为空");
        }
    }

    /**
     * 校验灰度比例
     * <p>
     * 校验规则：必须在 [0, 100] 范围内
     * </p>
     *
     * @param grayRate 灰度比例
     * @throws BizException 如果灰度比例超出范围
     */
    public void validateGrayRate(Integer grayRate) {
        if (grayRate != null && (grayRate < 0 || grayRate > 100)) {
            throw new BizException(400, "grayRate 参数必须在 [0, 100] 范围内");
        }
    }

    /**
     * 校验自动检查标识
     * <p>
     * 校验规则：只能是 0（手动）或 1（自动）
     * </p>
     *
     * @param auto 自动检查标识
     * @throws BizException 如果自动检查标识超出范围
     */
    public void validateAuto(Integer auto) {
        if (auto != null && (auto < 0 || auto > 1)) {
            throw new BizException(400, "auto 参数只能是 0（手动检查）或 1（自动检查）");
        }
    }

    /**
     * 完整校验升级检查请求参数
     * <p>
     * 校验必填参数：productModel, imei, version
     * </p>
     *
     * @param productModel 产品型号
     * @param imei         设备 IMEI
     * @param version      版本号
     * @throws BizException 如果任一必填参数无效
     */
    public void validateRequiredParams(String productModel, String imei, String version) {
        validateProductModel(productModel);
        validateImei(imei);
        validateVersion(version);
    }

    /**
     * 完整校验升级检查请求参数（带可选参数）
     *
     * @param productModel 产品型号
     * @param imei         设备 IMEI
     * @param version      版本号
     * @param auto         自动检查标识（可选）
     * @throws BizException 如果任一参数无效
     */
    public void validateAllParams(String productModel, String imei, String version, Integer auto) {
        validateRequiredParams(productModel, imei, version);
        validateAuto(auto);
    }
}
