package com.zifang.z.oss.core.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

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

            // 边读边算 MD5 边落盘。原来是先 ByteArrayOutputStream 收全量再 Files.write,
            // 峰值堆 ≈ 文件大小 —— 传个 1G 文件就是 OOM,而对象存储本来就不该按文件大小吃堆。
            MessageDigest md = newMd5();
            Path tmp = Files.createTempFile(path.getParent(), ".upload-", ".tmp");
            try {
                try (java.io.OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] data = new byte[64 * 1024];
                    int read;
                    while ((read = inputStream.read(data)) != -1) {
                        md.update(data, 0, read);
                        out.write(data, 0, read);
                    }
                }
                // 先写临时文件再原子改名: 避免写到一半进程挂掉,留下一个"看起来存在但内容截断"的对象
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
            return toHex(md.digest());
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
            pruneEmptyParents(path.getParent(), getStoragePath(bucket, ""));
        } catch (IllegalArgumentException e) {
            // 越界 key 属调用方输入问题，原样抛出，别被下面的兜底包成 RuntimeException 掩盖掉
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }

    /**
     * 删掉对象后把空掉的父目录一并回收。少了这一步，删光 a/1.txt 会在桶里
     * 留下空的 a/ 目录，而 deleteBucket 把"非 .acl 的目录项"一律算作非空 ——
     * 于是这个桶再也删不掉，只能进 pod 手删。
     */
    private void pruneEmptyParents(Path dir, Path stopAt) {
        try {
            Path cur = dir;
            while (cur != null && !cur.equals(stopAt) && Files.isDirectory(cur)) {
                try (Stream<Path> s = Files.list(cur)) {
                    if (s.findAny().isPresent()) {
                        return;
                    }
                }
                Files.deleteIfExists(cur);
                cur = cur.getParent();
            }
        } catch (IOException e) {
            log.debug("prune empty parent failed: {}", e.toString());
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
            return toHex(md.digest(data));
        } catch (Exception e) {
            return "invalid";
        }
    }

    private static String toHex(byte[] digest) {
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    // ==================== 分片上传的本地落盘实现 ====================
    //
    // 分片先落在 root/.zmultipart/<uploadId>/<partNumber>.part, complete 时按序号流式拼进
    // 目标对象的临时文件再原子改名 —— 全程不把整个对象读进堆。

    /**
     * 分片暂存根目录（桶目录之外，避免被 listObjects 当成对象列出来）
     */
    private Path multipartRoot() {
        return Paths.get(rootPath).toAbsolutePath().normalize().resolve(".zmultipart");
    }

    /**
     * 为一次分片上传建目录，返回 uploadId
     */
    public String createMultipartSession() throws IOException {
        Path root = multipartRoot();
        // createTempDirectory 不建父目录，.zmultipart 不先建出来就是 NoSuchFileException
        Files.createDirectories(root);
        Path dir = Files.createTempDirectory(root, "upload-");
        return dir.getFileName().toString();
    }

    /**
     * 写一个分片，返回该分片的 MD5
     */
    public String writePart(String uploadId, int partNumber, InputStream in) throws IOException {
        Path dir = multipartSessionDir(uploadId);
        Files.createDirectories(dir);
        Path part = dir.resolve(partNumber + ".part");
        MessageDigest md = newMd5();
        try (java.io.OutputStream out = Files.newOutputStream(part)) {
            byte[] data = new byte[64 * 1024];
            int read;
            while ((read = in.read(data)) != -1) {
                md.update(data, 0, read);
                out.write(data, 0, read);
            }
        }
        return toHex(md.digest());
    }

    /**
     * 按 partNumber 升序把分片拼成最终对象，返回整体 MD5
     */
    public String assembleParts(String bucket, String objectKey, String uploadId, List<Integer> partNumbers)
            throws IOException {
        Path dir = multipartSessionDir(uploadId);
        Path target = getStoragePath(bucket, objectKey);
        Files.createDirectories(target.getParent());
        MessageDigest md = newMd5();
        Path tmp = Files.createTempFile(target.getParent(), ".assemble-", ".tmp");
        try {
            try (java.io.OutputStream out = Files.newOutputStream(tmp)) {
                for (Integer partNumber : partNumbers) {
                    Path part = dir.resolve(partNumber + ".part");
                    if (!Files.exists(part)) {
                        throw new IOException("分片缺失: partNumber=" + partNumber + " uploadId=" + uploadId);
                    }
                    try (InputStream in = Files.newInputStream(part)) {
                        byte[] data = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(data)) != -1) {
                            md.update(data, 0, read);
                            out.write(data, 0, read);
                        }
                    }
                }
            }
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        abortMultipartSession(uploadId);
        return toHex(md.digest());
    }

    /**
     * 丢弃一次分片上传的全部暂存分片
     */
    public void abortMultipartSession(String uploadId) throws IOException {
        Path dir = multipartSessionDir(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    /**
     * 某个已落盘分片的字节数
     */
    public long getMultipartPartSize(String uploadId, int partNumber) throws IOException {
        Path part = multipartSessionDir(uploadId).resolve(partNumber + ".part");
        return Files.exists(part) ? Files.size(part) : 0;
    }

    /**
     * uploadId 完全由服务端生成，但仍然要过一遍路径校验 —— 它会直接参与拼路径。
     */
    private Path multipartSessionDir(String uploadId) {
        Path root = multipartRoot().normalize();
        Path dir = root.resolve(uploadId == null ? "" : uploadId).normalize();
        if (!dir.startsWith(root) || dir.equals(root)) {
            throw new IllegalArgumentException("Illegal uploadId: " + uploadId);
        }
        return dir;
    }
}
