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

    // ==================== 服务端复制 / 批量 / 范围读 ====================

    /**
     * 服务端复制对象（同桶改名走这个，不该由客户端下载再上传一遍）
     *
     * @return 目标对象元数据
     */
    default OssObjectSummary copyObject(String sourceBucket, String sourceKey,
                                        String destBucket, String destKey) throws IOException {
        try (InputStream in = getObject(sourceBucket, sourceKey)) {
            OssObjectSummary stat = statObject(sourceBucket, sourceKey);
            return putObject(destBucket, destKey, in,
                    stat == null || stat.getSize() == null ? 0 : stat.getSize(),
                    stat == null ? null : stat.getContentType());
        }
    }

    /**
     * 批量删除对象，单个失败不中断整批（语义同 S3 DeleteObjects 的静默模式）
     *
     * @return 实际删除的 key 列表
     */
    default List<String> batchDeleteObjects(String bucket, List<String> keys) throws IOException {
        List<String> deleted = new java.util.ArrayList<>();
        if (keys == null) {
            return deleted;
        }
        for (String key : keys) {
            if (doesObjectExist(bucket, key)) {
                deleteObject(bucket, key);
                deleted.add(key);
            }
        }
        return deleted;
    }

    /**
     * 范围读取（HTTP Range）。大对象断点续传、视频拖动进度条都靠它。
     *
     * @param offset 起始偏移
     * @param length 读取字节数，负数表示读到结尾
     */
    default InputStream getObject(String bucket, String key, long offset, long length) throws IOException {
        InputStream full = getObject(bucket, key);
        if (offset <= 0 && length < 0) {
            return full;
        }
        try {
            long skipped = 0;
            while (skipped < offset) {
                long n = full.skip(offset - skipped);
                if (n <= 0) {
                    // skip 返回 0 有两种含义：到头了，或流不支持 skip。读一个字节来分辨。
                    if (full.read() == -1) {
                        return new java.io.ByteArrayInputStream(new byte[0]);
                    }
                    skipped++;
                } else {
                    skipped += n;
                }
            }
            if (length < 0) {
                return full;
            }
            return new java.io.ByteArrayInputStream(readN(full, (int) length));
        } catch (IOException e) {
            full.close();
            throw e;
        }
    }

    static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r == -1) {
                break;
            }
            read += r;
        }
        if (read == n) {
            return buf;
        }
        byte[] exact = new byte[read];
        System.arraycopy(buf, 0, exact, 0, read);
        return exact;
    }

    // ==================== 分片上传 ====================

    /**
     * 开启一次分片上传，返回 uploadId。
     * <p>
     * 默认实现不支持 —— 各后端要支持就得自己覆盖。没实现的平台会明确报
     * "不支持分片上传"，而不是把大文件静默塞进内存。
     */
    default String initiateMultipartUpload(String bucket, String key, String contentType) {
        throw new UnsupportedOperationException(
                "Provider '" + getType() + "' 不支持分片上传");
    }

    /**
     * 上传一个分片
     *
     * @param partNumber 分片序号，从 1 开始
     * @return 分片信息（ETag + 大小），合并时要拿它校验
     */
    default OssPartInfo uploadPart(String bucket, String key, String uploadId,
                                    int partNumber, InputStream inputStream,
                                    long size, String contentType) throws IOException {
        throw new UnsupportedOperationException(
                "Provider '" + getType() + "' 不支持分片上传");
    }

    /**
     * 完成分片上传，按 partNumber 顺序归并成最终对象
     *
     * @param parts 已上传的分片（可乱序传入，Provider 自行按 partNumber 排序）
     */
    default OssObjectSummary completeMultipartUpload(String bucket, String key, String uploadId,
                                                     List<OssPartInfo> parts) throws IOException {
        throw new UnsupportedOperationException(
                "Provider '" + getType() + "' 不支持分片上传");
    }

    /**
     * 放弃分片上传，释放已上传分片占用的空间
     */
    default void abortMultipartUpload(String bucket, String key, String uploadId) {
        throw new UnsupportedOperationException(
                "Provider '" + getType() + "' 不支持分片上传");
    }
}
