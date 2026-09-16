package com.zifang.z.oss.api.vo;

/**
 * 预签名URL视图对象 (用于 getObjectUrl)
 */
public class PresignedUrlVO {

    private String url;
    private Long expiresAt;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}