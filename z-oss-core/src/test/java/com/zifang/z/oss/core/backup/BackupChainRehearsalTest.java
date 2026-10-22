package com.zifang.z.oss.core.backup;

import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.impl.LocalOssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-20261012-027 演练：z-oss <b>备份链</b>（归档 + 滚动保留 + 取回 + 失败可见）。
 *
 * <p>用 local provider 落真实对象文件（不是 mock）：备份链的真身就是"把字节送出去、
 * 留下最新 N 份、还能取回来"，mock 证明不了任何一件。</p>
 *
 * <p>演练流程里，本测试吃的是 <b>z-graph 导出的真快照</b>
 * （{@code -Dzgraph.rehearsal.snapshot=<路径>}），取回后落到
 * {@code -Dzgraph.rehearsal.dir}/restored.snapshot，交给 z-graph 的回读校验测试。</p>
 */
class BackupChainRehearsalTest {

    private static Path rehearsalDir() {
        String configured = System.getProperty("zgraph.rehearsal.dir");
        Path dir = configured == null || configured.trim().isEmpty()
                ? Paths.get("target", "rehearsal")
                : Paths.get(configured.trim());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create rehearsal dir " + dir, e);
        }
        return dir;
    }

    private static BackupChainService chain(Path storageRoot, int retention) {
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(storageRoot.toString());
        OssProvider provider = new LocalOssProvider(engine, "rehearsal-key", "http://localhost");
        BackupChainProperties props = new BackupChainProperties();
        props.setEnabled(true);
        props.setBucket("zgraph-backup");
        props.setKeyPrefix("zgraph/");
        props.setRetention(retention);
        return new BackupChainService(provider, props);
    }

    @Test
    void archivesKeepsNewestNAndRestoresByteIdentical(@TempDir Path storageRoot) throws IOException {
        Path dir = rehearsalDir();
        Path source = sourceSnapshot(dir);
        String expectedSha = sha256(source);

        BackupChainService chain = chain(storageRoot, 2);

        // 归档 3 份（模拟 3 天），保留 N=2 ⇒ 最老的一份被淘汰
        chain.archive(source, "zgraph/snap-001.bin");
        chain.archive(source, "zgraph/snap-002.bin");
        BackupChainService.BackupArtifact third = chain.archive(source, "zgraph/snap-003.bin");

        List<BackupChainService.BackupArtifact> backups = chain.listBackups();
        assertEquals(2, backups.size(), "滚动保留 N=2 ⇒ 归档后只剩 2 份");
        assertFalse(backups.stream().anyMatch(b -> b.getKey().equals("zgraph/snap-001.bin")),
                "最老的一份必须被淘汰");
        assertEquals(1, third.getEvictedByThisArchive().size(), "本次归档淘汰 1 份");
        assertEquals("zgraph/snap-001.bin", third.getEvictedByThisArchive().get(0));

        // 取回最新那份 ⇒ 字节级一致（回读校验的第一道闸）
        Path restored = dir.resolve("restored.snapshot");
        chain.restore(third.getKey(), restored);
        assertTrue(Files.exists(restored));
        assertEquals(expectedSha, sha256(restored), "取回的备份必须与原件字节一致");

        // 计数可观测
        Map<String, Object> stats = chain.stats();
        assertEquals(3L, stats.get("archives"));
        assertEquals(0L, stats.get("archiveFailures"));
        assertEquals(1L, stats.get("retentionDeletions"));
        assertEquals(1L, stats.get("restores"));
        assertEquals(2, ((Number) stats.get("backupCount")).intValue());
    }

    @Test
    void retentionDisabledKeepsEverything(@TempDir Path storageRoot) throws IOException {
        Path dir = rehearsalDir();
        Path source = sourceSnapshot(dir);
        BackupChainService chain = chain(storageRoot, 0);   // <=0 ⇒ 只增不删
        chain.archive(source, "zgraph/a.bin");
        chain.archive(source, "zgraph/b.bin");
        chain.archive(source, "zgraph/c.bin");
        assertEquals(3, chain.listBackups().size());
    }

    @Test
    void archiveFailureIsVisibleNotSilent(@TempDir Path storageRoot) {
        BackupChainService chain = chain(storageRoot, 2);
        Path missing = storageRoot.resolve("does-not-exist.snapshot");

        assertThrows(IOException.class, () -> chain.archive(missing, "zgraph/x.bin"));

        Map<String, Object> stats = chain.stats();
        assertEquals(1L, stats.get("archiveFailures"), "失败必须计数");
        assertNotNull(stats.get("lastError"));
        assertTrue(String.valueOf(stats.get("lastError")).contains("archive failed"));
        assertEquals(0L, stats.get("archives"));
    }

    @Test
    void restoreFailureIsVisibleNotSilent(@TempDir Path storageRoot) throws IOException {
        BackupChainService chain = chain(storageRoot, 2);
        Path dir = rehearsalDir();
        assertThrows(RuntimeException.class,
                () -> chain.restore("zgraph/not-there.bin", dir.resolve("nope.snapshot")));
        Map<String, Object> stats = chain.stats();
        assertEquals(1L, stats.get("restoreFailures"));
        assertTrue(String.valueOf(stats.get("lastError")).contains("restore failed"));
    }

    /** 演练输入：优先用 z-graph 导出的真快照；没有就造一份同形状的（保证单跑也能过）。 */
    private static Path sourceSnapshot(Path dir) throws IOException {
        String configured = System.getProperty("zgraph.rehearsal.snapshot");
        if (configured != null && !configured.trim().isEmpty()) {
            Path snapshot = Paths.get(configured.trim());
            assertTrue(Files.exists(snapshot), "z-graph 快照必须存在: " + snapshot);
            return snapshot;
        }
        Path synthetic = dir.resolve("synthetic.snapshot");
        if (!Files.exists(synthetic)) {
            Files.write(synthetic, "z-graph-snapshot-fixture".getBytes(StandardCharsets.UTF_8));
        }
        return synthetic;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed for " + file, e);
        }
    }
}
