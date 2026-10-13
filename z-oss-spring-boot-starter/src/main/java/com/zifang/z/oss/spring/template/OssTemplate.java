package com.zifang.z.oss.spring.template;

import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssPartInfo;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.OssProviderManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 对象存储业务门面 —— 消费方唯一需要注入的 bean。
 *
 * <p>门面本身不含存储逻辑：它按 {@code OssProviderManager} 当前激活的 provider 转发，
 * 所以 {@code oss.provider} 从 local 切到 s3 时，业务代码一行都不用改。
 *
 * <p>注入方式：
 * <pre>
 * &#64;Autowired OssTemplate oss;
 * oss.putObject("bucket", "a/b.txt", in, size, "text/plain");
 * try (InputStream in = oss.getObject("bucket", "a/b.txt")) { ... }
 * </pre>
 *
 * <p><b>线程安全</b>：内部无状态，所有方法直接转发给 provider。
 */
public class OssTemplate {

    private final OssProviderManager manager;

    public OssTemplate(OssProviderManager manager) {
        this.manager = manager;
    }

    // ==================== 对象 ====================

    /**
     * 上传对象（流式，不按文件大小吃堆）
     *
     * @param size        对象大小，调用方不知道时传 0
     * @param contentType MIME 类型，可空
     */
    public OssObjectSummary putObject(String bucket, String key, InputStream in,
                                      long size, String contentType) throws IOException {
        return provider().putObject(bucket, key, in, size, contentType);
    }

    /**
     * 读取整个对象。调用方负责关闭流。
     */
    public InputStream getObject(String bucket, String key) throws IOException {
        return provider().getObject(bucket, key);
    }

    /**
     * 范围读取（HTTP Range 语义）。{@code length} 传负数表示读到结尾。
     */
    public InputStream getObject(String bucket, String key, long offset, long length) throws IOException {
        return provider().getObject(bucket, key, offset, length);
    }

    public void deleteObject(String bucket, String key) throws IOException {
        provider().deleteObject(bucket, key);
    }

    /**
     * 批量删除。单个失败不中断整批（与 S3 DeleteObjects 静默模式一致）。
     *
     * @return 实际删除的 key
     */
    public List<String> deleteObjects(String bucket, String... keys) throws IOException {
        return deleteObjects(bucket, keys == null ? null : Arrays.asList(keys));
    }

    public List<String> deleteObjects(String bucket, List<String> keys) throws IOException {
        return provider().batchDeleteObjects(bucket, keys);
    }

    public boolean exists(String bucket, String key) {
        return provider().doesObjectExist(bucket, key);
    }

    /**
     * 对象元数据，不存在返回 {@code null}。
     */
    public OssObjectSummary stat(String bucket, String key) {
        return provider().statObject(bucket, key);
    }

    /**
     * 服务端复制（同桶改名走这里，不该下载再上传一遍）
     */
    public OssObjectSummary copy(String srcBucket, String srcKey, String destBucket, String destKey)
            throws IOException {
        return provider().copyObject(srcBucket, srcKey, destBucket, destKey);
    }

    /**
     * 列出对象。
     *
     * @param prefix  前缀过滤，可空
     * @param maxKeys 上限，<=0 表示不限
     */
    public List<OssObjectSummary> list(String bucket, String prefix, int maxKeys) {
        return provider().listObjects(bucket, prefix, maxKeys);
    }

    /**
     * 列出对象（不设上限）
     */
    public List<OssObjectSummary> list(String bucket) {
        return provider().listObjects(bucket, null, 0);
    }

    /**
     * 预签名 URL，用于把下载直交给浏览器/第三方，不让流量过业务进程。
     */
    public String presignedUrl(String bucket, String key, int expireSeconds) {
        return provider().generatePresignedUrl(bucket, key, expireSeconds);
    }

    // ==================== 分片上传 ====================

    /**
     * 开启分片上传，返回 uploadId。后续分片用 {@link #uploadPart} 逐个传，
     * 全部传完用 {@link #completeMultipart} 归并。
     */
    public String initiateMultipart(String bucket, String key, String contentType) {
        return provider().initiateMultipartUpload(bucket, key, contentType);
    }

    /**
     * 上传一个分片。
     *
     * @param partNumber 从 1 开始
     */
    public OssPartInfo uploadPart(String bucket, String key, String uploadId, int partNumber,
                                  InputStream in, long size, String contentType) throws IOException {
        return provider().uploadPart(bucket, key, uploadId, partNumber, in, size, contentType);
    }

    /**
     * 完成分片上传，按 partNumber 顺序归并成最终对象。
     *
     * @param parts 已上传的分片，可乱序传入
     */
    public OssObjectSummary completeMultipart(String bucket, String key, String uploadId,
                                              List<OssPartInfo> parts) throws IOException {
        return provider().completeMultipartUpload(bucket, key, uploadId, parts);
    }

    /**
     * 放弃分片上传并释放已上传分片占用的空间。客户端断线不调它是常见泄漏。
     */
    public void abortMultipart(String bucket, String key, String uploadId) {
        provider().abortMultipartUpload(bucket, key, uploadId);
    }

    // ==================== 桶 ====================

    /**
     * 创建桶。
     *
     * @param acl 可空，用后端默认
     */
    public void createBucket(String bucket, String region, String acl) throws IOException {
        provider().createBucket(bucket, region, acl);
    }

    public void createBucket(String bucket) throws IOException {
        createBucket(bucket, null, null);
    }

    public void deleteBucket(String bucket) throws IOException {
        provider().deleteBucket(bucket);
    }

    public boolean bucketExists(String bucket) {
        return provider().doesBucketExist(bucket);
    }

    public List<String> listBuckets() {
        return provider().listBuckets();
    }

    public void setBucketAcl(String bucket, String acl) {
        provider().setBucketAcl(bucket, acl);
    }

    public String getBucketAcl(String bucket) {
        return provider().getBucketAcl(bucket);
    }

    // ==================== provider 切换 ====================

    /**
     * 当前激活的 provider。想在同一次请求里跨后端读，用 {@link #provider(String)}。
     */
    public OssProvider provider() {
        return manager.provider();
    }

    /**
     * 按类型取 provider，用于"迁移期同时读写两端"这类跨后端场景。
     *
     * @throws IllegalStateException 类型未注册
     */
    public OssProvider provider(String type) {
        return manager.provider(type);
    }

    /**
     * 已注册的平台类型清单。
     */
    public List<String> providerTypes() {
        return new ArrayList<>(manager.listProviderTypes());
    }

    /**
     * 当前激活的平台类型标识。
     */
    public String activeProvider() {
        return manager.getActiveType();
    }
}
