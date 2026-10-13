package com.zifang.z.oss.spring.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 协议后端配置（AWS S3 / MinIO / Ceph RGW / 任意兼容实现共用一套）
 * <p>
 * 前缀 {@code oss.s3}。同一组键在两种部署下含义完全一致：
 * 唯一的差别是 {@link #endpoint} 指向 AWS 官方域名还是自建主机。
 */
@ConfigurationProperties(prefix = "oss.s3")
public class S3OssProperties {

    /**
     * 服务端点。AWS 填 {@code https://s3.<region>.amazonaws.com}，
     * MinIO 填 {@code http://<host>:<port>}。
     */
    private String endpoint;

    /**
     * accessKey。生产用环境变量注入，不要写进配置文件。
     */
    private String accessKey;

    /**
     * secretKey。
     */
    private String secretKey;

    /**
     * 区域。MinIO 可留空。
     */
    private String region = "us-east-1";

    /**
     * 桶名统一前缀。为空则用配置里的桶名原样发出去。
     */
    private String bucketPrefix = "";

    /**
     * 路径风格。AWS 及多数网关用 virtual-hosted；自建 MinIO 常见 path-style。
     */
    private boolean pathStyleAccess = false;

    /**
     * 单次 put 的分片大小（字节）。调用方未给出对象大小时用它切分，
     * minio 客户端要求 size 未知时必须显式给 partSize。
     */
    private int partSize = 10 * 1024 * 1024;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeoutMs = 10_000;

    /**
     * 读写超时（毫秒）。大对象上传要放宽。
     */
    private int readWriteTimeoutMs = 60_000;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucketPrefix() {
        return bucketPrefix;
    }

    public void setBucketPrefix(String bucketPrefix) {
        this.bucketPrefix = bucketPrefix;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public int getPartSize() {
        return partSize;
    }

    public void setPartSize(int partSize) {
        this.partSize = partSize;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadWriteTimeoutMs() {
        return readWriteTimeoutMs;
    }

    public void setReadWriteTimeoutMs(int readWriteTimeoutMs) {
        this.readWriteTimeoutMs = readWriteTimeoutMs;
    }
}
