package com.zifang.z.oss.core.storage;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * 本地文件系统存储引擎
 */
public class FileStorageEngine implements StorageEngine {

    private String rootPath;

    public FileStorageEngine() {
        // Default path - will be set by configuration
        this.rootPath = System.getProperty("oss.storage.file.root", "/Users/zifang/workplace/idea_workplace/z-oss/data/oss");
        System.out.println("=== FileStorageEngine initialized with rootPath: " + rootPath + " ===");
    }

    public void setRootPath(String rootPath) {
        System.out.println("=== FileStorageEngine setRootPath called: " + rootPath + " ===");
        this.rootPath = rootPath;
    }

    @Override
    public String store(String bucket, String objectKey, InputStream inputStream, long size) {
        try {
            Path path = getStoragePath(bucket, objectKey);
            Files.createDirectories(path.getParent());

            // 读取全部数据到字节数组
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            byte[] bytes = buffer.toByteArray();

            // 计算ETag
            String etag = computeETag(bytes);

            // 写入文件
            Files.write(path, bytes);

            return etag;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to store object: " + objectKey + " - " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream read(String bucket, String objectKey) {
        try {
            Path path = getStoragePath(bucket, objectKey);
            return new FileInputStream(path.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Object not found: " + objectKey, e);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            Path path = getStoragePath(bucket, objectKey);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        Path path = getStoragePath(bucket, objectKey);
        return Files.exists(path);
    }

    @Override
    public long getSize(String bucket, String objectKey) {
        try {
            Path path = getStoragePath(bucket, objectKey);
            return Files.size(path);
        } catch (Exception e) {
            return 0;
        }
    }

    private Path getStoragePath(String bucket, String objectKey) {
        // URL解码objectKey
        String decodedKey;
        try {
            decodedKey = java.net.URLDecoder.decode(objectKey, "UTF-8");
        } catch (Exception e) {
            decodedKey = objectKey;
        }
        return Paths.get(rootPath, bucket, decodedKey);
    }

    private String computeETag(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);

            // JDK8 通用的字节数组转十六进制
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "invalid";
        }
    }
}