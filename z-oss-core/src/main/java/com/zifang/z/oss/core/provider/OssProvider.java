package com.zifang.z.oss.core.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 通用对象存储 Provider 接口
 * <p>
 * 实现：
 * <ul>
 *   <li>{@code local}  —— 本地文件系统（FileStorageEngine）</li>
 *   <li>{@code aliyun} —— 阿里云 OSS</li>
 * </ul>
 * 通过 {@code oss.provider} 配置项切换。
 */
public interface OssProvider {

    /**
     * Provider 类型标识，对应 {@code oss.provider} 配置项
     */
    String getType();

    // ==================== 对象操作 ====================

    /**
     * 上传对象
     *
     * @param bucket      桶名
     * @param key         对象 key
     * @param inputStream 输入流
     * @param size        大小（字节），未知传 0
     * @param contentType MIME 类型，可空
     * @return 对象元数据
     */
    OssObjectSummary putObject(String bucket, String key, InputStream inputStream,
                               long size, String contentType) throws IOException;

    /**
     * 下载对象输入流（调用方负责关闭）
     */
    InputStream getObject(String bucket, String key) throws IOException;

    /**
     * 删除对象
     */
    void deleteObject(String bucket, String key) throws IOException;

    /**
     * 判断对象是否存在
     */
    boolean doesObjectExist(String bucket, String key);

    /**
     * 获取对象元数据
     */
    OssObjectSummary statObject(String bucket, String key);

    /**
     * 生成预签名 URL（用于直接下载或临时分享）
     *
     * @param expireSeconds 过期秒数
     */
    String generatePresignedUrl(String bucket, String key, int expireSeconds);

    /**
     * 列举对象
     *
     * @param prefix  前缀过滤，可空
     * @param maxKeys 最多返回条数
     */
    List<OssObjectSummary> listObjects(String bucket, String prefix, int maxKeys);

    // ==================== 桶操作 ====================

    /**
     * 创建桶
     *
     * @param acl 可空，使用 Provider 默认 ACL
     */
    void createBucket(String bucket, String region, String acl) throws IOException;

    /**
     * 删除桶（桶必须为空）
     */
    void deleteBucket(String bucket) throws IOException;

    /**
     * 判断桶是否存在
     */
    boolean doesBucketExist(String bucket);

    /**
     * 列举所有桶
     */
    List<String> listBuckets();

    /**
     * 设置桶 ACL
     */
    void setBucketAcl(String bucket, String acl);

    /**
     * 获取桶 ACL
     */
    String getBucketAcl(String bucket);
}
