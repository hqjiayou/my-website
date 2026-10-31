package com.mywebsite.infra.service.impl;

import com.mywebsite.infra.service.ObjectStorageService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class ObjectStorageServiceImpl implements ObjectStorageService {

    private final MinioClient minioClient;

    public ObjectStorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void put(String bucket, String objectKey, InputStream data, long size, String contentType) {
        Assert.hasText(bucket, "bucket must not be blank");
        Assert.hasText(objectKey, "objectKey must not be blank");
        Assert.notNull(data, "data must not be null");
        Assert.isTrue(size >= 0, "size must be non-negative");
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(data, size, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to put object: " + objectKey, ex);
        }
    }

    @Override
    public byte[] get(String bucket, String objectKey) {
        Assert.hasText(bucket, "bucket must not be blank");
        Assert.hasText(objectKey, "objectKey must not be blank");
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to get object: " + objectKey, ex);
        }
    }

    @Override
    public void remove(String bucket, String objectKey) {
        Assert.hasText(bucket, "bucket must not be blank");
        Assert.hasText(objectKey, "objectKey must not be blank");
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to remove object: " + objectKey, ex);
        }
    }

    @Override
    public String presignedGet(String bucket, String objectKey, Duration ttl) {
        Assert.hasText(bucket, "bucket must not be blank");
        Assert.hasText(objectKey, "objectKey must not be blank");
        Assert.notNull(ttl, "ttl must not be null");
        Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl must be positive");
        try {
            int expirySeconds = (int) Math.min(ttl.getSeconds(), TimeUnit.DAYS.toSeconds(7));
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to presign object: " + objectKey, ex);
        }
    }
}
