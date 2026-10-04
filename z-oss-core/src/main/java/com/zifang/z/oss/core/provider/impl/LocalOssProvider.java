package com.zifang.z.oss.core.provider.impl;

import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;

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
 */
public class LocalOssProvider implements OssProvider {

    private final FileStorageEngine engine;

    public LocalOssProvider(FileStorageEngine engine) {
        this.engine = engine;
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
        // 本地模式无签名概念，返回相对路径（业务层负责鉴权）
        return "/api/v1/object/" + bucket + "/" + key;
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
