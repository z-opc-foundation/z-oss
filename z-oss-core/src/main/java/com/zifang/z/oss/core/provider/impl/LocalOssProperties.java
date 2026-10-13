package com.zifang.z.oss.core.provider.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * z-oss 自持后端（local provider）配置，前缀 {@code oss.local}。
 * <pre>
 * oss:
 *   provider: local
 *   local:
 *     access-key: xxx          # 预签名 URL 的 HMAC 密钥, 生产必配
 *     public-base-url: https://oss.example.com
 * </pre>
 *
 * <p>放在 core 而不是 starter：签发端（生成 URL）和校验端（校验 URL）是同一个
 * provider 的两面，必须读同一份配置，拆到两个件里迟早会只改一边。
 */
@ConfigurationProperties(prefix = "oss.local")
public class LocalOssProperties {

    /**
     * 预签名 URL 的 HMAC 密钥。留空退回 {@link LocalOssProvider#DEFAULT_SIGNATURE_KEY}，
     * 那只能跑本地开发 —— 任何人都能签出任意对象的下载链接。
     */
    private String accessKey;

    /**
     * 预签名 URL 的对外前缀（{@code scheme://host[:port]}）。留空则签出相对路径。
     */
    private String publicBaseUrl;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
