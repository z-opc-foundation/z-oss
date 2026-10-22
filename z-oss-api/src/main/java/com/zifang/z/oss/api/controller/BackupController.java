package com.zifang.z.oss.api.controller;

import com.zifang.util.core.meta.Result;
import com.zifang.z.oss.common.exception.OssException;
import com.zifang.z.oss.core.backup.BackupChainService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份链 API（TASK-20261012-027）。
 *
 * <p>把备份产物（典型是 z-graph 的 {@code exportSnapshot} 文件）归档到对象存储，
 * 并按「保留最新 N 份」滚动淘汰。定时由 z-schedule 打过来，
 * 归档完的那一份就是**异地第二份**。</p>
 *
 * <p>鉴权沿用 {@code /api/v1/**} 的 AK/SK Header（{@code AuthenticationInterceptor}）。
 * <b>备份链本身不依赖数据库</b>，但这个 API 在 z-oss-api 里 ⇒ 鉴权依赖用户表。</p>
 *
 * <p>备份链没启用（{@code oss.backup.enabled != true}）时，这些端点返回 503 ——
 * 而不是"看起来成功但什么都没备份"。</p>
 */
@RestController
@RequestMapping("/api/v1/backup")
public class BackupController {

    private final ObjectProvider<BackupChainService> chainProvider;

    public BackupController(ObjectProvider<BackupChainService> chainProvider) {
        this.chainProvider = chainProvider;
    }

    /**
     * POST /api/v1/backup/archive — 归档一份备份（multipart 文件），并执行滚动保留。
     *
     * @param file      备份文件（必填）
     * @param objectKey 可选：指定对象 key；不给则用「前缀 + 原文件名 + 时间戳」
     */
    @PostMapping("/archive")
    public Result<?> archive(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "objectKey", required = false) String objectKey)
            throws IOException {
        BackupChainService chain = requireChain();
        String originalName = file.getOriginalFilename() == null
                ? "backup.bin" : file.getOriginalFilename();
        Path temp = null;
        try {
            temp = Files.createTempFile("zoss-backup-", "-" + sanitize(originalName));
            file.transferTo(temp.toFile());
            BackupChainService.BackupArtifact artifact =
                    objectKey == null || objectKey.trim().isEmpty()
                            ? chain.archive(temp)
                            : chain.archive(temp, objectKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("bucket", artifact.getBucket());
            body.put("key", artifact.getKey());
            body.put("size", artifact.getSize());
            body.put("archivedAtMillis", artifact.getArchivedAtMillis());
            body.put("evicted", artifact.getEvictedByThisArchive());
            body.put("backupCount", chain.listBackups().size());
            return Result.success(body);
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** GET /api/v1/backup/list — 当前保留的备份（最新在前）。 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        BackupChainService chain = requireChain();
        List<Map<String, Object>> body = new ArrayList<>();
        for (BackupChainService.BackupArtifact artifact : chain.listBackups()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket", artifact.getBucket());
            item.put("key", artifact.getKey());
            item.put("size", artifact.getSize());
            item.put("archivedAtMillis", artifact.getArchivedAtMillis());
            body.add(item);
        }
        return Result.success(body);
    }

    /** GET /api/v1/backup/stats — 备份链计数（失败可见的抓手）。 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(requireChain().stats());
    }

    private BackupChainService requireChain() {
        BackupChainService chain = chainProvider.getIfAvailable();
        if (chain == null) {
            throw new OssException(503,
                    "备份链未启用（oss.backup.enabled != true）—— 拒绝假装归档成功");
        }
        return chain;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
