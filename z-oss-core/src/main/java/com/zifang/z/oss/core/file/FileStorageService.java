package com.zifang.z.oss.core.file;

/**
 * 文件存储服务 — 对业务层提供简洁的 byte[] 上传与 URL 获取 API，
 * 内部委托给 {@link com.zifang.z.oss.core.provider.OssProviderManager}。
 */
public interface FileStorageService {

    /**
     * 基于字节数组创建上传构建器。
     */
    FileInfo of(byte[] bytes);

    /**
     * 将相对 URL / key 转为完整可访问 URL。
     * <p>本地模式: 拼接 public-base-url 前缀;
     * 云模式: 返回预签名 URL 或 CDN URL。
     */
    String getAllUrl(String url);
}
