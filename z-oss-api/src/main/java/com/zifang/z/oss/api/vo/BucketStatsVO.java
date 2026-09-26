package com.zifang.z.oss.api.vo;

/**
 * 存储桶统计视图对象 (用于 getBucketStats)
 */
public class BucketStatsVO {

    private Long objectCount;
    private Long totalSize;
    private String activeProvider;

    public Long getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(Long objectCount) {
        this.objectCount = objectCount;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }
}
