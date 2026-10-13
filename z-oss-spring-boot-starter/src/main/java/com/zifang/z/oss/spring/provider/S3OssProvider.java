package com.zifang.z.oss.spring.provider;

import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProvider;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * S3 协议后端 Provider —— 一套代码同时对接 AWS S3 与自建 MinIO。
 * <p>
 * 两者的差别只有 {@code endpoint} 与 {@code path-style}：AWS 用 virtual-hosted
 * （{@code https://bucket.s3.region.amazonaws.com}），自建 MinIO 通常只能 path-style
 * （{@code http://host:9000/bucket}）。因此不引入 aws-sdk，只用 S3 协议客户端。
 *
 * <p>ACL：S3 没有 MinIO/阿里云那套 canned ACL 枚举，权限由 bucket policy 文档表达。
 * {@link #setBucketAcl} 落的是 {@code public-read} / {@code public-read-write} 两档
 * policy，{@code private} 等价于删除 policy（未挂 policy 即私有）。
 */
public class S3OssProvider implements OssProvider {

    private static final Logger log = LogManager.getLogger(S3OssProvider.class);

    /**
     * S3 预签名 URL 的有效期上限（秒）。协议侧硬限 7 天。
     */
    private static final int MAX_PRESIGN_SECONDS = 7 * 24 * 3600;

    private static final String POLICY_VERSION = "2012-10-17";

    private final MinioClient client;

    private final String bucketPrefix;

    public S3OssProvider(S3OssProperties props) {
        if (props.getEndpoint() == null || props.getEndpoint().trim().isEmpty()) {
            throw new IllegalArgumentException("oss.s3.endpoint 未配置, S3 provider 无法启动");
        }
        this.bucketPrefix = props.getBucketPrefix() == null ? "" : props.getBucketPrefix();
        this.client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .region(props.getRegion())
                .build();
        if (props.getConnectTimeoutMs() > 0) {
            client.setTimeout(props.getConnectTimeoutMs(), props.getReadWriteTimeoutMs(), 10L);
        }
    }

    @Override
    public String getType() {
        return "s3";
    }

    /**
     * 统一出口：任何一层异常都包成 IOException。
     * <p>{@link OssProvider} 的契约是 IOException，minio 的一串 checked exception
     * 透传出去会把调用方的 try/catch 面撑成八个分支。
     */
    private static IOException wrap(String op, Exception e) {
        if (e instanceof IOException) {
            return (IOException) e;
        }
        log.error("S3 操作失败: op={}", op, e);
        return new IOException("S3 " + op + " 失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    private String realBucket(String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("bucketName 必填");
        }
        return bucketPrefix + bucket;
    }

    // ==================== 对象操作 ====================

    @Override
    public OssObjectSummary putObject(String bucket, String key, InputStream inputStream,
                                      long size, String contentType) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IOException("object key must not be empty");
        }
        String b = realBucket(bucket);
        try {
            PutObjectArgs.Builder bld = PutObjectArgs.builder().bucket(b).object(key);
            if (contentType != null && !contentType.isEmpty()) {
                bld.contentType(contentType);
            }
            // size <= 0 表示调用方不知道长度（流式上传）。minio 要求此时必须给 partSize，
            // 不给会直接抛 IllegalArgumentException；5MB 是官方客户端的默认分片尺寸。
            if (size > 0) {
                bld.stream(inputStream, size, -1);
            } else {
                bld.stream(inputStream, -1, 5 * 1024 * 1024);
            }
            ObjectWriteResponse resp = client.putObject(bld.build());
            // putObject 的响应里没有 size 字段（只有 etag/versionId），回读一次 stat 拿真实大小。
            // 顺带把 contentType 也对齐 —— 流式上传(size<=0)时调用方给不出长度,只有 stat 说得准。
            OssObjectSummary st = statObject(bucket, key);
            return new OssObjectSummary(key, st == null ? null : st.getSize(),
                    resp == null ? null : resp.etag(), contentType, key.endsWith("/"), new Date());
        } catch (Exception e) {
            throw wrap("putObject " + b + "/" + key, e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String key) throws IOException {
        return getObject(bucket, key, -1, -1);
    }

    /**
     * S3 原生支持 Range，所以这里走真协议而不是基类那个"读全量再 skip"的兜底实现。
     */
    @Override
    public InputStream getObject(String bucket, String key, long offset, long length) throws IOException {
        GetObjectArgs.Builder bld = GetObjectArgs.builder().bucket(realBucket(bucket)).object(key);
        if (offset > 0) {
            bld.offset(offset);
        }
        if (length >= 0) {
            bld.length(length);
        }
        try {
            return client.getObject(bld.build());
        } catch (Exception e) {
            throw wrap("getObject " + bucket + "/" + key, e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(realBucket(bucket)).object(key).build());
        } catch (Exception e) {
            throw wrap("deleteObject " + bucket + "/" + key, e);
        }
    }

    @Override
    public boolean doesObjectExist(String bucket, String key) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(realBucket(bucket)).object(key).build());
            return true;
        } catch (Exception e) {
            // "不存在"是正常分支，不是错误
            return false;
        }
    }

    @Override
    public OssObjectSummary statObject(String bucket, String key) {
        try {
            io.minio.StatObjectResponse st = client.statObject(StatObjectArgs.builder()
                    .bucket(realBucket(bucket)).object(key).build());
            return new OssObjectSummary(key, st.size(), st.etag(),
                    st.contentType(), key.endsWith("/"),
                    new Date(st.lastModified().toInstant().toEpochMilli()));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expireSeconds) {
        int expire = expireSeconds <= 0 ? 3600 : Math.min(expireSeconds, MAX_PRESIGN_SECONDS);
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(realBucket(bucket))
                    .object(key)
                    .expiry(expire, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成预签名 URL 失败: " + bucket + "/" + key, e);
        }
    }

    @Override
    public List<OssObjectSummary> listObjects(String bucket, String prefix, int maxKeys) {
        ListObjectsArgs.Builder bld = ListObjectsArgs.builder()
                .bucket(realBucket(bucket))
                // 不加 recursive 时 minio 按 "/" 切分隔符，只回公共前缀（a/、b/ 这类哨兵），
                // 叶子对象一个都不给。OssProvider 的契约是返回叶子对象（local 端是 Files.walk 递归），
                // 两端不一致会让 OssTemplate.list() 的结果随 oss.provider 切换而变。
                .recursive(true);
        if (prefix != null && !prefix.isEmpty()) {
            bld.prefix(prefix);
        }
        if (maxKeys > 0) {
            bld.maxKeys(maxKeys);
        }
        List<OssObjectSummary> out = new ArrayList<>();
        try {
            for (Result<Item> r : client.listObjects(bld.build())) {
                Item it = r.get();
                // minio 的目录哨兵对象 key 结尾带 '/'，映射成 folder 标志而不是当普通对象
                String key = it.objectName();
                boolean isFolder = it.isDir() || key.endsWith("/");
                out.add(new OssObjectSummary(key, it.size(), it.etag(), null, isFolder, null));
            }
        } catch (Exception e) {
            log.error("S3 listObjects 失败: bucket={}", bucket, e);
            return Collections.emptyList();
        }
        return out;
    }

    // ==================== 桶操作 ====================

    @Override
    public void createBucket(String bucket, String region, String acl) throws IOException {
        String b = realBucket(bucket);
        try {
            MakeBucketArgs.Builder bld = MakeBucketArgs.builder().bucket(b);
            if (region != null && !region.isEmpty()) {
                bld.region(region);
            }
            client.makeBucket(bld.build());
            if (acl != null && !acl.isEmpty()) {
                setBucketAcl(bucket, acl);
            }
        } catch (Exception e) {
            throw wrap("createBucket " + b, e);
        }
    }

    @Override
    public void deleteBucket(String bucket) throws IOException {
        try {
            client.removeBucket(RemoveBucketArgs.builder().bucket(realBucket(bucket)).build());
        } catch (Exception e) {
            throw wrap("deleteBucket " + bucket, e);
        }
    }

    @Override
    public boolean doesBucketExist(String bucket) {
        try {
            return client.bucketExists(BucketExistsArgs.builder().bucket(realBucket(bucket)).build());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listBuckets() {
        List<String> names = new ArrayList<>();
        try {
            for (Bucket b : client.listBuckets()) {
                names.add(b.name());
            }
        } catch (Exception e) {
            log.error("S3 listBuckets 失败", e);
            return Collections.emptyList();
        }
        if (bucketPrefix.isEmpty()) {
            return names;
        }
        // 有前缀时只把"去掉前缀后仍是自己建的桶"报给业务层
        List<String> stripped = new ArrayList<>();
        for (String n : names) {
            if (n.startsWith(bucketPrefix)) {
                stripped.add(n.substring(bucketPrefix.length()));
            }
        }
        return stripped;
    }

    @Override
    public void setBucketAcl(String bucket, String acl) {
        String b = realBucket(bucket);
        if (acl == null || BucketAcl.PRIVATE.getCode().equals(acl)) {
            // S3 里"私有"就是没有 policy，不需要显式落一份 deny-all 文档
            try {
                client.deleteBucketPolicy(io.minio.DeleteBucketPolicyArgs.builder().bucket(b).build());
            } catch (Exception e) {
                log.debug("删除 bucket policy 失败(桶上本来就没有): {}", b, e);
            }
            return;
        }
        String principal = BucketAcl.PUBLIC_READ_WRITE.getCode().equals(acl) ? "*" : null;
        String resource = "arn:aws:s3:::" + b + "/*";
        String actions = principal == null
                ? "[\"s3:GetObject\"]"
                : "[\"s3:GetObject\",\"s3:PutObject\",\"s3:DeleteObject\"]";
        String policy = "{"
                + "\"Version\":\"" + POLICY_VERSION + "\","
                + "\"Statement\":[{"
                + "\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[" + (principal == null ? "*" : principal) + "]},"
                + "\"Action\":" + actions + ","
                + "\"Resource\":[\"" + resource + "\"]"
                + "}]}";
        try {
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(b).config(policy).build());
        } catch (Exception e) {
            throw new IllegalStateException("设置 bucket policy 失败: " + b + " acl=" + acl, e);
        }
    }

    @Override
    public String getBucketAcl(String bucket) {
        String b = realBucket(bucket);
        try {
            String policy = client.getBucketPolicy(io.minio.GetBucketPolicyArgs.builder().bucket(b).build());
            if (policy == null || policy.isEmpty()) {
                return BucketAcl.PRIVATE.getCode();
            }
            return policy.contains("s3:PutObject")
                    ? BucketAcl.PUBLIC_READ_WRITE.getCode()
                    : BucketAcl.PUBLIC_READ.getCode();
        } catch (Exception e) {
            return BucketAcl.PRIVATE.getCode();
        }
    }

    // ==================== 服务端复制 / 批量 ====================

    @Override
    public OssObjectSummary copyObject(String sourceBucket, String sourceKey,
                                       String destBucket, String destKey) throws IOException {
        // S3 有真 CopyObject，不是"下载再上传"
        try {
            ObjectWriteResponse resp = client.copyObject(CopyObjectArgs.builder()
                    .bucket(realBucket(destBucket))
                    .object(destKey)
                    .source(CopySource.builder()
                            .bucket(realBucket(sourceBucket))
                            .object(sourceKey)
                            .build())
                    .build());
            OssObjectSummary st = statObject(destBucket, destKey);
            return new OssObjectSummary(destKey, st == null ? null : st.getSize(),
                    resp == null ? null : resp.etag(), null, destKey.endsWith("/"), new Date());
        } catch (Exception e) {
            throw wrap("copyObject " + sourceBucket + "/" + sourceKey + " -> "
                    + destBucket + "/" + destKey, e);
        }
    }

    @Override
    public List<String> batchDeleteObjects(String bucket, List<String> keys) throws IOException {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        // S3 的 DeleteObjects 对不存在的 key 也回成功，直接把结果原样返回会让
        // OssProvider#batchDeleteObjects 的"实际删除"契约在两个后端上不一致
        // （local 只报真正删掉的）。先按存在性过滤，批量请求只发真实存在的那些。
        List<String> existing = new ArrayList<>();
        for (String k : keys) {
            if (doesObjectExist(bucket, k)) {
                existing.add(k);
            }
        }
        if (existing.isEmpty()) {
            return Collections.emptyList();
        }
        List<io.minio.messages.DeleteObject> targets = new ArrayList<>(existing.size());
        for (String k : existing) {
            targets.add(new io.minio.messages.DeleteObject(k));
        }
        List<String> deleted = new ArrayList<>(existing);
        try {
            for (Result<io.minio.messages.DeleteError> r : client.removeObjects(
                    RemoveObjectsArgs.builder().bucket(realBucket(bucket)).objects(targets).build())) {
                io.minio.messages.DeleteError err = r.get();
                if (err != null) {
                    // 静默语义：单个失败不影响整批，但失败的那个不能算进"已删除"
                    log.warn("S3 批量删除失败: bucket={} key={} code={}",
                            bucket, err.objectName(), err.code());
                    deleted.remove(err.objectName());
                }
            }
            return deleted;
        } catch (Exception e) {
            throw wrap("batchDeleteObjects " + bucket, e);
        }
    }

    // ==================== 分片上传 ====================

    /**
     * S3 的 multipart 在协议上是三段独立调用（CreateMultipartUpload / UploadPart /
     * CompleteMultipartUpload），minio 客户端只在 {@code MinioAsyncClient} 的底层
     * 留了口子，8.x 高层 API 没暴露。留着基类的默认实现：显式报"不支持"，
     * 好过把大文件悄悄读进内存拼成一个 put。
     */
    @Override
    public String initiateMultipartUpload(String bucket, String key, String contentType) {
        throw new UnsupportedOperationException(
                "Provider 's3' 不支持分片上传（S3 multipart 需走低层 AsyncClient 签名调用）");
    }
}
