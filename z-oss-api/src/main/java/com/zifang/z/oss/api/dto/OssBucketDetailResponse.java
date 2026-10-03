package com.zifang.z.oss.api.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OSS Bucket 详情响应 DTO.
 * <p>
 * 用于 /api/oss/{bucketName} 别名端点的结构化返回.
 */
public class OssBucketDetailResponse {

    private String bucketName;
    private List<Map<String, Object>> objects = new ArrayList<>();
    private int totalObjects;
    private long totalSize;

    public OssBucketDetailResponse() {
    }

    public OssBucketDetailResponse(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public List<Map<String, Object>> getObjects() {
        return objects;
    }

    public void setObjects(List<Map<String, Object>> objects) {
        this.objects = objects;
    }

    public int getTotalObjects() {
        return totalObjects;
    }

    public void setTotalObjects(int totalObjects) {
        this.totalObjects = totalObjects;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }
}
