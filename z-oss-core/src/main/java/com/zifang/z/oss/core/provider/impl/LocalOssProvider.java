package com.zifang.z.oss.core.provider.impl;

import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssPartInfo;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;
import com.zifang.z.oss.core.util.ZOssSignature;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地文件系统 Provider（基于 {@link FileStorageEngine}）
 * <p>
 * 这是 z-oss 的<b>自持后端</b>：不接任何外部对象存储，z-oss 自己就是那个提供
 * 存储的服务端（对标 MinIO 的 standalone 模式）。分片上传与预签名 URL 在这里
 * 是一等能力，不是转发给云厂商的薄壳。
 */
public class LocalOssProvider implements OssProvider {

    /**
     * 未配置签名密钥时的兜底 accessKey。生产必须显式配 {@code oss.local.access-key}。
     */
    static final String DEFAULT_SIGNATURE_KEY = "zoss-local";

    private final FileStorageEngine engine;

    /**
     * 预签名 URL 用它签，也用它校验。空则退回 {@link #DEFAULT_SIGNATURE_KEY}。
     */
    private final String signatureAccessKey;

    /**
     * 预签名 URL 的对外前缀（scheme://host[:port]），空则返回相对路径。
     */
    private final String publicBaseUrl;

    public LocalOssProvider(FileStorageEngine engine) {
        this(engine, null, null);
    }

    public LocalOssProvider(FileStorageEngine engine, String signatureAccessKey, String publicBaseUrl) {
        this.engine = engine;
        this.signatureAccessKey = signatureAccessKey;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String getType() {
        return "local";
    }

    // ==================== 对象操作 ====================

    @Override
    public OssObjectSummary putObject(String bucket, String key, InputStream inputStream,
                                      long size, String contentType) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IOException("object key must not be empty");
        }
        String etag = engine.store(bucket, key, inputStream, size);
        long storedSize = engine.getSize(bucket, key);
        return new OssObjectSummary(key, storedSize, etag, contentType,
                key.endsWith("/"), new Date());
    }

    @Override
    public InputStream getObject(String bucket, String key) throws IOException {
        return engine.read(bucket, key);
    }

    @Override
    public void deleteObject(String bucket, String key) throws IOException {
        engine.delete(bucket, key);
    }

    @Override
    public boolean doesObjectExist(String bucket, String key) {
        return engine.exists(bucket, key);
    }

    @Override
    public OssObjectSummary statObject(String bucket, String key) {
        if (!engine.exists(bucket, key)) {
            return null;
        }
        long size = engine.getSize(bucket, key);
        boolean isFolder = key.endsWith("/");
        return new OssObjectSummary(key, size, null, null, isFolder, new Date());
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expireSeconds) {
        // 本地模式没有云厂商的签名服务可借, 自己签: HMAC-SHA256(accessKey, "bucket/key/expire"),
        // 过期时间与 accessKey 一起进 URL。签名/校验都在 ZOssSignature 里, 两边共用一份算法。
        int expire = expireSeconds <= 0 ? 900 : expireSeconds;
        String accessKey = (signatureAccessKey == null || signatureAccessKey.isEmpty())
                ? DEFAULT_SIGNATURE_KEY : signatureAccessKey;
        long expiresAt = System.currentTimeMillis() / 1000L + expire;
        String signature = ZOssSignature.sign(accessKey, bucket, key, expiresAt);

        String base = (publicBaseUrl == null || publicBaseUrl.isEmpty())
                ? "" : trimTrailingSlash(publicBaseUrl);
        return base + "/api/v1/object/download"
                + "?bucketName=" + urlEncode(bucket)
                + "&objectKey=" + urlEncodePath(key)
                + "&expires=" + expiresAt
                + "&accessKeyId=" + urlEncode(accessKey)
                + "&signature=" + signature;
    }

    private static String urlEncode(String v) {
        try {
            return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 不可用", e);
        }
    }

    /**
     * 对象 key 是路径语义：只编码非路径字符，保留 {@code /} 不编码，
     * 否则一个深层 key 会被编成单段，服务端解回来还是对的但 URL 不可读。
     */
    private static String urlEncodePath(String v) {
        return urlEncode(v).replace("%2F", "/").replace("+", "%20");
    }

    private static String trimTrailingSlash(String s) {
        String v = s.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    // ==================== 分片上传 ====================

    @Override
    public String initiateMultipartUpload(String bucket, String key, String contentType) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("object key must not be empty");
        }
        try {
            return engine.createMultipartSession();
        } catch (IOException e) {
            throw new RuntimeException("开启分片上传失败: " + key, e);
        }
    }

    @Override
    public OssPartInfo uploadPart(String bucket, String key, String uploadId, int partNumber,
                                   InputStream inputStream, long size, String contentType) throws IOException {
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber 从 1 开始, 收到 " + partNumber);
        }
        String etag = engine.writePart(uploadId, partNumber, inputStream);
        long written = engine.getMultipartPartSize(uploadId, partNumber);
        return new OssPartInfo(partNumber, etag, written);
    }

    @Override
    public OssObjectSummary completeMultipartUpload(String bucket, String key, String uploadId,
                                                    List<OssPartInfo> parts) throws IOException {
        if (parts == null || parts.isEmpty()) {
            throw new IOException("completeMultipartUpload 至少需要一个分片");
        }
        List<Integer> ordered = new ArrayList<>();
        for (OssPartInfo p : parts) {
            ordered.add(p.getPartNumber());
        }
        ordered.sort(Integer::compareTo);

        String etag = engine.assembleParts(bucket, key, uploadId, ordered);
        long size = engine.getSize(bucket, key);
        return new OssObjectSummary(key, size, etag, null, key.endsWith("/"), new Date());
    }

    @Override
    public void abortMultipartUpload(String bucket, String key, String uploadId) {
        try {
            engine.abortMultipartSession(uploadId);
        } catch (IOException e) {
            throw new RuntimeException("放弃分片上传失败: uploadId=" + uploadId, e);
        }
    }

    @Override
    public List<OssObjectSummary> listObjects(String bucket, String prefix, int maxKeys) {
        Path dir = resolveBucketDir(bucket);
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        final String prefixFilter = prefix == null ? "" : prefix;
        List<OssObjectSummary> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, 50)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals(".acl"))
                    .forEach(p -> {
                        String rel = dir.relativize(p).toString().replace(File.separatorChar, '/');
                        if (!rel.startsWith(prefixFilter)) {
                            return;
                        }
                        try {
                            long size = Files.size(p);
                            FileTime ft = Files.getLastModifiedTime(p);
                            boolean isFolder = rel.endsWith("/");
                            result.add(new OssObjectSummary(rel, size, null, null, isFolder,
                                    new Date(ft.toMillis())));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            return Collections.emptyList();
        }
        Collections.sort(result, (a, b) -> a.getKey().compareTo(b.getKey()));
        if (maxKeys > 0 && result.size() > maxKeys) {
            return result.subList(0, maxKeys);
        }
        return result;
    }

    // ==================== 桶操作 ====================

    @Override
    public void createBucket(String bucket, String region, String acl) throws IOException {
        if (bucket == null || bucket.isEmpty()) {
            throw new IOException("bucket name must not be empty");
        }
        Path dir = resolveBucketDir(bucket);
        if (Files.exists(dir)) {
            throw new IOException("Bucket already exists: " + bucket);
        }
        Files.createDirectories(dir);
        String aclValue = (acl == null || acl.isEmpty()) ? BucketAcl.PRIVATE.getCode() : acl;
        Files.write(dir.resolve(".acl"), aclValue.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void deleteBucket(String bucket) throws IOException {
        Path dir = resolveBucketDir(bucket);
        if (!Files.exists(dir)) {
            return;
        }
        // 拒绝删除非空桶
        try (Stream<Path> stream = Files.list(dir)) {
            long count = stream.filter(p -> !p.getFileName().toString().equals(".acl")).count();
            if (count > 0) {
                throw new IOException("Bucket is not empty: " + bucket);
            }
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Override
    public boolean doesBucketExist(String bucket) {
        if (bucket == null) {
            return false;
        }
        try {
            return Files.exists(resolveBucketDir(bucket));
        } catch (IllegalArgumentException e) {
            // 越界的桶名一律按"不存在"处理，保持本方法不抛异常的契约
            return false;
        }
    }

    @Override
    public List<String> listBuckets() {
        Path root = Paths.get(resolveLocalRoot());
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        File[] dirs = root.toFile().listFiles(File::isDirectory);
        if (dirs == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (File d : dirs) {
            names.add(d.getName());
        }
        Collections.sort(names);
        return names;
    }

    @Override
    public void setBucketAcl(String bucket, String acl) {
        Path aclFile = resolveBucketDir(bucket).resolve(".acl");
        try {
            Files.write(aclFile, (acl == null ? BucketAcl.PRIVATE.getCode() : acl)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to set bucket acl: " + bucket, e);
        }
    }

    @Override
    public String getBucketAcl(String bucket) {
        Path aclFile;
        try {
            aclFile = resolveBucketDir(bucket).resolve(".acl");
        } catch (IllegalArgumentException e) {
            // 越界的桶名按默认 ACL 处理，与下方读文件失败时的行为一致
            return BucketAcl.PRIVATE.getCode();
        }
        if (!Files.exists(aclFile)) {
            return BucketAcl.PRIVATE.getCode();
        }
        try {
            return new String(Files.readAllBytes(aclFile), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return BucketAcl.PRIVATE.getCode();
        }
    }

    /**
     * 反射拿到 FileStorageEngine 的 rootPath（私有字段）
     */
    private String resolveLocalRoot() {
        try {
            Field f = FileStorageEngine.class.getDeclaredField("rootPath");
            f.setAccessible(true);
            return (String) f.get(engine);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve FileStorageEngine.rootPath", e);
        }
    }

    /**
     * 把桶名解析为受约束的桶目录。
     *
     * <p>原来这里是 {@code Paths.get(resolveLocalRoot(), bucket)}，对 bucket 零校验，
     * 而 bucket 同样来自 HTTP 的 {@code @RequestParam}。最严重的是 {@code deleteBucket}：
     * 它会 {@code Files.walk} 后逐个 {@code deleteIfExists}，即
     * {@code deleteBucket("../../some-dir")} 可以<b>递归删除存储根目录之外的整棵目录树</b>
     * （只要该目录通过了"非空检查"）。</p>
     *
     * <p>{@code resolve} 遇到绝对路径会直接丢弃 base，故 {@code bucket=/etc} 这类也必须拦。</p>
     *
     * @throws IllegalArgumentException 桶名越出存储根目录，或指向根目录本身
     */
    private Path resolveBucketDir(String bucket) {
        Path root = Paths.get(resolveLocalRoot()).toAbsolutePath().normalize();
        Path dir = root.resolve(bucket == null ? "" : bucket).normalize();
        if (dir.equals(root) || !dir.startsWith(root)) {
            throw new IllegalArgumentException("Illegal bucket name, escapes storage root: " + bucket);
        }
        return dir;
    }
}
