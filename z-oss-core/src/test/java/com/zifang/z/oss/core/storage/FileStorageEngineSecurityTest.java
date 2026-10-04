package com.zifang.z.oss.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileStorageEngine} 的路径约束。
 *
 * <p><b>修复的缺陷</b>：{@code getStoragePath} 原本是
 * {@code Paths.get(rootPath, bucket, decodedKey)}，对 bucket 与 objectKey 零校验，
 * 而两者都直接来自 HTTP 的 {@code @RequestParam}。实测（以"桶目录已存在"为真实前置），
 * {@code objectKey=../../x} 可以把文件写到 root 之外；同一个 key 走 read 能读到 root 外
 * 文件的内容、delete 能删掉 root 外文件、exists 能探测其是否存在。</p>
 */
class FileStorageEngineSecurityTest {

    private static InputStream payload(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 构造一个 root 与 root 之外的"受害目录"，并预先建好桶目录（真实使用顺序）。 */
    private static FileStorageEngine engineWithBucket(Path root, Path bucketDir) throws Exception {
        Files.createDirectories(bucketDir);
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(root.toString());
        return engine;
    }

    // ==================== 正常路径不受影响 ====================

    @Test
    void normalKeyStillWorks(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        engine.store("mybucket", "a/b/c.txt", payload("hello"), 5);

        Path expected = root.resolve("mybucket/a/b/c.txt");
        assertTrue(Files.exists(expected), "正常 key 应照常落盘: " + expected);
        assertEquals("hello", new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));
        assertTrue(engine.exists("mybucket", "a/b/c.txt"));
        assertEquals(5L, engine.getSize("mybucket", "a/b/c.txt"));
    }

    @Test
    void urlEncodedNormalKeyStillWorks(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        engine.store("mybucket", "dir%2Ffile.txt", payload("x"), 1);
        assertTrue(Files.exists(root.resolve("mybucket/dir/file.txt")));
    }

    // ==================== 写：不得逃出 root ====================

    @Test
    void writeCannotEscapeRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Path victim = tmp.resolve("PWNED.txt");
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.store("mybucket", "../../" + victim.getFileName(), payload("x"), 1));
        assertFalse(Files.exists(victim), "越界写必须被拦下");
    }

    /** URL 编码不构成额外屏障：getStoragePath 会先 URLDecoder.decode。 */
    @Test
    void urlEncodedTraversalAlsoRejected(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Path victim = tmp.resolve("PWNED2.txt");
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.store("mybucket", "%2e%2e%2f" + victim.getFileName(), payload("x"), 1));
        assertFalse(Files.exists(victim));
    }

    /** 逃出桶（但仍在 root 内）同样要拦：桶是隔离边界。 */
    @Test
    void writeCannotEscapeBucket(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.store("mybucket", "../otherbucket/steal.txt", payload("x"), 1));
    }

    /** 绝对路径：resolve 遇到绝对路径会丢弃 base，必须单独拦。 */
    @Test
    void absoluteKeyRejected(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.store("mybucket", "/etc/passwd", payload("x"), 1));
    }

    /** bucket 自身越界（上游只校验了桶归属，不校验名字形态）。 */
    @Test
    void traversalInBucketNameRejected(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.store("../..", "evil.txt", payload("x"), 1));
    }

    // ==================== 读 / 删 / 探测 ====================

    @Test
    void readCannotEscapeRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        Path secret = tmp.resolve("secret.txt");
        Files.write(secret, "TOP-SECRET".getBytes(StandardCharsets.UTF_8));

        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class, () -> engine.read("mybucket", "../../secret.txt"));
    }

    @Test
    void deleteCannotEscapeRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        Path victim = tmp.resolve("victim.txt");
        Files.write(victim, "x".getBytes(StandardCharsets.UTF_8));

        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class, () -> engine.delete("mybucket", "../../victim.txt"));
        assertTrue(Files.exists(victim), "越界删必须被拦下");
    }

    @Test
    void existsCannotProbeOutsideRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("mybucket"));
        Files.write(tmp.resolve("secret.txt"), "s".getBytes(StandardCharsets.UTF_8));

        FileStorageEngine engine = engineWithBucket(root, root.resolve("mybucket"));

        assertThrows(IllegalArgumentException.class, () -> engine.exists("mybucket", "../../secret.txt"));
    }

    // ==================== 默认根目录 ====================

    /**
     * 构造默认曾写死某台开发机的绝对路径，而 Spring 侧 properties 的默认是 /data/oss，
     * 同一个引擎经两条路径构造会落在两个不同的根上。
     */
    @Test
    void defaultRootIsNotADeveloperMachinePath() {
        FileStorageEngine engine = new FileStorageEngine();
        String root = engine.getRootPath();
        assertFalse(root.contains("/Users/") || root.contains("zifang"),
                "默认根目录不得是某台开发机的家目录: " + root);
        assertEquals("/data/oss", root, "应与 FileStorageProperties 的默认值一致");
    }
}
