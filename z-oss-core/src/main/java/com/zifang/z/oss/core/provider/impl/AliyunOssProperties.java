package com.zifang.z.oss.core.provider.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置
 * <pre>
 * oss:
 *   provider: aliyun
 *   aliyun:
 *     endpoint: oss-cn-hangzhou.aliyuncs.com
 *     access-key-id: xxx
 *     access-key-secret: xxx
 *     bucket-prefix: zopc-      # 可选，给所有桶加统一前缀
 *     default-acl: private
 * </pre>
 */
@ConfigurationProperties(prefix = "oss.aliyun")
public class AliyunOssProperties {

    /**
     * 阿里云 OSS Endpoint（如 oss-cn-hangzhou.aliyuncs.com）
     */
    private String endpoint;

    /**
     * AccessKey ID
     */
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * 桶名前缀（避免污染根账号），空表示不添加
     */
    private String bucketPrefix = "";

    /**
     * 创建桶时默认 ACL（private / public-read / public-read-write）
     */
    private String defaultAcl = "private";

    /**
     * STS 安全令牌（可选，启用 STS 鉴权时填写）
     */
    private String securityToken;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getBucketPrefix() {
        return bucketPrefix;
    }

    public void setBucketPrefix(String bucketPrefix) {
        this.bucketPrefix = bucketPrefix;
    }

    public String getDefaultAcl() {
        return defaultAcl;
    }

    public void setDefaultAcl(String defaultAcl) {
        this.defaultAcl = defaultAcl;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }

    /**
     * 业务侧传入的桶名 → 实际阿里云桶名（自动加前缀）
     */
    public String resolveBucketName(String bucket) {
        if (bucketPrefix == null || bucketPrefix.isEmpty()) {
            return bucket;
        }
        if (bucket.startsWith(bucketPrefix)) {
            return bucket;
        }
        return bucketPrefix + bucket;
    }
}
