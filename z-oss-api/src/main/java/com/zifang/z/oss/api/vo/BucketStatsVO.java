package com.zifang.z.oss.api.vo;

import java.time.LocalDateTime;

/**
 * 存储桶统计视图对象 (用于 getBucketStats)
 */
public class BucketStatsVO {

    private Long totalObjects;
    private Long totalSize;
    private LocalDateTime lastModified;

    public Long getTotalObjects() {
        return totalObjects;
    }

    public void setTotalObjects(Long totalObjects) {
        this.totalObjects = totalObjects;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
}