package com.zifang.z.oss.core.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger log = LogManager.getLogger(FileStorageEngine.class);

    /**
     * 未显式配置时的根目录。
     * <p>与 {@link FileStorageProperties#getRoot()} 的默认值保持一致。原来这里写的是某台
     * 开发机的绝对路径 {@code /Users/zifang/.../z-oss/data/oss}，而 Spring 侧
     * {@code OssCoreAutoConfiguration} 用的是 properties 的 {@code /data/oss} ——
     * 同一引擎经 Spring 构造与直接 new 出来会落在**两个不同的根目录**上，
     * 且前者一旦漏配就静默写到某个开发者本机路径。</p>
     */
    private static final String DEFAULT_ROOT = "/data/oss";

    private String rootPath;

    public FileStorageEngine() {
        this.rootPath = System.getProperty("oss.storage.file.root", DEFAULT_ROOT);
        log.info("FileStorageEngine initialized, rootPath={}", rootPath);
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
        log.info("FileStorageEngine rootPath set to {}", rootPath);
    }

    public String getRootPath() {
        return rootPath;
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
        } catch (IllegalArgumentException e) {
            // 非法 key：调用方的输入问题，调用方多半还要据此返回 400，原样抛出
            throw e;
        } catch (Exception e) {
            log.error("Failed to store object: {}", objectKey, e);
            throw new RuntimeException("Failed to store object: " + objectKey + " - " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream read(String bucket, String objectKey) {
        Path path = getStoragePath(bucket, objectKey);
        try {
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
        } catch (IllegalArgumentException e) {
            // 越界 key 属调用方输入问题，原样抛出，别被下面的兜底包成 RuntimeException 掩盖掉
            throw e;
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
        } catch (IllegalArgumentException e) {
            // 越界 key 不是"文件恰好读不到"，不能降级成 0 —— 0 会被当成"对象是空文件"
            throw e;
        } catch (Exception e) {
            log.debug("getSize failed for {}: {}", objectKey, e.toString());
            return 0;
        }
    }

    /**
     * 把 bucket + objectKey 解析为受约束的落盘路径。
     *
     * <p><b>为什么要约束</b>：原来直接 {@code Paths.get(rootPath, bucket, decodedKey)}，
     * 对 bucket 和 objectKey 零校验，而两者都来自 HTTP 的 {@code @RequestParam}
     * （见 {@code ObjectController} 的 upload / download / delete / HEAD / copy），
     * 上游 {@code OssObjectServiceImpl} 只用 {@code validateBucket} 校验**桶归属**，
     * objectKey 全程无任何检查。实测（先建好桶目录这一真实前置条件）三种形态的结果：</p>
     * <ul>
     *   <li>{@code ../outside/PWNED.txt} → 写到 {@code root/outside/}，**逃出了桶**</li>
     *   <li>{@code %2e%2e%2foutside%2fPWNED2.txt} → 同上（{@code getStoragePath} 会
     *       {@code URLDecoder.decode}，URL 编码不构成额外屏障）</li>
     *   <li>{@code ../../x} → <b>直接写到 root 之外</b>；同样的 key 走 read 能读到
     *       root 外文件内容、delete 能删掉 root 外文件、exists 能探测其是否存在</li>
     * </ul>
     *
     * <p>即任意文件读 / 写 / 删。现在先 {@code normalize()} 消解 {@code ..}，再双重校验：
     * 既不许逃出桶，也不许逃出 root。桶名本身也要校验 —— {@code resolve} 遇到绝对路径
     * 会直接丢弃前面的 base，所以 {@code bucket=/etc} 这类同样必须拦。</p>
     *
     * @throws IllegalArgumentException key 或 bucket 试图越出存储根目录
     */
    private Path getStoragePath(String bucket, String objectKey) {
        // URL解码objectKey
        String decodedKey;
        try {
            decodedKey = java.net.URLDecoder.decode(objectKey, "UTF-8");
        } catch (Exception e) {
            decodedKey = objectKey;
        }
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path bucketPath = root.resolve(bucket == null ? "" : bucket).normalize();
        Path path = bucketPath.resolve(decodedKey == null ? "" : decodedKey).normalize();

        if (!path.startsWith(bucketPath) || !path.startsWith(root)) {
            log.warn("Rejected illegal storage path: bucket={} objectKey={} -> {}", bucket, objectKey, path);
            throw new IllegalArgumentException(
                    "Illegal object key, escapes storage root: bucket=" + bucket + ", objectKey=" + objectKey);
        }
        return path;
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
