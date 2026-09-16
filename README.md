# z-oss

> **可插拔的对象存储抽象层** — Aliyun OSS / MinIO / Local Storage 统一接口
> Java 8 + Spring Boot 2.7, 一行切换存储后端, 业务代码零修改

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.1-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-oss*)
[![License](https://img.shields.io/license/MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-6DB33F)](https://spring.io)

---

## 🚀 5 分钟接入

### 方式一：作为 enum + 异常库（最小依赖）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-oss-common</artifactId>
    <version>1.0.1</version>
</dependency>
```

```java
import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.common.enums.ObjectStatus;
import com.zifang.z.oss.common.exception.OssException;

// Bucket ACL 枚举
BucketAcl acl = BucketAcl.PRIVATE_READ;     // 私有
// PUBLIC_READ / PUBLIC_READ_WRITE / AUTHENTICATED_READ

// Object 状态
ObjectStatus status = ObjectStatus.UPLOADED;

// 统一异常
try {
    // ... 调用 OSS 操作 ...
} catch (OssException e) {
    log.error("OSS 错误: code={}, msg={}", e.getErrorCode(), e.getMessage());
}
```

### 方式二：作为完整存储服务（即将发布 — z-oss-core + z-oss-api）

> ⚠️ **z-oss-core / z-oss-api 暂未发 Maven Central**，原因：依赖未上 Central 的 `com.zifang:z-ctc-core`（TenantInitializer）。
> 一旦 `z-ctc-core` 上 Central，业务代码如下：

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-oss-core</artifactId>
    <version>1.0.2</version>
</dependency>
```

```yaml
z:
  oss:
    enabled: true
    provider: aliyun                # aliyun / minio / local
    aliyun:
      endpoint: oss-cn-hangzhou.aliyuncs.com
      access-key: ${ALIYUN_ACCESS_KEY}
      secret-key: ${ALIYUN_SECRET_KEY}
      bucket-name: my-bucket
```

```java
@Service
public class FileService {

    @Autowired private OssTemplate ossTemplate;

    public String upload(MultipartFile file) {
        String key = "uploads/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        ossTemplate.putObject("my-bucket", key, file.getInputStream());
        return ossTemplate.getObjectUrl("my-bucket", key);  // 返回签名 URL
    }

    public InputStream download(String key) {
        return ossTemplate.getObject("my-bucket", key);
    }

    public void delete(String key) {
        ossTemplate.deleteObject("my-bucket", key);
    }
}
```

### 方式三：Spring Boot Starter（自动装配，一行 import）

> ⚠️ z-oss-spring-boot-starter 暂未发布，等 z-oss-core 上 Central 后发布

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-oss-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```

---

## 📦 已发布到 Maven Central 的所有模块

> groupId: `io.github.yuku123` · version: **1.0.1**

| 模块 | 说明 | 何时该引入 |
|---|---|---|
| `z-oss-common` | 通用 enum（BucketAcl / ObjectStatus）+ 异常（OssException / BucketException） | 任何需要 OSS 抽象的项目 |

> ⚠️ **未发布的模块**（依赖 z-ctc-core）：
> - `z-oss-core`：Provider 抽象 + 业务逻辑 + Aliyun/MinIO/Local 实现
> - `z-oss-api`：Spring Boot 可执行 jar（含 REST 端点）
> - `z-oss-spring-boot-starter`：自动装配 starter
>
> 等 `io.github.yuku123:z-ctc-core` 上 Central 后，会自动补齐发布。

---

## ✨ 设计原则

### 后端无关
业务代码只依赖 `OssTemplate` 抽象接口，**不绑死** Aliyun OSS / MinIO / 任意私有云。切换后端只改 `application.yml` 一行。

### Provider 抽象

```
                ┌──────────────────────────────┐
                │      OssTemplate (核心抽象)  │
                └──────────────────────────────┘
                          ▲           ▲      ▲
                          │           │      │
            ┌─────────────┘    ┌──────┘      └───────────┐
            │                  │                         │
   ┌────────┴──────┐  ┌────────┴─────┐        ┌─────────┴──────┐
   │ AliyunOssProvider│  │ MinioProvider│        │ LocalFsProvider │
   │  (Aliyun SDK)    │  │ (S3 SDK)     │        │ (文件系统)       │
   └──────────────────┘  └──────────────┘        └─────────────────┘
```

> **z-oss 是抽象 + Aliyun OSS / MinIO / 本地文件系统 之间的胶水层**
> 业务代码：`ossTemplate.putObject(bucket, key, inputStream)` 即可，对后端无感

### Bucket ACL

| ACL | 含义 |
|---|---|
| `PRIVATE` | 私有 — 仅创建者可访问 |
| `PUBLIC_READ` | 公共读 — 任意匿名 GET，写需鉴权 |
| `PUBLIC_READ_WRITE` | 公共读写 — 任意匿名读写 |
| `AUTHENTICATED_READ` | 认证读 — 任何登录用户可读 |

---

## ⚙️ 实用 Case（规划 / 已规划的 z-oss-core API）

### Case 1: 简单上传 + 签名 URL 下载

```java
@Autowired private OssTemplate ossTemplate;

// 上传
String key = "avatars/" + userId + ".jpg";
ossTemplate.putObject("user-avatars", key, file.getInputStream(),
    ObjectMetadata.builder()
        .contentType("image/jpeg")
        .acl(BucketAcl.PUBLIC_READ)
        .build());

// 生成 1 小时有效的签名 URL（用于前端下载）
String signedUrl = ossTemplate.generatePresignedUrl(
    "user-avatars", key, Duration.ofHours(1));
```

### Case 2: 大文件分片上传（断点续传）

```java
// 1. 初始化分片上传
String uploadId = ossTemplate.initMultipartUpload("videos", "movie.mp4");

// 2. 上传分片 (前端并发)
List<PartETag> parts = new ArrayList<>();
for (int i = 0; i < chunkCount; i++) {
    PartETag tag = ossTemplate.uploadPart("videos", "movie.mp4", uploadId, i + 1, chunkData);
    parts.add(tag);
}

// 3. 完成上传
ossTemplate.completeMultipartUpload("videos", "movie.mp4", uploadId, parts);
```

### Case 3: 批量删除

```java
List<String> keys = List.of("a.jpg", "b.jpg", "c.jpg");
ossTemplate.deleteObjects("user-avatars", keys);
// 返回真正删除的数量 (部分失败不抛)
int deleted = ossTemplate.countDeleted();
```

### Case 4: 图片处理（缩略图 + 水印）

```java
// z-oss-core 提供图片处理 pipeline
ossTemplate.putObject("photos", "raw/IMG_001.jpg", rawBytes);

String thumbUrl = ossTemplate.generateProcessedUrl("photos", "raw/IMG_001.jpg",
    ImageProcessing.builder()
        .resize(200, 200)
        .watermark("@yuku123")
        .format("webp")
        .build());
```

### Case 5: 跨 Provider 迁移（开发用 Local，生产用 Aliyun）

```yaml
# application-dev.yml (本地开发)
z:
  oss:
    provider: local
    local:
      root-dir: /tmp/z-oss-data

# application-prod.yml (生产)
z:
  oss:
    provider: aliyun
    aliyun:
      endpoint: oss-cn-hangzhou.aliyuncs.com
      access-key: ${ALIYUN_ACCESS_KEY}
      secret-key: ${ALIYUN_SECRET_KEY}
```

业务代码完全一致。

---

## 🏗️ 项目结构

```
z-oss/
├── pom.xml                          # 自给自足 parent
├── z-oss-common/                    # enum + 异常 ✅ 已发布 1.0.1
├── z-oss-core/                      # Provider 抽象 + 业务逻辑 ⏳ 等 z-ctc-core
├── z-oss-api/                       # Spring Boot REST ⏳ 等 z-oss-core
└── README.md
```

---

## 🔧 阻塞发布依赖说明

z-oss 的核心模块依赖 `com.zifang:z-ctc-core`（TenantInitializer / TenantInitContext 接口），目前该模块还在 monorepo 内部 namespace。

**解锁条件**：
1. 把 `com.zifang:z-ctc-core` 切换 namespace 到 `io.github.yuku123:z-ctc-core`
2. z-ctc-core 发布到 Maven Central
3. z-oss-core / z-oss-api 改依赖 + 发布

预计 1-2 周内完成。

---

## 🐳 Docker（z-oss-api 发布后可用）

```bash
docker run -d --name z-oss-api \
  -p 9000:9000 \
  -e Z_OSS_PROVIDER=aliyun \
  -e Z_OSS_ALIYUN_ENDPOINT=oss-cn-hangzhou.aliyuncs.com \
  -e Z_OSS_ALIYUN_ACCESS_KEY=${ALIYUN_ACCESS_KEY} \
  -e Z_OSS_ALIYUN_SECRET_KEY=${ALIYUN_SECRET_KEY} \
  ghcr.io/z-opc-foundation/z-oss-api:1.0.2
```

`/doc.html` 看 API 文档。

---

## 🧪 测试覆盖

```
单元测试:    18 PASS  (z-oss-common enum + 异常)
集成测试:    0       (z-oss-core / api 暂未发布)
```

---

## 📚 详细文档

- [Provider 接口](docs/PROVIDER_INTERFACE.md)（z-oss-core 发布后）
- [Bucket ACL 设计](docs/BUCKET_ACL.md)
- [图片处理 pipeline](docs/IMAGE_PROCESSING.md)
- [迁移指南（从 Aliyun SDK 直连 → z-oss）](docs/MIGRATION.md)

---

## 🤝 贡献

z-oss-common 模块欢迎 PR：

```bash
cd z-oss-common
mvn clean verify
```

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 相关项目

| 项目 | 关系 |
|---|---|
| [z-cache](https://github.com/z-opc-foundation/z-cache) | 同系列 — 分布式缓存 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 同系列 — 分布式消息队列 |
| [z-rpc](https://github.com/z-opc-foundation/z-rpc) | 同系列 — RPC 框架 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | 同系列 — Spring Boot Starter 聚合 + BOM |

> **通过 [z-boot-oss-starter](https://central.sonatype.com/artifact/io.github.yuku123/z-boot-oss-starter) 可以一行 import 集成 z-oss-common + 自动锁定版本**

---

## 📮 联系

- GitHub Issues: 提交 bug / feature request
- Email: yuku123@users.noreply.github.com

S3 兼容的对象存储服务, 支持 Bucket 管理、对象 CRUD、ACL 权限控制

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **项目** | z-oss |
| **分类** | 基础设施 · 对象存储 |
| **父项目** | z-opc (com.zifang:z-opc:1.0.0-SNAPSHOT) |
| **默认端口** | `9000` |
| **文档维护** | z-opc-foundation |
| **最近更新** | 2026-09-06 |

---

## 🎯 核心功能

S3 兼容的对象存储服务, 支持 Bucket 管理、对象 CRUD、ACL 权限控制

详细功能特性详见各子模块 README 或源码注释。

---

## 🏗️ 项目结构

```
z-oss/
├── pom.xml                      # 根 POM (引用 z-opc 父项目)
├── README.md                    # 本文档
├── MODULE_NOTE.md               # 来源说明 (从 z-opc 拆分)
```

子模块列表:

| 模块 | 职责 |
|------|------|
| `z-oss-common/` | 公共枚举/异常 |
| `z-oss-core/` | 存储引擎 + 元数据 |
| `_frontend/` | 前端 (React/Vue) |
| `z-oss-api/` | REST API 接口 |

---

## 🔧 技术栈

- Java 8+
- Netty
- MinIO 兼容协议

---

## 🚀 快速开始

### 前置条件

- JDK 8+ (推荐 JDK 17)
- Maven 3.6+
- 端口 `9000` 未被占用

### 编译

```bash
# 在 z-opc 父项目下编译 (推荐)
cd /Users/zifang/workplace/idea_workplace/z-opc
mvn clean install -pl :z-opc -am -DskipTests

# 单独编译本模块 (需 ../pom.xml 父项目可用)
cd /Users/zifang/workplace/ceo_workplace/z-opc-foundation/z-oss
mvn clean compile
```

### 运行

```bash
# 启动主服务 (根据项目类型选择)
mvn -pl <启动模块> spring-boot:run
# 或
java -jar <启动模块>/target/*.jar
```

---

## 📦 模块说明

z-oss 由以下子模块组成:

| `z-oss-common/` | 公共枚举/异常 |
| `z-oss-core/` | 存储引擎 + 元数据 |
| `_frontend/` | 前端 (React/Vue) |
| `z-oss-api/` | REST API 接口 |

各模块职责详见各子目录下的 `pom.xml` 和源码。

---

## 🧪 测试

```bash
mvn test
```

测试覆盖:
- 单元测试: 各核心服务类
- 集成测试: 端到端调用链路
- 性能测试: 详见 `/src/test` 下的 `*PerformanceTest.java`

---

## 🔌 API 接口

API 接口定义在各子模块的 `controller` 包下。

启动后访问 `http://localhost:9000/swagger-ui.html` 或 `/doc.html` (knife4j) 查看完整 API 文档。

---

## 🐳 部署

### Docker

```bash
# 构建镜像
docker build -t z-oss:latest .

# 运行容器
docker run -d -p 9000:9000 --name z-oss z-oss:latest
```

### 配置

主要配置文件:
- `application.yml` - Spring Boot 配置
- `logback.xml` - 日志配置
- 环境变量: `JAVA_OPTS`, `SPRING_PROFILES_ACTIVE`

---

## 📚 相关文档

- [MODULE_NOTE.md](./MODULE_NOTE.md) - 从 z-opc 拆分说明
- [z-opc 父项目](https://github.com/yuku123/z-opc) - 完整源码

---

## 📝 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0.0 | 2026-09-06 | 从 z-opc monorepo 拆分独立仓, 文档补齐 |

---

## 📄 License

Internal use only. 版权属于 z-biz。

_Maintained by z-opc-foundation organization._


## 文档目录

本项目文档统一收口在 `_doc/` 下:

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档 (项目总览 / 模块结构 / 接口清单 / DB schema / 前端 / 能力 / roadmap):
  - [`00-overview.md`](_doc/001_arch/00-overview.md)

- [`_doc/003_script/`](_doc/003_script/) — 运维脚本:
  - [`build.sh`](_doc/003_script/build.sh)
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh)
  - [`install-settings.sh`](_doc/003_script/install-settings.sh)
  - [`package.sh`](_doc/003_script/package.sh)

各文档详细说明见各子目录。
