package com.zifang.z.oss.core.backup;

import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>备份链</b>：把备份产物归档到对象存储，并按"保留最新 N 份"滚动淘汰。
 *
 * <p>用在哪：z-graph 的 {@code exportSnapshot} 产物（仓库级容灾的第一份）之外，
 * 再往对象存储里放一份<b>异地</b>副本。N（保留份数）可配。</p>
 *
 * <p><b>这一层刻意不碰数据库</b>：它直接用 {@link OssProvider}（local / aliyun / s3 都行）
 * 读写对象，所以备份链能不能用，跟对象表里有没有行无关 ——
 * "备份在库里看不见"不等于"备份不存在"，但更常见的是反例：
 * DB 连不上时备份链照样能把文件送出去。</p>
 *
 * <p><b>失败可见</b>：任何一步失败都记 ERROR 日志 + 计数 + 最后一错，并<b>照抛</b>，
 * 绝不静默。备份链最坏的失败模式是"以为在备份，其实什么都没发生"。</p>
 */
public class BackupChainService {

    private static final Logger log = LogManager.getLogger(BackupChainService.class);

    private final OssProvider provider;
    private final BackupChainProperties properties;

    private final AtomicLong archiveAttempts = new AtomicLong();
    private final AtomicLong archives = new AtomicLong();
    private final AtomicLong archiveFailures = new AtomicLong();
    private final AtomicLong retentionDeletions = new AtomicLong();
    private final AtomicLong restoreAttempts = new AtomicLong();
    private final AtomicLong restores = new AtomicLong();
    private final AtomicLong restoreFailures = new AtomicLong();

    private volatile long lastArchiveAtMillis = -1L;
    private volatile long lastRestoreAtMillis = -1L;
    private volatile String lastError;

    public BackupChainService(OssProvider provider, BackupChainProperties properties) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    // ==================== 归档 ====================

    /**
     * 归档一个备份文件，并立即执行滚动保留。
     *
     * @param source    本地备份文件
     * @param objectKey 对象 key；传 null ⇒ 用「前缀 + 文件名 + 时间戳」自动生成
     * @return 归档产物描述
     */
    public BackupArtifact archive(Path source, String objectKey) throws IOException {
        return archiveInternal(source, objectKey);
    }

    /** 归档一个备份文件，object key 用默认命名（前缀 + 文件名 + 时间戳）。 */
    public BackupArtifact archive(Path source) throws IOException {
        return archiveInternal(source, null);
    }

    private BackupArtifact archiveInternal(Path source, String objectKey) throws IOException {
        archiveAttempts.incrementAndGet();
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(source)) {
            archiveFailures.incrementAndGet();
            lastError = "archive failed: source is not a regular file: " + source;
            log.error("z-oss 备份归档失败（不静默）: {}", lastError);
            throw new IOException("Backup source is not a regular file: " + source);
        }
        String bucket = properties.getBucket();
        String key = objectKey == null || objectKey.trim().isEmpty()
                ? defaultKey(source)
                : objectKey.trim();
        try {
            long size = Files.size(source);
            OssObjectSummary summary;
            try (InputStream in = Files.newInputStream(source)) {
                summary = provider.putObject(bucket, key, in, size, "application/octet-stream");
            }
            archives.incrementAndGet();
            lastArchiveAtMillis = System.currentTimeMillis();
            List<String> deleted = applyRetention();
            BackupArtifact artifact = new BackupArtifact(bucket, key, size, lastArchiveAtMillis,
                    summary == null ? null : summary.getEtag(), deleted);
            log.info("z-oss 备份归档完成: bucket={}，key={}，字节={}，滚动淘汰={} 份",
                    bucket, key, size, deleted.size());
            return artifact;
        } catch (IOException | RuntimeException e) {
            archiveFailures.incrementAndGet();
            lastError = "archive failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("z-oss 备份归档失败（不静默）: bucket={}，key={}，异常={}",
                    bucket, key, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 滚动保留：同一前缀下只留<b>最新 N 份</b>，其余删掉。
     *
     * @return 被淘汰（删除）的对象 key 列表
     */
    public List<String> applyRetention() throws IOException {
        List<String> deleted = new ArrayList<>();
        int retention = properties.getRetention();
        if (retention <= 0) {
            return deleted;   // <=0 ⇒ 只增不删
        }
        String bucket = properties.getBucket();
        String prefix = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix();
        List<OssObjectSummary> objects = new ArrayList<>();
        for (OssObjectSummary summary : provider.listObjects(bucket, prefix, 1000)) {
            if (summary == null || summary.getKey() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(summary.getIsFolder())) {
                continue;
            }
            objects.add(summary);
        }
        if (objects.size() <= retention) {
            return deleted;
        }
        // 最新在前：最后修改时间倒序；同一毫秒的用 key 倒序兜底（保证排序确定）
        objects.sort(Comparator
                .comparing((OssObjectSummary o) -> o.getLastModified() == null
                        ? new java.util.Date(0) : o.getLastModified())
                .reversed()
                .thenComparing(Comparator.comparing(OssObjectSummary::getKey).reversed()));
        for (int i = retention; i < objects.size(); i++) {
            String key = objects.get(i).getKey();
            try {
                provider.deleteObject(bucket, key);
                deleted.add(key);
                retentionDeletions.incrementAndGet();
            } catch (IOException | RuntimeException e) {
                lastError = "retention delete failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("z-oss 备份滚动淘汰失败（不静默）: bucket={}，key={}，异常={}",
                        bucket, key, e.getMessage(), e);
                throw e;
            }
        }
        if (!deleted.isEmpty()) {
            log.info("z-oss 备份滚动淘汰: bucket={}，前缀={}，保留 {} 份，删除 {} 份",
                    bucket, prefix, retention, deleted.size());
        }
        return deleted;
    }

    // ==================== 取回 ====================

    /** 列出当前保留的备份（最新在前）。 */
    public List<BackupArtifact> listBackups() {
        String bucket = properties.getBucket();
        String prefix = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix();
        List<BackupArtifact> result = new ArrayList<>();
        for (OssObjectSummary summary : provider.listObjects(bucket, prefix, 1000)) {
            if (summary == null || summary.getKey() == null
                    || Boolean.TRUE.equals(summary.getIsFolder())) {
                continue;
            }
            result.add(new BackupArtifact(bucket, summary.getKey(), summary.getSize(),
                    summary.getLastModified() == null ? -1L : summary.getLastModified().getTime(),
                    summary.getEtag(), Collections.<String>emptyList()));
        }
        result.sort(Comparator.comparingLong(BackupArtifact::getArchivedAtMillis).reversed());
        return result;
    }

    /**
     * 取回一份备份到本地文件（回读校验用）。
     *
     * @param objectKey 对象 key
     * @param target    本地目标文件（父目录不存在会创建）
     * @return 落地的文件
     */
    public Path restore(String objectKey, Path target) throws IOException {
        restoreAttempts.incrementAndGet();
        String bucket = properties.getBucket();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = provider.getObject(bucket, objectKey);
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            restores.incrementAndGet();
            lastRestoreAtMillis = System.currentTimeMillis();
            log.info("z-oss 备份取回完成: bucket={}，key={}，落地={}", bucket, objectKey, target);
            return target;
        } catch (IOException | RuntimeException e) {
            restoreFailures.incrementAndGet();
            lastError = "restore failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("z-oss 备份取回失败（不静默）: bucket={}，key={}，异常={}",
                    bucket, objectKey, e.getMessage(), e);
            throw e;
        }
    }

    // ==================== 观测 ====================

    /** 备份链计数（失败可见的抓手）。 */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("bucket", properties.getBucket());
        stats.put("keyPrefix", properties.getKeyPrefix());
        stats.put("retention", properties.getRetention());
        stats.put("archiveAttempts", archiveAttempts.get());
        stats.put("archives", archives.get());
        stats.put("archiveFailures", archiveFailures.get());
        stats.put("retentionDeletions", retentionDeletions.get());
        stats.put("restoreAttempts", restoreAttempts.get());
        stats.put("restores", restores.get());
        stats.put("restoreFailures", restoreFailures.get());
        stats.put("lastArchiveAtMillis", lastArchiveAtMillis);
        stats.put("lastRestoreAtMillis", lastRestoreAtMillis);
        stats.put("lastError", lastError);
        stats.put("backupCount", listBackups().size());
        return stats;
    }

    private String defaultKey(Path source) {
        String prefix = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix();
        String stamp = java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(System.currentTimeMillis()));
        return prefix + source.getFileName() + "-" + stamp;
    }

    // ==================== 值对象 ====================

    /** 一份备份产物的描述。 */
    public static final class BackupArtifact {

        private final String bucket;
        private final String key;
        private final long size;
        private final long archivedAtMillis;
        private final String etag;
        private final List<String> evictedByThisArchive;

        BackupArtifact(String bucket, String key, long size, long archivedAtMillis,
                       String etag, List<String> evictedByThisArchive) {
            this.bucket = bucket;
            this.key = key;
            this.size = size;
            this.archivedAtMillis = archivedAtMillis;
            this.etag = etag;
            this.evictedByThisArchive = Collections.unmodifiableList(new ArrayList<>(evictedByThisArchive));
        }

        public String getBucket() {
            return bucket;
        }

        public String getKey() {
            return key;
        }

        public long getSize() {
            return size;
        }

        public long getArchivedAtMillis() {
            return archivedAtMillis;
        }

        public String getEtag() {
            return etag;
        }

        /** 本次归档触发滚动淘汰时删掉的对象 key。 */
        public List<String> getEvictedByThisArchive() {
            return evictedByThisArchive;
        }
    }
}
