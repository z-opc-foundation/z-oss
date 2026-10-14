package com.zifang.z.oss.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删光带前缀的对象后，空掉的父目录必须一起回收。
 *
 * <p><b>修复的缺陷</b>：{@code delete} 只 {@code deleteIfExists} 了对象文件本身，
 * 留下 {@code a/}、{@code a/b/} 这些空目录；而
 * {@link com.zifang.z.oss.core.provider.impl.LocalOssProvider#deleteBucket}
 * 把"非 {@code .acl} 的目录项"一律算作非空，于是对象全删光之后桶仍然删不掉。
 * 集群上这个状态只能进 pod 手删 —— 第一次踩到是在跑对真实服务的 IT 时。
 */
class FileStorageEnginePrefixPruneTest {

    private static InputStream payload(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static FileStorageEngine engine(Path root) {
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(root.toString());
        return engine;
    }

    @Test
    void deletingLastObjectInPrefixPrunesEmptyDirectories(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("bkt"));
        FileStorageEngine engine = engine(root);

        engine.store("bkt", "a/1.txt", payload("one"), 3);
        engine.store("bkt", "b/3.txt", payload("three"), 5);
        assertTrue(Files.isDirectory(root.resolve("bkt/a")));

        engine.delete("bkt", "a/1.txt");

        assertFalse(Files.exists(root.resolve("bkt/a")),
                "a/ 下面已经没有对象了，空目录应该被回收，否则桶永远删不掉");
        assertTrue(Files.isDirectory(root.resolve("bkt/b")),
                "b/ 还有对象，不能被一起删掉");
        assertTrue(Files.exists(root.resolve("bkt")));
    }

    @Test
    void pruneStopsAtBucketRootSoBucketItselfSurvives(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("bkt"));
        FileStorageEngine engine = engine(root);

        engine.store("bkt", "flat.txt", payload("x"), 1);
        engine.delete("bkt", "flat.txt");

        assertTrue(Files.isDirectory(root.resolve("bkt")), "桶目录本身不能被 prune 掉");
    }

    @Test
    void pruneStopsAtFirstNonEmptyAncestor(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("bkt"));
        FileStorageEngine engine = engine(root);

        engine.store("bkt", "a/1.txt", payload("one"), 3);
        engine.store("bkt", "a/2.txt", payload("two"), 3);
        engine.delete("bkt", "a/1.txt");

        assertTrue(Files.exists(root.resolve("bkt/a/2.txt")),
                "a/ 里还有 2.txt，a/ 必须留着");
        assertTrue(Files.isDirectory(root.resolve("bkt/a")));
    }
}