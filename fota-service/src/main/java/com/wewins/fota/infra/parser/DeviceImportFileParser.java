package com.wewins.fota.infra.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 设备导入文件解析器接口
 * <p>
 * 用于解析包含设备IMEI列表的文件（Excel、TXT等）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-27
 */
public interface DeviceImportFileParser {

    /**
     * 解析文件，提取IMEI列表
     * <p>
     * 对于Excel文件：查找第一行中名为"IMEI"的列（不区分大小写），提取该列所有非空值
     * 对于TXT文件：逐行读取，过滤空行和空白字符
     * </p>
     *
     * @param inputStream 文件输入流
     * @return IMEI列表（已去除空白和空行）
     * @throws IOException 文件读取失败时抛出
     */
    List<String> parse(InputStream inputStream) throws IOException;
}
