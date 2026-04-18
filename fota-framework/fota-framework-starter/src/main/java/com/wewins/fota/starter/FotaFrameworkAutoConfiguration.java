package com.wewins.fota.starter;

import com.wewins.fota.cdn.CdnWarmMarker;
import com.wewins.fota.cache.FotaCacheMarker;
import com.wewins.fota.common.FotaCommonMarker;
import com.wewins.fota.database.FotaDatabaseMarker;
import com.wewins.fota.mq.FotaMqMarker;
import com.wewins.fota.scheduler.FotaSchedulerMarker;
import com.wewins.fota.scheduler.config.SchedulerAutoConfiguration;
import com.wewins.fota.security.FotaSecurityMarker;
import com.wewins.fota.storage.FotaStorageMarker;
import com.wewins.fota.task.FotaTaskMarker;
import com.wewins.fota.task.config.TaskAutoConfiguration;
import com.wewins.fota.web.config.RestTemplateConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * FOTA framework auto-configuration entry.
 *
 * <p>Scans framework modules and enables their default infrastructure beans
 * when {@code fota-framework-starter} is on the classpath.
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = {
        FotaCommonMarker.class,
        FotaCacheMarker.class,
        FotaDatabaseMarker.class,
        FotaMqMarker.class,
        FotaStorageMarker.class,
        FotaSecurityMarker.class,
        CdnWarmMarker.class,
        FotaTaskMarker.class,
        FotaSchedulerMarker.class,
        RestTemplateConfiguration.class,
        TaskAutoConfiguration.class,
        SchedulerAutoConfiguration.class
})
public class FotaFrameworkAutoConfiguration {
}
