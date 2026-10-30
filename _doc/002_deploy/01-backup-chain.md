# 备份链与滚动保留（z-oss）

> 任务：TASK-20261012-027 · 范围：z-oss 仓内的备份链
> 用途：z-graph 的 `exportSnapshot` 产物 → **异地第二份**（对象存储），按「保留最新 N 份」滚动淘汰。

## 0. 一句话

`BackupChainService` 把备份文件归档到对象存储，归档完立刻按 **保留最新 N 份**（N 可配）
淘汰更老的备份；每一步的成败都记计数 + 最后一错，**失败照抛，不静默**。

## 1. 代码位置

| 层 | 位置 | 作用 |
|---|---|---|
| 服务 | `z-oss-core/src/main/java/com/zifang/z/oss/core/backup/BackupChainService.java` | 归档 / 滚动保留 / 取回 / 计数 |
| 配置 | `z-oss-core/src/main/java/com/zifang/z/oss/core/backup/BackupChainProperties.java` | `oss.backup.*` |
| 装配 | `z-oss-core/src/main/java/com/zifang/z/oss/core/OssCoreAutoConfiguration.java` → `backupChainService(...)` | **`oss.backup.enabled=true` 才装配** |
| API | `z-oss-api/src/main/java/com/zifang/z/oss/api/controller/BackupController.java` | `POST /api/v1/backup/archive`、`GET /api/v1/backup/list`、`GET /api/v1/backup/stats` |
| 演练 | `z-oss-core/src/test/java/com/zifang/z/oss/core/backup/BackupChainRehearsalTest.java` | 归档 + 保留 + 取回 + 失败可见 |

## 2. 配置

```yaml
oss:
  backup:
    enabled: true            # 总开关，默认 false
    bucket: z-graph-backup   # 备份归档落在哪个 bucket
    key-prefix: zgraph/      # 滚动保留只在这个前缀下算
    retention: 7             # 保留最新 N 份；<=0 表示只增不删
```

- **默认不启用**：备份链会**删对象**，没配 retention 就让它跑起来等于给了一把没上膛的枪。
- 没启用时 `BackupController` 的三个端点返回 **503**（"拒绝假装归档成功"），不是 200 空结果。

## 3. 语义

### 3.1 归档 `archive(Path[, objectKey])`

1. 读本地备份文件 → `provider.putObject(bucket, key, ...)`；
2. object key 给了就用给的；不给则默认 `keyPrefix + 文件名 + 时间戳`（`yyyyMMdd-HHmmss`）；
3. 立刻执行滚动保留（见 3.2）；
4. 返回 `BackupArtifact`（bucket / key / size / archivedAtMillis / etag / 本次淘汰掉的 key 列表）。

### 3.2 滚动保留 `applyRetention()`

- 只看 `bucket` + `keyPrefix` 下的**文件对象**（目录对象跳过）；
- 排序：**最后修改时间倒序**，同一毫秒用 **key 倒序**兜底（保证排序确定、可复现）；
- 保留前 N 份，**其余删掉**；
- `retention <= 0` ⇒ 只增不删（不删任何东西）。

> ⚠️ 保留策略是"按 key 前缀"算的：往同一个前缀里塞**别的**东西也会被卷进淘汰。
> 备份请用独立前缀（默认 `zgraph/`）。

### 3.3 取回 `restore(objectKey, target)`

把归档里的备份取回本地文件（父目录不存在会创建），供**回读校验**用。
取回的字节必须与原件一致 —— 回读校验的第一道闸。

### 3.4 这一层**不碰数据库**

直接用 `OssProvider`（local / aliyun / s3 都行）读写对象，所以：

- 备份链能不能用，跟对象表里有没有行**无关**；
- DB 连不上时，备份链照样能把文件送出去；
- 反过来：这些备份对象**不会**出现在对象表里（`GET /api/v1/object/list` 看不到它们），
  备份链自己有 `GET /api/v1/backup/list`。

## 4. 失败可见

任何一步失败都：**记 ERROR 日志 + 计数 +1 + `lastError` 记录摘要 + 照抛**。

| 计数 | 含义 |
|---|---|
| `archiveAttempts` / `archives` | 归档尝试数 / 成功数 |
| `archiveFailures` | 归档失败数 |
| `retentionDeletions` | 滚动淘汰删掉的对象数 |
| `restoreAttempts` / `restores` / `restoreFailures` | 取回尝试 / 成功 / 失败 |
| `backupCount` | 当前保留的备份份数 |
| `lastError` | 最后一错的摘要（无错时为 null） |

备份链最坏的失败模式是"以为在备份，其实什么都没发生" —— 所以失败一律**响**。

## 5. 与 z-graph 的对接

```
z-graph  POST /maintenance/snapshot?branch=main&stream=true   → 快照字节（X-Snapshot-Sha256）
   ↓ 调用方（z-schedule）把字节 POST 给
z-oss    POST /api/v1/backup/archive  (multipart file)          → 归档 + 滚动保留
```

- z-graph 侧的口径见 z-graph 仓 `_doc/002_deploy/01-gc-reachability.md` 第 9 节；
- 定时由 z-schedule 触发（handler 见 z-schedule 仓 `GraphMaintenanceHandler`）；
- **回读校验**（产物能不能真的用）由 z-graph 侧 `SnapshotImportReadbackTest` 做：
  把 z-oss 取回的那份 `importSnapshot` 进新仓库，逐行比对内容清单。

## 6. 纪律

- 上线前先在**测试目录**演练（演练用 local provider，落临时目录）；
  **生产 bucket 的首轮只观察不自动跑**。
- 凭据（AK/SK）只从环境变量进，**不进版本库**。
