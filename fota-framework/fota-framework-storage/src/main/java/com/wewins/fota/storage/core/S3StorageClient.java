package com.wewins.fota.storage.core;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.awscore.client.config.AwsClientOption;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * S3-compatible implementation of storage client.
 */
@RequiredArgsConstructor
public class S3StorageClient implements StorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    @Override
    public String upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
        return "s3://" + bucket + "/" + objectKey;
    }

    @Override
    public String getDownloadUrl(String objectKey, Duration ttl) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public String getDownloadUrl(String objectKey, Duration ttl, Map<String, String> customParams) {
        if (customParams == null || customParams.isEmpty()) {
            return getDownloadUrl(objectKey, ttl);
        }

        // 构建带自定义查询参数的请求
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey);

        // 添加自定义查询参数到 overrideConfiguration
        SdkClientConfiguration.Builder overrideBuilder = SdkClientConfiguration.builder();

        // 使用 request builder 的 overrideConfiguration 添加原始查询参数
        GetObjectRequest request = requestBuilder
                .overrideConfiguration(override -> {
                    for (Map.Entry<String, String> entry : customParams.entrySet()) {
                        override.putRawQueryParameter(entry.getKey(), entry.getValue());
                    }
                })
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build());
    }
}
