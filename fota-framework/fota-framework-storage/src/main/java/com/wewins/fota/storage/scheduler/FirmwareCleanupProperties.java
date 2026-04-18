package com.wewins.fota.storage.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 固件临时文件清理任务配置属性。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.firmware.upload.cleanup")
public class FirmwareCleanupProperties {

    /**
     * 是否启用清理任务。
     */
    private boolean enabled = true;

    /**
     * 临时文件目录。
     */
    private Path tempDir = Path.of("data/storage/tmp");

    /**
     * 文件最大保留时间。
     * <p>
     * 超过此时间的临时文件将被清理。
     * </p>
     */
    private Duration maxFileAge = Duration.ofHours(2);
}
