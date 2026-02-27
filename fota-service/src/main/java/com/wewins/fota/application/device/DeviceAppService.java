package com.wewins.fota.application.device;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.device.dto.BatchOperationReqDTO;
import com.wewins.fota.application.device.dto.BatchOperationResultDTO;
import com.wewins.fota.application.device.dto.DeviceImportRespDTO;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.domain.device.entity.Device;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 设备应用服务接口
 */
public interface DeviceAppService {

    /**
     * 分页查询设备列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    Page<Device> pageDevices(DevicePageReqDTO reqDTO);

    /**
     * 根据 ID 获取设备
     *
     * @param id 设备 ID
     * @return 设备实体
     */
    Device getById(Long id);

    /**
     * 创建设备
     *
     * @param device 设备实体
     * @return 创建后的设备
     */
    Device createDevice(Device device);

    /**
     * 更新设备
     *
     * @param device 设备实体
     * @return 更新后的设备
     */
    Device updateDevice(Device device);

    /**
     * 删除设备（逻辑删除）
     *
     * @param id 设备 ID
     * @return 是否成功
     */
    boolean deleteDevice(Long id);

    /**
     * 批量导入设备
     *
     * @param file 导入文件（Excel或TXT）
     * @param productId 产品ID
     * @param batchName 批次名称（可选）
     * @return 导入结果
     * @throws IOException 文件读取失败
     */
    DeviceImportRespDTO importDevices(MultipartFile file, Long productId, String batchName) throws IOException;

    /**
     * 预估批量操作影响的设备数
     *
     * @param reqDTO 批量操作请求参数
     * @return 影响的设备数
     */
    int estimateBatchOperation(BatchOperationReqDTO reqDTO);

    /**
     * 执行批量操作
     *
     * @param reqDTO 批量操作请求参数
     * @return 操作结果
     */
    BatchOperationResultDTO executeBatchOperation(BatchOperationReqDTO reqDTO);
}
