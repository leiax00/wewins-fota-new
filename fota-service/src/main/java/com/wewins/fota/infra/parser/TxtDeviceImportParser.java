package com.wewins.fota.infra.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT文件设备导入解析器
 * <p>
 * 逐行读取TXT文件，每行作为一个IMEI，过滤空行和空白字符
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-27
 */
@Slf4j
@Component
public class TxtDeviceImportParser implements DeviceImportFileParser {

    @Override
    public List<String> parse(InputStream inputStream) throws IOException {
        List<String> imeis = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();

                // 跳过空行
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                imeis.add(trimmedLine);

                if (log.isDebugEnabled()) {
                    log.debug("解析TXT文件第{}行: IMEI={}", lineNumber, trimmedLine);
                }
            }

            log.info("TXT文件解析完成，共提取{}个IMEI", imeis.size());
        }

        return imeis;
    }
}
