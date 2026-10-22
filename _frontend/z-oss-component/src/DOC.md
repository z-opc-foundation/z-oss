# z-oss-component

z-oss（对象存储）跨仓唯一前端面：把「存储桶 / 对象浏览 / 用户与密钥 / 概览」这一整套对象存储
管理页面收敛成一个可复用的 React 组件包。包内**并存两套数据面**，路由分工明确，互不重叠。

- **npm 包名**：`@yuku123/z-oss-component`
- **宿主接入**：`@yuku123/z-oss-component/pages`（manifest + API），主壳可只取 `ComponentDocPage`
- **后端**：只读面走 z-opc 的 `/api/oss-admin/**`（`OssAdminController`，同 JVM 直调 z-oss service bean，
  鉴权是平台 SSO）；用户 / 桶 / 对象的读写面走 z-oss 原生 `/api/v1/**`（要 AK/SK 头）
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）

---

## 一、能力概览

路由前缀 `/z-oss`，菜单与路由在 `src/pages-manifest.jsx` 里声明。

| 页面 | 路由（相对 `/z-oss/*`） | 源文件 | 数据面 | 能力 |
|------|--------------------------|--------|--------|------|
| 登录 | `login` | `pages/Login.jsx` | v1 | 用户名密码登录 / 注册（真登录，不用基座 mock） |
| 首页 | `home` | `pages/HomePage.jsx` | — | 入口卡片页 |
| 概览 | `dashboard` | `pages/Dashboard.jsx` | v1 | 桶数与容量统计（`bucketApi.list` + `bucketApi.getStats`） |
| 存储桶 | `bucket` | `pages/BucketList.jsx` | v1 | 桶列表 + 新建 / 编辑 / 删除（校验小写字母数字连字符、区域、ACL） |
| 对象管理 | `object` | `pages/ObjectList.jsx` | v1 | 对象浏览、新建文件夹、复制、下载、单个 / 批量删除 |
| 用户管理 | `user` | `pages/UserManage.jsx` | v1 | 改用户名 / 密码、重置密钥 |
| 桶列表（只读） | `*/buckets`、`*/bucket` | `pages/BucketListPage.jsx` | oss-admin | 平台账号可见的桶列表，区分「真的空」与「报错（含未开户）」 |
| 对象浏览（只读） | `*/objects` | `pages/ObjectListPage.jsx` | oss-admin | 桶 + 前缀维度的对象列表，两栏统计（桶侧 / 对象侧） |
| 账号信息 | `*/account` | `pages/AccountPage.jsx` | oss-admin | `whoami`：平台用户名、租户、是否已开户 |
| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | — | 本文件渲染页（左侧 240px TOC + 右侧正文） |

`src/pages/OssApp.jsx` 是只读面的子路由挂载点（`buckets` / `bucket` / `objects` / `account`），
被 `routes` 里的 `/z-oss/*` 承接，同时从包根具名导出（OpsWorkbench 以 `/oss/*` 通配挂它）。

## 二、导出面

```js
// 主入口（. 桶）
import {OssApp, ComponentDocPage} from '@yuku123/z-oss-component'
import {ossApi, ossErrorText, isUnprovisionedError, fmtBytes} from '@yuku123/z-oss-component'

// 宿主组装面（./pages 桶）
import {menuItems, routes, HomePage, Login, BucketList, ObjectList, UserManage} from '@yuku123/z-oss-component/pages'
import {configureOss} from '@yuku123/z-oss-component/pages'
```

`./pages` 桶 = 一组具名再导出 + `export * from './pages-manifest.jsx'` 的整份转发。
**不要再手挑名字** —— 之前手挑过一次，manifest 新增的 `HomePage` / `LoginPage` 没被转发，
rollup 直接报 `LoginPage is not exported by dist/pages.js`。

## 三、API 端点

### 3.1 只读面（平台 SSO，无 AK/SK）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/oss-admin/whoami` | 当前平台账号信息（`platform_username` / `platform_tenant` / `provisioned`） |
| GET | `/oss-admin/buckets` | 当前账号可见的桶列表 |
| GET | `/oss-admin/bucket/detail` | 桶详情 + 桶侧 / 对象侧两份统计（query `name`） |
| GET | `/oss-admin/objects` | 对象列表（query `name` + `prefix`） |

### 3.2 用户 / 桶 / 对象面（AK/SK，原生 `/api/v1`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/user/login` | 登录，返回 `accessKey:secretKey` 形状的 token |
| POST | `/api/v1/user/register` | 注册 |
| GET | `/api/v1/user/info` | 用户信息 |
| PUT | `/api/v1/user/info` | 改用户名 |
| POST | `/api/v1/user/reset-key` | 重置 AK/SK |
| POST | `/api/v1/user/password` | 改密码 |
| GET | `/api/v1/bucket` | 桶列表（不带 `bucketName`） |
| GET | `/api/v1/bucket` | 桶详情（带 `bucketName`；后端把两个裸 `@GetMapping` 合成了一个） |
| POST | `/api/v1/bucket` | 新建桶 |
| PUT | `/api/v1/bucket` | 更新桶（query `bucketName`） |
| DELETE | `/api/v1/bucket` | 删除桶（query `bucketName`） |
| GET | `/api/v1/bucket/stats` | 桶统计（query `bucketName`） |
| GET | `/api/v1/object/list` | 对象列表（query `bucketName` + `prefix`） |
| POST | `/api/v1/folder` | 新建文件夹（query `bucketName` + `folderKey`） |
| GET | `/api/v1/object` | 下载对象（query `bucketName` + `objectKey`，`responseType: 'blob'`） |
| POST | `/api/v1/object/copy` | 复制对象 |
| DELETE | `/api/v1/object` | 删除对象（query `bucketName` + `objectKey`） |
| POST | `/api/v1/object/batch-delete` | 批量删除对象（body 是 key 数组） |

## 四、页面说明

### 4.1 登录（`pages/Login.jsx`）

真登录：走 `POST /api/v1/user/login`，成功后把 `accessKey` / `secretKey` 写进 localStorage。
⚠ 不能用基座 mock 的 `LoginPage`：它存的是 `token`，而 z-oss 的桶 / 对象接口要的是
`X-Zoss-Access-Key` / `X-Zoss-Secret-Key` 头，走 mock 登录进去后每个请求都 401。

### 4.2 概览

桶列表 + 每桶统计汇总。⚠ 统计端点在 `bucketApi.getStats`。

### 4.3 存储桶（v1 面，可写）

桶列表，支持新建 / 编辑 / 删除。新建表单校验桶名（小写字母、数字、连字符），可选区域与 ACL。

### 4.4 对象管理（v1 面，可写）

按桶 + 前缀浏览对象，支持新建文件夹、复制、下载、单个 / 批量删除。
下载是唯一不走 Result 信封的端点 —— controller 直写 Servlet 输出流（按 JSON 解析会变成 base64），
所以 `responseType` 必须是 `blob`。

### 4.5 用户管理（v1 面）

改用户名、改密码、重置密钥。

### 4.6 桶列表（只读面）

显示「当前平台账号在 z-oss 名下可见的桶」，不是全平台桶（service 层按 `user_id` 过滤）。
页面把两种空态分开：接口成功且确实 0 条 ⇒ 明写「接口通了、确实是空」；
报错（含 409 未开户）⇒ 显示错误原文，**绝不退化成空表**。

### 4.7 对象浏览（只读面）

统计刻意分两栏：对象数 / 总大小来自 `IOssObjectService`，`physicalExists` / `activeProvider`
来自 `IOssBucketService`（两份 z-oss 自己的实现，字段互不覆盖）。
「存储提供方里没有这个桶」（`physicalExists=false`）与「桶在、里面 0 个对象」是两件事，不允许合并显示。
页面用基座 `ListTable` + `PageHeader` + `TableToolbar`。

### 4.8 账号信息

`whoami` 的结果：平台用户名、租户、是否已在 z-oss 开户。

### 4.9 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `configureOss({apiBase})` | `'/api/v1'` | 改写 `services/request.js` 里那个独立请求实例的 `baseURL` |
| 鉴权头 | `X-Zoss-Access-Key` / `X-Zoss-Secret-Key` | 从 localStorage 的 `accessKey` / `secretKey` 取 |
| 登录态判据 | `accessKey` 存在即已登录 | `isAuthenticated()` 与 suit 的 `ProtectedShell` 都用它 |
| `fmtBytes(b)` | — | 字节数格式化（B / KiB / MiB / GiB），空值返回 `-` |
| `ossErrorText(e)` | — | 把两种后端错误形态（Result 信封 / 原样 HTTP 语义）都透成可读文案 |
| `isUnprovisionedError(e)` | — | 识别「未开户」（409 或文案含「未开户」），用于和空列表区分 |

## 六、已知限制

- **只读面与可写面是两套页面**：manifest 里 `/z-oss/bucket`、`/z-oss/object`、`/z-oss/user`
  走 v1 面（要 AK/SK，可写）；`/z-oss/*` 兜底里的 `buckets` / `objects` / `account` 走 oss-admin 只读面。
  两者路由不同、数据源不同，别混用。
- **刻意不调的两条现成路**（都是假的，别「顺手改回去」）：
  `GET /api/oss/list`（`E2EAliasController` 直查 DB 且 `catch(Exception)` 后回空列表 ⇒
  「查不到」与「没有桶」不可区分）；`GET /api/oss/{bucket}`（返回的是构造出来的常量
  `objects=[]` / `totalObjects=0` / `totalSize=0`）。
- `services/api.js` 用的是**基座默认桶**里的 axios 实例（`baseURL='/api'`），而 v1 端点在代码里
  写的是完整路径 `/api/v1/**` ⇒ axios 拼出来的实际 URL 是 `/api/api/v1/**`。
  这条与网关侧的真实路由口径需要核对（要么改成去掉 `/api` 前缀，要么在网关重写）。
- `configureOss` 改的是 `services/request.js` 的实例，而 `services/api.js` 里的 API 函数
  用的是基座默认桶 —— **两者没接上**，换挂载前缀时 `configureOss` 对实际调用不生效。
- 登录态用 `accessKey` 而非 `token`；与基座 `LoginPage`（存 `token`）不通用。
- 下载端点返回 blob，前端必须自己 `URL.createObjectURL` 再 revoke，漏了会泄漏内存。
- 只读面全部只读（后端只有 `@GetMapping`），页面上不应出现上传 / 删除 / 清空按钮。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |
| 0.1.1 | 新增只读面（`BucketListPage` / `ObjectListPage` / `AccountPage`）+ `OssApp` 子路由，与 v1 读写面并存 |
| 0.1.0 | 首版：桶 / 对象 / 用户页面集 + manifest + 自洽 request |
