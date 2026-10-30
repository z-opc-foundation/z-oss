# z-oss

> 对象存储服务 —— z-oss 自己既是一个可插拔的对象存储后端（`local` 自持后端，对标 MinIO standalone），
> 也是一个多平台对接层（`aliyun` / `s3`，后者覆盖 AWS S3 与自建 MinIO）。
> 消费方只需依赖 `z-oss-spring-boot-starter` 一件，注入 `OssTemplate` 即可用对象/桶/分片全能力。

它解决的是两件事：一是"业务侧不想认识具体云厂商"——附件、头像、导入导出文件过去各处直连 SDK，
换环境就要改代码；二是"不想为了存附件再运维一套 MinIO"——`local` 后端自带存储引擎、流式落盘、
分片上传与签名 URL，不接任何外部平台也能独立提供对象存储服务。

## 模块

| 模块 | 定位 |
|------|------|
| `z-oss-common` | 纯 enum / exception，无 Spring 依赖 |
| `z-oss-core` | `OssProvider` SPI、存储引擎、各后端实现、领域服务 |
| `z-oss-spring-boot-starter` | **消费方入口**：自动装配 + `OssTemplate` 门面 + S3/MinIO 平台对接 |
| `z-oss-api` | 可独立运行的 REST 服务（Bucket/Object/用户/鉴权） |

`OssProviderManager` 按 `oss.provider` 配置项挑出当前激活实现；对象元数据（桶、对象、用户）落 MySQL，
字节流落 provider。业务方既可以只引 `z-oss-common` 拿枚举与异常，也可以把 `z-oss-api` 当独立服务
（`ZOssApplication`）或当可嵌入的自动装配模块（`OssWebAutoConfiguration` + `spring.factories`）使用。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-oss`（对象存储服务） |
| **Maven 坐标** | `io.github.yuku123:z-oss` · `z-oss-common` · `z-oss-core` · `z-oss-api` |
| **当前版本** | `1.0.5`（根 POM 用的是普通 `<version>`，**不是** `<revision>` + CI-friendly，子模块 `<parent>` 里写死 `1.0.5`） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.2.0`（`<relativePath/>` 留空，parent 在 repo1 不在磁盘；第三方地板与兄弟仓权威表均已并入 parent 自身） |
| **Maven Central** | 已发布（2026-09-30 ranged GET `-r 0-0` 实测，均 **206**）：`z-oss-1.0.4.pom`、`z-oss-common-1.0.4.pom`、`z-oss-core-1.0.4.pom`、`z-oss-api-1.0.4.pom`、`z-oss-api-1.0.4-exec.jar`。`maven-metadata.xml` 上四个坐标的历史版本为 `1.0.1`（根/common）、`1.0.2`–`1.0.4`（core/api） |
| **默认端口** | `8082`（`z-oss-api/src/main/resources/z-oss-standalone.yml` 的 `server.port`（**刻意不叫** `application.yml`，见 `_doc/001_arch/02_库件配置约束.md`）；**未配置** `server.servlet.context-path`，所以没有 `/oss` 之类前缀） |
| **运行口径** | Java 17（`java.version=17` 由 parent 下发）· Spring Boot 3.3.13 · 日志实现 Log4j2 `2.17.2`（本仓按坐标逐条压回，刻意不与地板的 2.25.4 同值） |
| **最近更新** | 2026-09-30 |

> 消费方注意：fleet 里 `z-oss` 那一格当前面值也是 `1.0.4`，与 HEAD 同值；根 POM 的
> `dependencyManagement` 把自家 4 个坐标钉成 `${project.version}`，所以抬根版本时不会把旧版
> `z-oss-core` 字节码打进 `z-oss-api` 的包里。

---

## 🎯 能力清单

每一条都能对应到本仓的一个类或一个端点，写不出实现的一律不列（见文末「尚未实现」）。

| 能力 | 实现位置 | 说明 |
|------|----------|------|
| Provider 抽象与切换 | `z-oss-core` `provider/OssProvider` + `provider/OssProviderManager` | `OssProviderManager#provider()` 按 `oss.provider` 取激活实现，取不到抛 `IllegalStateException` 并列出已注册类型 |
| 本地文件系统后端 | `provider/impl/LocalOssProvider`（`getType()` 返回 `local`）+ `storage/FileStorageEngine` | 默认后端（`@ConditionalOnProperty(name="oss.provider", havingValue="local", matchIfMissing=true)`），落到 `oss.storage.file.root` 目录 |
| 阿里云 OSS 后端 | `provider/impl/AliyunOssProvider` + `AliyunOssProperties`（`getType()` 返回 `aliyun`） | 走 `aliyun-sdk-oss` 的 `OSSClientBuilder`；`bucket-prefix` 由 `resolveBucketName` 统一加前缀，签名 URL 由 SDK 生成 |
| 桶 CRUD / ACL / Policy | `api/controller/BucketController` → `IOssBucketService` / `OssBucketServiceImpl` | 创建、删除、列表与详情、更新（acl/region/policy）、ACL 读写、Policy 读写删、HEAD 存在性校验 |
| 对象上传 / 下载 / 删除 / 列表 | `api/controller/ObjectController` → `IOssObjectService` / `OssObjectServiceImpl` | `uploadObject` / `downloadObject` / `deleteObject` / `getObject` / `listObjects`（支持 `prefix`） |
| 对象复制 · 批量删除 · 建目录 | `ObjectController` `POST /object/copy`、`POST /object/batch-delete`、`POST /folder` | 目录即 `is_folder=1` 的零字节对象（`createFolder`） |
| 预签名下载 URL | `GET /object/url`（默认 `expires=3600` 秒）签发，`GET /object/download` 校验并直出字节 | Aliyun 返回 SDK 真实签名地址；Local 走 `ZOssSignature` 自己的 HMAC-SHA256 签名（`oss.local.access-key`），校验失败/过期一律 401 语义 |
| S3 协议后端（AWS S3 / MinIO / Ceph RGW） | `spring/provider/S3OssProvider` + `S3OssProperties`（`getType()` 返回 `s3`），在 **starter** 件内 | 同一套 S3 协议覆盖 AWS 与自建 MinIO，差别只在 `endpoint` 与 `path-style`；用 `io.minio:minio` 而非 aws-sdk，避免把 jackson/okhttp 家族拖进消费方依赖树 |
| 消费方门面 | `spring/template/OssTemplate`（在 **starter** 件内） | 注入一个 bean 覆盖对象/桶/分片全能力；无状态，业务代码不感知当前后端 |
| starter 自动装配 | `z-oss-spring-boot-starter` + `AutoConfiguration.imports` 与 `spring.factories` 双注册 | 消费方只依赖 starter 一件。各后端 bean 各自 `@ConditionalOnProperty`，配了谁才实例化谁；`oss.enabled=false` 整体关停 |
| 分片上传 | `OssProvider` 的 `initiate/uploadPart/complete/abort` 四方法（默认实现明确报"不支持"），Local 后端原生支持；端点 `POST /object/multipart/{init,part,complete,abort}` | 分片先落 `root/.zmultipart/<uploadId>/`，归并时按 partNumber 顺序流式拼进目标文件再原子改名，全程不把对象读进堆。**S3 后端不支持**（8.x 客户端没暴露 multipart 的高层 API） |
| 服务端复制 / 批量删除 / 范围读 | `OssProvider` 的 `copyObject` / `batchDeleteObjects` / `getObject(offset,length)` 默认实现；S3 后端走真协议（CopyObject / DeleteObjects / Range） | 同桶改名不下载再上传一遍 |
| 流式落盘与原子写 | `storage/FileStorageEngine#store` | 边读边算 MD5 边写临时文件再原子改名；原实现先 `ByteArrayOutputStream` 收全量，峰值堆 ≈ 文件大小 |
| 下载真二进制直出 | `ObjectController#downloadObject`（`StreamingResponseBody`）+ Range 支持 | 原实现返回 `Result.success(byte[])`，字节被 Jackson 序列化成 JSON，`Content-Type`/`Content-Length`/`ETag` set 在一个随即丢弃的 `HttpHeaders` 上 |
| 对象元数据（HEAD） | `HEAD /object/meta` | 返回 key / size / contentType / etag / lastModified，缺失时 404 |
| 桶统计 | `GET /bucket/stats` → `OssObjectServiceImpl#getBucketStats` | 键名实测为 `objectCount` / `totalSize` / `activeProvider` |
| 自有用户体系与 AK/SK | `api/controller/UserController` → `IOssUserService` / `OssUserServiceImpl` | 注册时生成 accessKey/secretKey，登录签发 `accessKey:secretKey` 形态的 token，另有改用户名、重置密钥、改密码 |
| Header 鉴权 | `api/config/AuthenticationInterceptor` | 只认 `X-Zoss-Access-Key` + `X-Zoss-Secret-Key` 两个请求头；缺头 401、SK 不匹配 401、`status != 1` 403；query 传密钥那条旁路已关闭 |
| 统一异常出口 | `api/config/OssGlobalExceptionHandler` + `z-oss-common/exception/*` | `OssException` 的 `code` 直接映射 HTTP 状态（`HttpStatus.valueOf`），其余异常收敛成 500 |
| 模块化数据源 | `api/config/OssModuleDataSource`（继承 z-boot `ModuleDataSourceTemplate`） | `z.base.db.oss.*` 缺省回退 `z.base.db.default.*`，产出 `dataSourceOss` + `sqlSessionFactoryOss`，Druid 连接保活由模板统一下发 |
| 自动装配 | `core/OssCoreAutoConfiguration`、`api/web/config/OssWebAutoConfiguration`（`META-INF/spring.factories`） | 后者被 main-starter 自动加载，并用 `excludeFilters` 排掉 demo 启动类 `ZOssApplication`，避免 `@MapperScan` 双通道抢注 mapper |
| 扩展点（SPI 缝） | `core/hook/OssTenantHook`、`BucketLifecycleHook`、`OssInitializer` | **本仓只有接口，0 个实现、0 处调用**；`OssCoreAutoConfiguration` 的 `@ComponentScan` 还列了一个并不存在的 `com.zifang.z.oss.core.init` 包 |

尚未实现（实测 0 命中，别按它接入）：阿里云之外的第二套云厂商对接（S3 协议后端已实现但仅本地分片
不支持，见下）、图片处理 pipeline、`deleteObjects`+`countDeleted`、CDN 与在线预览。
⚠ 分片上传的**过期巡检**没有实现：客户端开了分片又断线不调 abort 时，分片会一直占着
`oss.storage.file.root/.zmultipart`，需靠运维定期清。

---

## 🏗️ 项目结构

真实目录树（按本仓现状，不按规范理想形态描画）：

```
z-oss/
├── pom.xml                # 根聚合 POM：parent=z-boot-parent:1.2.0，<version>1.0.4</version>，4 个 module
├── z-oss-common/          # 纯 enum + exception，<dependencies> 为空（刻意不引 Spring）
├── z-oss-core/            # Provider 抽象 + domain(MyBatis-Plus 实体/Mapper/Service) + storage + hook
├── z-oss-api/             # REST 层 + 启动类 + 数据源装配 + z-oss-standalone.yml（**不叫** application* ⇒ 不劫持宿主；可独立跑，也可被 main-starter 引入）
├── _frontend/             # Vite + React 19 + antd 6 的 npm 工程；根 POM 的 <modules> 里该条已注释，不进 Maven reactor
├── _deploy/
│   └── Dockerfile         # ⚠ 部署资产在仓库根的 _deploy/，不在 _doc/002_deploy/（见「部署」一节）
├── sql/                   # 空目录，git 未跟踪任何文件；真正的 DDL 在 _doc/002_deploy/init/
├── _doc/                  # 001_arch（仅 00-overview.md）· 002_deploy/init（2 个 SQL）· 003_script（4 个脚本）· 004_skill（空）
└── LICENSE                # MIT
```

各模块职责（读源码得到，不是抄 POM 描述）：

- **`z-oss-common`** — `enums/BucketAcl`（4 值，code 为 `private` / `public-read` / `public-read-write` / `authenticated-read`）、
  `enums/ObjectStatus`（只有 `NORMAL(1)` / `DELETED(0)`）、`exception/OssException`（`getCode()`，默认 500）
  及其两个子类 `BucketException`、`ObjectException`。该 POM 的 `<dependencies>` 是空的。
- **`z-oss-core`** — `provider`（`OssProvider` 接口 + `OssProviderManager` 门面 + `Aliyun`/`Local` 两个实现）、
  `storage`（`StorageEngine` 接口、`FileStorageEngine` 本地实现、`FileStorageProperties`）、
  `domain`（`OssUser`/`OssBucket`/`OssObject`/`OssMultipart`/`OssPart` 实体、3 个 Mapper、3 组 Service 接口与实现）、
  `file`（`FileStorageService` 门面：`of(byte[])` + `getAllUrl(String)`，由 `FileStorageServiceImpl` 实现并注册成
  `fileStorageService` bean，**当前 REST 层没有调用它**，链路走的是 `IOssObjectService`）、`hook`。
- **前端产物落位** — `_frontend` 的构建产物只放 **`z-oss-api/src/main/resources/static/`**（`index.html` + `assets/`），
  被打包进 jar 后由 Spring Boot 在 `/` 直接对外提供。**`z-oss-core` 不再带任何 static**：
  2026-10-18 实测两处都在时，fat jar 里 `BOOT-INF/lib/z-oss-core.jar` 的 classpath 排在
  `BOOT-INF/classes` 前面，旧的前端会赢，控制台一直显示上一个版本的界面而完全无报错。
- **`z-oss-api`** — 6 个 Controller（40 个方法级映射，见 API 一节）、`AuthenticationInterceptor`、`WebMvcConfig`
  （只拦 `/api/v1/**`，放行 register/login）、`OssGlobalExceptionHandler`、`OssMybatisPlusConfig`、
  `OssModuleDataSource`、DTO/VO、`ZOssApplication`、`OssWebAutoConfiguration` + `spring.factories`。

> **纠偏**：`z-oss-core/pom.xml` 里那段"当前不在 aggregator 中（已临时排除）"的注释、
> 以及 `_doc/003_script/deploy_maven_center.sh` 头部同时写着 `✅ z-oss-core 已解耦` 和
> `⏸ z-oss-core 已排除` 的两行，都是历史残留。根 POM 的 `<modules>` 今天实实在在列了
> `z-oss-common`、`z-oss-core`、`z-oss-spring-boot-starter`、`z-oss-api` 四个模块；
> 其中 `z-oss-common`、`z-oss-core`、`z-oss-api` 三个坐标的 1.0.4 已实测可从 Central 回读
> （`z-oss-spring-boot-starter` 是消费方入口，不在本次回读范围内）。
> 四个模块的 POM 均未设置 `maven.deploy.skip`（全仓 `maven.deploy.skip` 只在根 POM 的一处**注释**里出现），
> 也就是说本仓没有 z-ctc 那种"演示模块永不发布"的例外。

---

## 🔧 技术栈

| 层级 | 技术 | 版本来源 |
|------|------|----------|
| 语言 / 运行时 | Java 17（`source`/`target` 8，非 `--release`；class major 61） | `z-boot-parent:1.2.0` 下发 |
| 框架 | Spring Boot 2.7.18 | 同上（`spring-boot.version` 由 parent 供，本仓只在 pluginManagement 保留 `${spring-boot.version}` 兜底） |
| 持久层 | MyBatis-Plus 3.5.7 + `mybatis-spring` 2.1.2（本仓按坐标写死压回，地板那一格是 2.0.6） | `z-boot-parent` 自带地板 + 根 POM |
| 连接池 | Druid 1.2.23（由 `z-boot-datasource-starter` 传递带入，本仓 `druid.version` 死键已删） | 地板 / starter |
| 数据库 | MySQL 8（`mysql-connector-j` 8.4.0，会连带 `protobuf-java` 3.25.5）；**没有 H2、没有 profile 化的内存库方案** | 地板 |
| 对象存储 SDK | `com.aliyun.oss:aliyun-sdk-oss` 3.17.4 | 地板 |
| 内部依赖 | `z-boot-web-starter` / `z-boot-datasource-starter` 1.0.19、`z-util-core` 1.0.14（`Result` / `ResultCode` 来自这里） | `z-boot-parent` 的兄弟仓权威表 |
| 日志 | Log4j2 2.17.2（`log4j-api`/`core`/`jul`/`slf4j-impl` 四条按坐标逐条压回）；代码里 12 处 `org.apache.logging.log4j`，0 处 slf4j/logback 直用 | 根 POM |
| 接口文档 | Knife4j `knife4j-openapi3-spring-boot-starter` 4.1.0（注意地板另有 `knife4j.version=3.0.3` 那一族，本仓用的是 openapi3 那一族） | 地板 |
| 前端 | React 19 + antd 6 + Vite 6 + react-router-dom 7，包名 `@zifang/z-oss-frontend`（`private: true`），另引 `@yuku123/z-oss-frontend-component`、`@yuku123/z-frontend-common` | `_frontend/package.json` |
| 构建 | Maven（flatten-maven-plugin 1.5.0，`flattenMode=oss` + `updatePomFile`，常开不挂 profile；发布形状是自包含 POM）· npm/pnpm（前端）· Docker（`_deploy/Dockerfile`） | 根 POM |

`_frontend/pom.xml` 里那个 `frontend-maven-plugin` 把 `dist/` 拷到
`z-oss-core/src/main/resources/static` 的链路，parent 还写着 `com.zifang:z-oss:1.0.0-SNAPSHOT`
（该坐标在本仓已不存在，且 `_frontend` 不在 `<modules>` 里），所以**这条链今天不会被 Maven 触发**；
静态产物得手工 `npm run build` 后再拷到 `z-oss-api/src/main/resources/static`。

---

## ⚙️ 配置项与后端切换

配置前缀是 `oss.*`（不是旧 README 写的 `z.oss.*`），由两个
`@ConfigurationProperties` 类承载：

| 配置键 | 定义处 | 默认值 | 说明 |
|--------|--------|--------|------|
| `oss.provider` | `OssCoreAutoConfiguration` 的 `@Value("${oss.provider:local}")` | `local` | 只接受 `local` / `aliyun` 两个值；写成别的值时 `OssProviderManager#provider()` 抛异常 |
| `oss.storage.file.root` | `FileStorageProperties#root` | `/data/oss` | 本地后端落盘根目录 |
| `oss.storage.file.default-bucket` | `FileStorageProperties#defaultBucket` | `z-team` | `FileStorageService` 用的默认桶 |
| `oss.storage.file.public-base-url` | `FileStorageProperties#publicBaseUrl` | `http://localhost:8888/oss-files/` | 拼公网 URL 用；⚠ 本仓**没有**注册 `/oss-files/**` 资源处理器，也没有把后端端口改成 8888，留默认值拼出来的地址当前不可达 |
| `oss.aliyun.endpoint` | `AliyunOssProperties#endpoint` | 无 | 如 `oss-cn-hangzhou.aliyuncs.com` |
| `oss.aliyun.access-key-id` / `access-key-secret` | `AliyunOssProperties` | 无 | 云厂商凭证，**见下面红线** |
| `oss.aliyun.security-token` | `AliyunOssProperties#securityToken` | 无 | 走 STS 时才填 |
| `oss.aliyun.bucket-prefix` | `AliyunOssProperties#bucketPrefix` | `""` | 给所有桶名加统一前缀，避免污染根账号 |
| `oss.aliyun.default-acl` | `AliyunOssProperties#defaultAcl` | `private` | 建桶默认 ACL |
| `server.port` | `z-oss-standalone.yml` | `8082` | 本仓只有这一份 yml，**没有 dev/prod 等 profile 文件**；由启动类用 `spring.config.additional-location` 显式加载 |
| `z.base.db.oss.*` → `z.base.db.default.*` | `ModuleDataSourceTemplate#buildDataSource` | host `localhost`、port `3306`、database `""` | 模块化数据源；`oss.*` 缺项时回退 `default.*` |
| `z.base.db.{oss,default}.initial-size` / `min-idle` / `max-active` / `max-wait` / `remove-abandoned` / `remove-abandoned-timeout` | 同上 | `5` / `5` / `20` / `60000` / `false` / `300` | Druid 池参数；连接保活（`testWhileIdle` + `keepAlive` + `SELECT 1`）是模板硬编码的，不可关 |
| `mybatis-plus.*` | `z-oss-standalone.yml` | 下划线转驼峰；逻辑删除字段 `deleted`，1/0 | |
| `spring.mvc.pathmatch.matching-strategy` | `z-oss-standalone.yml` | `ant_path_matcher` | |

**上传体积与分片**：全仓没有一处 `spring.servlet.multipart.*` 配置，也没有 `MultipartConfigElement` bean，
因此走 Spring Boot 2.7 的默认口径 —— 单个文件 1MB、整个请求 10MB；要放开必须在 yml 里显式写
`spring.servlet.multipart.max-file-size` / `max-request-size`（或用同名环境变量
`SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` / `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`）。
另外 `FileStorageEngine#store` 是先把整个 `InputStream` 读进 `ByteArrayOutputStream` 再算 ETag、
再 `Files.write`，也就是说本地模式下**峰值堆占用 ≈ 文件大小**，放开 multipart 上限时要一并看 `-Xmx`。
应用层没有分片/断点续传实现（见「尚未实现」），大文件目前只能整传。

### 凭据红线

本仓接的是真实对象存储与真实 MySQL，README 只列**配置键名与环境变量名**，一律不复述任何 key id、
secret、桶名之外的口令值。

- `z-oss-standalone.yml`（`z-oss-api/src/main/resources/`）里的 MySQL 地址 / 账号 / 口令**一律走占位符**
  （`${SPRING_DATASOURCE_URL:…}` / `${SPRING_DATASOURCE_USERNAME:}` / `${SPRING_DATASOURCE_PASSWORD:}`，
  2026-10-19 起已脱敏），`z.base.db.default.*` 同理。
  ⇒ **任何真实值都不得被复制进文档、镜像层或日志**。
  ⚠ 该文件**刻意不叫** `application.yml`：库件把配置放 classpath 根会**劫持宿主**
  （Spring Boot 的 `classpath:/application.yml` 只解析到第一个命中）⇒ 约束与门禁见
  `_doc/001_arch/02_库件配置约束.md`。
- 可用的环境变量名（Spring Boot relaxed binding 覆盖同名配置键）：
  `SERVER_PORT`、`SPRING_APPLICATION_NAME`、
  `Z_BASE_DB_OSS_HOST` / `Z_BASE_DB_OSS_PORT` / `Z_BASE_DB_OSS_DATABASE` /
  `Z_BASE_DB_OSS_USERNAME` / `Z_BASE_DB_OSS_PASSWORD`，
  以及回退档 `Z_BASE_DB_DEFAULT_*`（含 `Z_BASE_DB_DEFAULT_HOST`）；
  `spring.datasource` 那份主数据源走 `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` /
  `SPRING_DATASOURCE_PASSWORD`（2026-10-19 起 yml 里是占位符，部署必须注入前两个）；
  `OSS_PROVIDER`、`OSS_STORAGE_FILE_ROOT`、`OSS_ALIYUN_ENDPOINT`、
  `OSS_ALIYUN_ACCESS_KEY_ID`、`OSS_ALIYUN_ACCESS_KEY_SECRET`、`OSS_ALIYUN_SECURITY_TOKEN`、
  `OSS_ALIYUN_BUCKET_PREFIX`、`OSS_ALIYUN_DEFAULT_ACL`。
- 云厂商 AK/SK 与 STS token、数据库口令都必须由部署环境注入；`oss.aliyun.access-key-id` /
  `access-key-secret` 缺省时 `AliyunOssProvider` 构造 `OSSClient` 会在运行期失败，
  但 README 与代码注释都不该出现真值。
- 数据面鉴权凭证（z-oss 自己发给用户的 accessKey/secretKey）通过请求头
  `X-Zoss-Access-Key` / `X-Zoss-Secret-Key` 传输；`AuthenticationInterceptor` 明确拒绝 query 传参，
  原因就是 URL 会进访问日志。
- 发布侧凭证走 `_doc/003_script/*.sh`：`CENTRAL_USERNAME`、`CENTRAL_TOKEN`、`CENTRAL_GPG_PASSPHRASE`
  三个环境变量名由仓库根的 `.env` 提供（`.env` 已被 `.gitignore` 排除），
  `install-settings.sh` 往 `~/.m2/settings.xml` 写的也是 `${env.CENTRAL_USERNAME}` / `${env.CENTRAL_TOKEN}`
  占位符而非明文。

---

## 🚀 快速开始

### 编译

```bash
mvn clean install -DskipTests
```

第三方版本一律由 `z-boot-parent:1.2.0` 供给（第三方地板与兄弟仓权威表均已并入 parent 自身），
模块 POM 里不应再出现字面版本钉。构建报找不到版本时，先确认能解析到
`io.github.yuku123:z-boot-parent:1.2.0`（`relativePath` 留空，它在 repo1 不在磁盘）。
本仓没有 `.mvn/`、没有 `Makefile`、没有 `mvnw`。

### 本地跑起来

```bash
# 只改 provider，不动数据库口令的最小可用形态（仍需一个可达的 MySQL）
export OSS_PROVIDER=local
export OSS_STORAGE_FILE_ROOT=/tmp/z-oss-data
java -jar z-oss-api/target/z-oss-api-1.0.4-exec.jar
```

可执行 jar 的 classifier 是 `exec`（`spring-boot-maven-plugin` 配了 `<classifier>exec</classifier>`），
`z-oss-api/target/z-oss-api-1.0.4.jar` 那份是普通瘦 jar，可当依赖引用 —— 别把它当启动包。

服务起在 `http://localhost:8082`，无 context-path。Knife4j UI 在 `http://localhost:8082/doc.html`
（yml 未覆盖文档路径），Actuator 只因为引了 `spring-boot-starter-actuator` 而带默认 `/actuator/health`。
`_frontend` 构建产物被 `z-oss-core` 的 `static/` 打包，所以 `http://localhost:8082/` 直接就是管理台首页。

### 建库

DDL 只有 SQL 文件，没有 Flyway/Liquibase：
[`_doc/002_deploy/init/db.sql`](_doc/002_deploy/init/db.sql)（建 5 张表：`oss_user`、`oss_bucket`、
`oss_object`、`oss_part`、`oss_multipart`）与同目录的
[`_doc/002_deploy/init/oss.sql`](_doc/002_deploy/init/oss.sql)（同一套表结构的另一份写法，
含 AK/SK 列）。仓库根的 `sql/` 是空目录，不放东西。

### 前端

```bash
cd _frontend
npm install            # 或 pnpm install（pnpm-workspace.yaml 只设 nodeLinker: hoisted）
npm run dev            # vite，端口 3004
npm run build          # 产物在 _frontend/dist，需手工拷进 z-oss-api/src/main/resources/static
```

### 嵌入 main-starter（不独立起进程）

引入 `io.github.yuku123:z-oss-api`，`META-INF/spring.factories` 里的 `OssWebAutoConfiguration`
会自动生效，宿主应用**不必**在自己的 `@ComponentScan` 里列 `com.zifang.z.oss`。
两条硬约束写在 `OssWebAutoConfiguration` 的注释上，改这个类前先读它：
不要在它上面加 `@MapperScan`（mapper 注册由 `OssModuleDataSource` 独占），
也不要把扫描范围放宽到 `com.zifang.z.oss` 整包（会把 core 的 `@Mapper` 接口按默认
`SqlSessionFactory` 抢注，触发 "expected single matching bean but found 16"）。

---

## 🔌 API 一览

`z-oss-api` 下 **6 个 Controller、40 个方法级映射**：`/api/v1` 族 34 个
（`UserController` 6 / `BucketController` 10 / `ObjectController` 15 / `BackupController` 3）、`/api/oss-admin` 族 4 个（只读管理面）、
`/api/oss/*` 族 2 个（z-opc 侧的别名入口）。鉴权由 `AuthenticationInterceptor` 拦 `/api/v1/**`，
只放行 `POST /api/v1/user/register` 与 `POST /api/v1/user/login`。

> **路径口径纠偏**：`BucketController` 与 `ObjectController` 两个 Controller 的类级 Javadoc 还列着一批
> `/object/{bucketName}/{objectKey}`
> 这种 path-style 写法，但方法上的注解早已换成 query 参数风格 —— **下面这张表是按注解实测出来的，
> 是唯一可信的版本**。

用户（`UserController`，base `/api/v1/user`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/user/register` | 注册，免鉴权；返回 VO 里含生成的 accessKey / secretKey |
| POST | `/api/v1/user/login` | 登录，免鉴权；返回 `LoginResponseVO`，其 token 字段由 accessKey 与 secretKey 以冒号拼接 |
| GET | `/api/v1/user/info` | 当前用户信息（读 `X-Zoss-Access-Key`） |
| PUT | `/api/v1/user/info` | 改用户名 |
| POST | `/api/v1/user/reset-key` | 重置 accessKey / secretKey |
| POST | `/api/v1/user/password` | 改密码，请求体给 oldPassword / newPassword 两个字段 |

桶（`BucketController`，base `/api/v1/bucket`）：

| 方法 | 路径 | 关键入参 |
|------|------|----------|
| POST | `/api/v1/bucket` | body `CreateBucketRequest.name` |
| DELETE | `/api/v1/bucket` | `bucketName` |
| GET | `/api/v1/bucket` | `bucketName` 可选：不传 = 列表，传了 = 详情（两个裸 `@GetMapping` 会撞 Ambiguous mapping，已合并成一个入口） |
| PUT | `/api/v1/bucket` | `bucketName` + body acl/region/policy |
| GET · PUT | `/api/v1/bucket/acl` | `bucketName`（PUT body 给 `acl` 一个键） |
| GET · PUT · DELETE | `/api/v1/bucket/policy` | `bucketName`（PUT body 给 `policy` 一个键；DELETE 走 `updateBucket(..., null)` 清空） |
| HEAD | `/api/v1/bucket` | `bucketName`（存在性 + 归属校验） |
| GET | `/api/v1/bucket/stats` | `bucketName` —— 注意它挂在 `ObjectController`（base `/api/v1`）上，不在 `BucketController` 里 |

对象（`ObjectController`，base `/api/v1`）：

| 方法 | 路径 | 关键入参 |
|------|------|----------|
| POST | `/api/v1/object` | `bucketName` + `objectKey` + multipart 字段 `file`；无 Content-Type 时回落 `application/octet-stream` |
| GET | `/api/v1/object` | `bucketName` + `objectKey`；响应体是字节数组，但 `Content-Type`/`Content-Length`/`ETag` 只设在 `HttpHeaders` 对象上、随 `Result` 一起 JSON 化返回 —— 当前实现**不是**真正的二进制流下载 |
| GET | `/api/v1/object/download` | `bucketName` + `objectKey` + `expires` + `accessKeyId` + `signature`；返回 `StreamingResponseBody`，**这条才是真二进制流**（与上一条不同） |
| DELETE | `/api/v1/object` | `bucketName` + `objectKey` |
| GET | `/api/v1/object/list` | `bucketName` + 可选 `prefix` |
| POST | `/api/v1/folder` | `bucketName` + `folderKey` |
| HEAD | `/api/v1/object/meta` | `bucketName` + `objectKey`；不存在时 code 走 404 |
| POST | `/api/v1/object/copy` | `bucketName` + `objectKey` + body `destBucketName` / `destObjectKey` |
| POST | `/api/v1/object/batch-delete` | `bucketName` + body 为 key 字符串数组；部分失败不返回计数 |
| GET | `/api/v1/object/url` | `bucketName` + `objectKey` + `expires`（默认 3600 秒）；返回 `PresignedUrlVO`（url + expiresAt） |
| GET | `/api/v1/bucket/stats` | `bucketName`；返回 `objectCount` / `totalSize` / `activeProvider` |
| POST | `/api/v1/object/multipart/init` | `bucketName` + `objectKey` + 可选 `contentType`；返回 uploadId |
| POST | `/api/v1/object/multipart/part` | `bucketName` + `objectKey` + `uploadId` + `partNumber` + multipart 字段 `file` + 可选 `contentType` |
| POST | `/api/v1/object/multipart/complete` | `bucketName` + `objectKey` + `uploadId`；返回 `ObjectVO` |
| POST | `/api/v1/object/multipart/abort` | `bucketName` + `objectKey` + `uploadId`；丢弃已传分片 |

备份链（`BackupController`，base `/api/v1/backup`，TASK-20261012-027）：

| 方法 | 路径 | 关键入参 |
|------|------|----------|
| POST | `/api/v1/backup/archive` | multipart 字段 `file`（备份文件）+ 可选 `objectKey`；归档并执行滚动保留（保留最新 N 份） |
| GET | `/api/v1/backup/list` | 无 —— 当前保留的备份（最新在前） |
| GET | `/api/v1/backup/stats` | 无 —— 备份链计数（archives / archiveFailures / retentionDeletions / lastError） |

> 备份链**默认不启用**（要 `oss.backup.enabled=true`）：没启用时这三个端点返回 503，
> 而不是"看起来归档成功"。详见 `_doc/002_deploy/01-backup-chain.md`。

管理面（`OssAdminController`，base `/api/oss-admin`，**只读** —— 本文件只有 `@GetMapping`，没有任何写映射）：

| 方法 | 路径 | 关键入参 |
|------|------|----------|
| GET | `/api/oss-admin/whoami` | 无 —— 返回平台登录身份到 z-oss 账号的映射；`provisioned=false` 表示该账号在 z-oss 里没有身份（不降级成空列表冒充「有账号、桶为空」）；取不到 SSO 身份时返回 401 |
| GET | `/api/oss-admin/buckets` | 无 —— 桶列表（等价于 `GET /api/v1/bucket` 的 list 分支，但不需要 AK/SK） |
| GET | `/api/oss-admin/bucket/detail` | `name` —— 桶元数据 + 两份统计：`bucket_stats` 来自 `IOssBucketService`、`object_stats` 来自 `IOssObjectService`，两份不能只取一份 |
| GET | `/api/oss-admin/objects` | `name` + 可选 `prefix` —— 对象列表（`prefix` 原样交给 service，本层不拼接也不兜空） |

别名入口（`OssAliasController`，类在 `com.zifang.z.oss.host` 包下，**没有**类级前缀）：

| 方法 | 路径 | 关键入参 |
|------|------|----------|
| GET | `/api/oss/list` | 无 —— z-opc 侧 SSO 令牌直查桶列表 |
| GET | `/api/oss/{bucketName}` | path 变量 `bucketName` —— 桶详情 |

> ⚠ `/api/oss-admin/**` 与 `/api/oss/**` **不在** z-oss 自己的 `AuthenticationInterceptor` 口径里 ——
> 它们落在 z-opc 的 `/api/**` 整族上，由 **SSO 会话**（`SsoInterceptor`）鉴权，不是 AK/SK。
> （`OssAdminController` 类注释记录：实测 `/api/oss/list` 不带 token 一律 302。）

前端与后端的口径对不上，接之前要先修：`_frontend/src/services/api.js` 的 `bucketApi` / `objectApi`
仍在请求 path-style 的 `/bucket/{name}`、`/object/{bucket}/{key}`，这些路径在上表里一个都不存在；
`_frontend/vite.config.js` 的 `/api` 代理目标写的是 `http://localhost:8888`，而后端在 `8082`。
`_frontend/src/utils/request.js`（走 `@yuku123/z-frontend-common` 的那份）没有被任何页面 import，
5 个页面全部 import 的是 `services/api`。

---

## 🧪 测试

```bash
mvn test
```

仓内的测试类：`z-oss-core` 的 `LocalOssProviderMultipartTest`、`LocalOssProviderPathTest`、
`FileStorageEnginePrefixPruneTest`、`FileStorageEngineSecurityTest`，`z-oss-api` 的
`ObjectDownloadControllerTest`，`z-oss-spring-boot-starter` 的 `OssStarterAutoConfigurationTest`，
以及 `z-oss-core` 的 `ReadmeClaimsTest`（本 README 定量主张与源码重算值的对账守卫）；
`ZossDeployedServiceIT` 与 `S3OssProviderMinioIT` 打的是真实部署实例，不在 `mvn test` 的默认口径里。
旧 README 写的"单元测试 18 PASS"没有任何来源。也没有 `*PerformanceTest.java` 这类性能用例。

跨进程的行为验证仍然只能靠编译 + 手工冒烟：

```bash
mvn clean install -DskipTests                                  # 至少证明 reactor 与父链解析得通
# 起服务后用两个头跑一遍 register → login → bucket → object
curl -s -X POST 'http://localhost:8082/api/v1/bucket' \
  -H 'Content-Type: application/json' -H 'X-Zoss-Access-Key: <AK>' -H 'X-Zoss-Secret-Key: <SK>' \
  -d '{"name":"demo"}'
```

补测试时的已知坑：`z-boot-parent` 自带地板对 `spring-boot-starter-logging` 写了 `*:*` 通配 exclusion，
logback-classic / logback-core / log4j-to-slf4j 会从类路径静默消失。本仓生产实现一直是 Log4j2（不受影响），
但如果新增的测试用例直接用 logback，就得自己在 test scope 补依赖并在 POM 注释里写清原因。

---

## 🐳 部署

**镜像构建资产在仓库根的 [`_deploy/Dockerfile`](_deploy/Dockerfile)**，不在 [`_doc/002_deploy/`](_doc/002_deploy/)
（那一层现在只放 SQL）。这是本仓与组织文档收口规范不一致的地方之一，按现状记录如下：

```
仓库根/
├── _deploy/Dockerfile        # 唯一的 Dockerfile；_doc/002_deploy/ 下没有 Dockerfile
├── sql/                      # 空目录
└── _doc/002_deploy/init/     # 只有 db.sql + oss.sql
```

没有 `docker-compose*.yml`，没有 `k8s/`，没有 `Makefile`，[`_doc/002_deploy/`](_doc/002_deploy/) 里也没有部署说明文档。

`_deploy/Dockerfile` **当前不可直接用**，先修再跑，实测到三处问题：

1. 它 `COPY z-oss-api/target/z-oss-api-1.0.0-SNAPSHOT-exec.jar`，而 HEAD 版本是 `1.0.4`，
   产物名是 `z-oss-api-1.0.4-exec.jar` —— 文件名已对不上。
2. `EXPOSE 8088` 与 `HEALTHCHECK` 打的 `http://127.0.0.1:8088/doc.html`，跟 `z-oss-standalone.yml`
   的 `server.port: 8082` 是两个端口。
3. 这个文件里**串了两段 FROM 块**（`8-jre` 那份在前、`8-jdk` 那份在后，第二份还 `COPY ../z-oss-api/...`
   这种越出 build context 的路径）。Docker 只会按文件语义解析，结果是多余的一层 JDK 镜像定义被留在文件里。

能用的做法（build context 必须是仓库根，因为 COPY 路径以 `z-oss-api/...` 起头）：

```bash
mvn clean install -DskipTests
docker build -f _deploy/Dockerfile -t z-oss:1.0.4 .
docker run -d -p 8082:8082 \
  -e SERVER_PORT=8082 \
  -e OSS_PROVIDER=aliyun \
  -e OSS_ALIYUN_ENDPOINT=oss-cn-hangzhou.aliyuncs.com \
  -e OSS_ALIYUN_ACCESS_KEY_ID -e OSS_ALIYUN_ACCESS_KEY_SECRET \
  -e Z_BASE_DB_OSS_HOST -e Z_BASE_DB_OSS_DATABASE \
  -e Z_BASE_DB_OSS_USERNAME -e Z_BASE_DB_OSS_PASSWORD \
  -v /srv/oss-data:/data/oss \
  z-oss:1.0.4
```

（`OSS_ALIYUN_ACCESS_KEY_ID` 这类不带 `=` 的写法是把宿主环境透传进容器；不要把值写进命令行、
compose 文件或本文档。）

`_doc/003_script/build.sh` 那套"打包 → build → 起容器 → 健康检查 → 清理旧容器"的一键脚本还在，
但它的 `DOCKERFILE_PATH="./Dockerfile"`、`HOST_PORT/CONTAINER_PORT=8088`、`IMAGE_NAME=":latest"`
三项都与本仓现状不符（Dockerfile 在 `_deploy/`、端口 8082、镜像名空），要跑得先改这三行。
`_doc/003_script/package.sh` 就两句：`mvn clean` + `mvn install -DskipTests=true`。

---

## 📄 License

MIT，见仓库根 [`LICENSE`](LICENSE)（版权行 `Copyright (c) 2026 z-opc-foundation`），根 POM 的
`<licenses>` 同样声明 MIT License。旧 README 结尾那句"Internal use only. 版权属于 z-biz"与两者都不符，已删。

---

## 相关项目

| 项目 | 关系 |
|---|---|
| `z-boot` | 提供 `z-boot-parent`（自带第三方地板与兄弟仓权威表），以及本仓用到的 `z-boot-web-starter`、`z-boot-datasource-starter` |
| `z-util` | `z-util-core` 供给 `Result` / `ResultCode` 等返回体类型 |
| `z-ctc` | 4A 中心；本仓**不依赖** `z-ctc-core`（`OssTenantHook` 就是为此而留的反向缝），用户体系是 z-oss 自己的一套 |
| `z-boot-oss-starter` | 已发布到 Central（`1.0.19` ranged GET 实测 206），可作为对外聚合入口 |

---

## 文档目录

- [`_doc/008_troubleshooting/`](_doc/008_troubleshooting/) — 踩坑与失败要点（控制台三处契约断裂）
本项目文档统一收口在 `_doc/` 下（现状：`001_arch` 只有 1 篇、`004_skill` 是空目录）：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档:
  - [`00-overview.md`](_doc/001_arch/00-overview.md) — 项目总览 / 模块关系 / 端口 / 技术栈 / 开发优先级
    （注意该文的口径比代码旧：它写的端口是 8094 与 8080、Spring Boot 2.7.12、MyBatis-Plus 3.3.1，
    实体名写作 `ZOssBucket` / `ZOssObject`，实测均与当前实现不符，以本文为准）
- [`_doc/002_deploy/`](_doc/002_deploy/) — 部署 SQL 与运维口径:
  - [`01-backup-chain.md`](_doc/002_deploy/01-backup-chain.md) — 备份归档与滚动保留策略
  - [`init/`](_doc/002_deploy/init/)
    - [`db.sql`](_doc/002_deploy/init/db.sql) — `oss_user` / `oss_bucket` / `oss_object` / `oss_part` / `oss_multipart` 建表
    - [`oss.sql`](_doc/002_deploy/init/oss.sql) — 同一套表结构的另一份写法
- [`_doc/003_script/`](_doc/003_script/) — 运维脚本:
  - [`build.sh`](_doc/003_script/build.sh) — 打包 + 镜像 + 容器 + 健康检查（端口/Dockerfile 路径需先修，见「部署」）
  - [`package.sh`](_doc/003_script/package.sh) — `mvn clean` + `mvn install -DskipTests=true`
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh) — `publish` / `verify` / `gpg-init` / `readme` / `help`，凭证从仓库根 `.env` 读
  - [`install-settings.sh`](_doc/003_script/install-settings.sh) — 往 `~/.m2/settings.xml` 注入 `server id="central"`（写 `${env.*}` 占位符，不落明文）
- `_doc/004_skill/` — AI skill 定义（目前为空目录，暂无 skill）

与规范不一致、但按现状如实记录的一处：Dockerfile 在仓库根 [`_deploy/`](_deploy/) 而不是
[`_doc/002_deploy/`](_doc/002_deploy/)。真正的 DDL 在 `_doc/002_deploy/init/`（`db.sql` / `oss.sql`）；
仓库根已无 `sql/` 目录（空目录 git 不跟踪，已清）。

_Maintained by the z-opc-foundation organization._
