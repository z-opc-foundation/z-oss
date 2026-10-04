package com.zifang.z.oss.core.provider.impl;

import com.zifang.z.oss.core.storage.FileStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalOssProvider} 的桶名路径约束。
 *
 * <p><b>修复的缺陷</b>：桶目录原本是 {@code Paths.get(resolveLocalRoot(), bucket)}，
 * 对 bucket 零校验，而 bucket 同样来自 HTTP 的 {@code @RequestParam}。
 * 最严重的是 {@link LocalOssProvider#deleteBucket}：它 {@code Files.walk} 后逐个
 * {@code deleteIfExists}，即可以用 {@code deleteBucket("../../some-dir")}
 * <b>递归删除存储根目录之外的整棵目录树</b>。</p>
 */
class LocalOssProviderPathTest {

    private static LocalOssProvider provider(Path root) {
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(root.toString());
        return new LocalOssProvider(engine);
    }

    @Test
    void createAndListBucketStillWork(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root);
        LocalOssProvider p = provider(root);

        p.createBucket("mybucket", null, "public-read");
        assertTrue(Files.isDirectory(root.resolve("mybucket")));

        assertTrue(p.doesBucketExist("mybucket"));
        assertEquals(List.of("mybucket"), p.listBuckets());
        assertEquals("public-read", p.getBucketAcl("mybucket"));
    }

    @Test
    void createBucketCannotEscapeRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root);
        LocalOssProvider p = provider(root);

        assertThrows(IllegalArgumentException.class, () -> p.createBucket("../escaped", null, null));
        assertFalse(Files.exists(tmp.resolve("escaped")), "桶目录不得建到 root 之外");
    }

    @Test
    void deleteBucketCannotEscapeRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root);
        // root 之外的一棵"空"目录树（只有 .acl，符合 deleteBucket 的非空检查）
        Path victim = tmp.resolve("victim");
        Files.createDirectories(victim);
        Files.write(victim.resolve(".acl"), "private".getBytes(StandardCharsets.UTF_8));

        LocalOssProvider p = provider(root);

        assertThrows(IllegalArgumentException.class, () -> p.deleteBucket("../victim"));
        assertTrue(Files.exists(victim), "root 之外的目录不得被删掉");
    }

    @Test
    void absoluteBucketNameRejected(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root);
        LocalOssProvider p = provider(root);

        // resolve 遇到绝对路径会直接丢弃 base，必须单独拦
        assertThrows(IllegalArgumentException.class, () -> p.createBucket("/etc/cron.d", null, null));
    }

    /** 这两个方法原本就不抛异常，修复后仍保持"安全默认"而不是抛。 */
    @Test
    void queryMethodsReturnSafeDefaultForIllegalBucket(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Files.createDirectories(root);
        LocalOssProvider p = provider(root);

        assertFalse(p.doesBucketExist("../.."));
        assertEquals("private", p.getBucketAcl("../.."));
    }
}
