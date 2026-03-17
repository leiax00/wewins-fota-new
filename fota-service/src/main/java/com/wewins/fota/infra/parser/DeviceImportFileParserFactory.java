package com.wewins.fota.infra.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备导入文件解析器工厂
 * <p>
 * 根据文件扩展名返回对应的解析器
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceImportFileParserFactory {

    private final TxtDeviceImportParser txtParser;
    private final ExcelDeviceImportParser excelParser;

    /**
     * 根据文件名获取对应的解析器
     *
     * @param filename 文件名
     * @return 文件解析器
     * @throws IllegalArgumentException 不支持的文件格式
     */
    public DeviceImportFileParser getParser(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerCaseFilename = filename.toLowerCase();

        if (lowerCaseFilename.endsWith(".txt")) {
            log.debug("使用TXT解析器处理文件: {}", filename);
            return txtParser;
        }

        if (lowerCaseFilename.endsWith(".xlsx") || lowerCaseFilename.endsWith(".xls")) {
            log.debug("使用Excel解析器处理文件: {}", filename);
            return excelParser;
        }

        throw new IllegalArgumentException(
                "不支持的文件格式: " + filename + "，仅支持 .xlsx, .xls, .txt 格式"
        );
    }

    /**
     * 获取支持的文件扩展名列表
     *
     * @return 支持的文件扩展名
     */
    public List<String> getSupportedExtensions() {
        return List.of(".xlsx", ".xls", ".txt");
    }
}
