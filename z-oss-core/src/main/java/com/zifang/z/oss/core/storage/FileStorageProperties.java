package com.zifang.z.oss.core.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置属性
 */
@ConfigurationProperties(prefix = "oss.storage.file")
public class FileStorageProperties {

    private String root = "/data/oss";

    /**
     * 默认 bucket 名称（FileStorageService 使用）
     */
    private String defaultBucket = "z-team";

    /**
     * 公网访问 URL 前缀（本地模式下拼接完整可访问 URL）
     */
    private String publicBaseUrl = "http://localhost:8888/oss-files/";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}