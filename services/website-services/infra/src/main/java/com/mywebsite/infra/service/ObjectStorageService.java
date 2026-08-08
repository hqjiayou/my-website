package com.mywebsite.infra.service;

import java.io.InputStream;
import java.time.Duration;

/** 对象存储薄封装：仅 IO，无站点/权限业务。 */
public interface ObjectStorageService {

    void put(String bucket, String objectKey, InputStream data, long size, String contentType);

    void remove(String bucket, String objectKey);

    String presignedGet(String bucket, String objectKey, Duration ttl);
}
