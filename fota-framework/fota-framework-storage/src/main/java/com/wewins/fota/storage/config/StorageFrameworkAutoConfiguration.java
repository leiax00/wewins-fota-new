package com.wewins.fota.storage.config;

import com.wewins.fota.storage.core.DefaultFileTransferService;
import com.wewins.fota.storage.core.FileTransferService;
import com.wewins.fota.storage.core.LocalStorageClient;
import com.wewins.fota.storage.core.S3StorageClient;
import com.wewins.fota.storage.core.StorageClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Object storage auto configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageFrameworkAutoConfiguration {

    @Bean
    public LocalStorageClient localStorageClient(StorageProperties properties) {
        Path baseDir = Path.of(properties.getLocal().getBaseDir());
        ensureDirectory(baseDir);
        ensureDirectory(resolveLocalTempDir(properties));
        return new LocalStorageClient(baseDir);
    }

    @Bean("storageClient")
    @ConditionalOnMissingBean(name = "storageClient")
    @Qualifier("storageClient")
    public StorageClient storageClient(
            LocalStorageClient localStorageClient,
            ObjectProvider<S3StorageClient> s3StorageClientProvider
    ) {
        // Storage policy:
        // 1) local storage is always available (also used for staging files)
        // 2) S3 storage is optional and enabled by config
        // 3) if S3 exists, final firmware objects go to S3; otherwise stay local
        S3StorageClient s3StorageClient = s3StorageClientProvider.getIfAvailable();
        return s3StorageClient != null ? s3StorageClient : localStorageClient;
    }

    @Bean("storageLocalTempDir")
    public Path storageLocalTempDir(StorageProperties properties) {
        Path tempDir = resolveLocalTempDir(properties);
        ensureDirectory(tempDir);
        return tempDir;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        if (!StringUtils.hasText(s3.getBucket())) {
            throw new IllegalStateException("app.storage.s3.bucket is required when storage type is s3");
        }
        var builder = S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccessEnabled())
                        .build());
        if (StringUtils.hasText(s3.getEndpoint())) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccessEnabled())
                        .build());
        if (StringUtils.hasText(s3.getEndpoint())) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    public S3StorageClient s3StorageClient(
            StorageProperties properties,
            S3Client s3Client,
            S3Presigner s3Presigner
    ) {
        return new S3StorageClient(s3Client, s3Presigner, properties.getS3().getBucket());
    }

    @Bean
    @ConditionalOnMissingBean(name = "storageDefaultTtl")
    public Duration storageDefaultTtl(StorageProperties properties) {
        return Duration.ofSeconds(properties.getDefaultTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean(FileTransferService.class)
    public FileTransferService fileTransferService(
            @Qualifier("storageLocalTempDir") Path storageLocalTempDir,
            @Qualifier("storageClient") StorageClient storageClient
    ) {
        return new DefaultFileTransferService(storageLocalTempDir, storageClient);
    }

    private AwsCredentialsProvider resolveCredentialsProvider(StorageProperties.S3 s3) {
        if (StringUtils.hasText(s3.getAccessKey()) && StringUtils.hasText(s3.getSecretKey())) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey());
            return StaticCredentialsProvider.create(credentials);
        }
        return DefaultCredentialsProvider.create();
    }

    private Path resolveLocalTempDir(StorageProperties properties) {
        return Path.of(properties.getLocal().getBaseDir()).resolve("tmp");
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception ex) {
            throw new IllegalStateException("Create storage directory failed: " + path, ex);
        }
    }
}
