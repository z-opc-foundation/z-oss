# 2026-10-01 z-oss 控制台三处契约断裂（浏览器实测，非推断）

量具与账本（本机，不在仓内）：`~/.cache/zopc_ui_sweep/sweep/zoss_console_sweep.js`、`sweep/zoss_diag.js`，
原始事件流 `~/.cache/zopc_ui_sweep/logs/zoss_pass{A,B,C,D,E}*.{jsonl,log}`，复盘全文 `~/.cache/zopc_ui_sweep/NOTES.md` §14:0x。

起法（不改仓内任何文件）：

```bash
cd z-oss/_frontend && ./node_modules/.bin/vite --port 3004 --strictPort --host 127.0.0.1
# vite.config.js 唯一代理： '/api' → http://localhost:8888（z-opc 合并 JVM，本机在跑）
```

浏览器凭证一律用哨兵串 `SENTINEL_AK_NOT_A_CRED` / `SENTINEL_SK_NOT_A_CRED` 写进 `localStorage`
（控制台进门只看 `accessKey` 是否非空：`App.jsx` 的 `RequireAuth`），非任何真 AK/SK。

---

## 缺陷 1｜`Dashboard.jsx` 少一条 import ⇒ 整站白屏，且所有未匹配 URL 也被导向这张崩页

- 现象（实测）：`/dashboard` 上 `pageerror: FileSizeFormatter is not defined`，React 抛
  `An error occurred in the <Dashboard> component`，随后 `#root.children=0`、`document.body.innerText.length=0`、
  候选控件数 = 0。页面不是"没数据"，是一个节点都没提交。
- 根因（现读）：`src/pages/Dashboard.jsx:55` 使用 `<FileSizeFormatter bytes={stats.totalSize}/>`，
  而该文件 1–13 行的 import 中**没有** `@yuku123/z-oss-frontend-component`；
  同一符号在 `src/pages/ObjectList.jsx:15` 是正确 import 的，且确实由已安装件导出：

```bash
grep -n "FileSizeFormatter" src/pages/*.jsx
grep -o "FileSizeFormatter" node_modules/@yuku123/z-oss-frontend-component/dist/*.es.js | head -3
```

- 连带面：`AppLayout.jsx` 的嵌套路由末尾是 `<Route path="*" element={<Navigate to="/dashboard" replace/>}>`，
  所以任意拼错的 URL 也会被重定向到这张会崩的页 → 用户看到的是彻底的白屏，没有 404、没有错误边界。
- 影响：本控制台在 `console_boot_readiness` 里记作"6 页 / 150 控件点"，实测浏览器可达 0 页。

## 缺陷 2｜前端 URL 说 path-param，控制器要 query-param ⇒ 前端全部形状拿 500

只读 GET 探测（直连合并 JVM `:8888`，带上述哨兵头）：

| 形状 | 例 | 实测 |
|---|---|---|
| 前端（`src/services/api.js`） | `/api/v1/bucket/{n}`、`/bucket/{n}/acl`、`/bucket/{n}/policy`、`/bucket/{n}/stats`、`/object/{b}`、`/object/{b}/{k}` | **500**，115–121 B，body 为 Spring 默认 `{"timestamp","status","error","path"}` |
| 控制器（`z-oss-api/.../BucketController.java:102,136`、`ObjectController.java:95,157,272`） | `/bucket?bucketName=`、`/bucket/acl?bucketName=`、`/object/list?bucketName=&prefix=`、`/object?bucketName=&objectKey=`、`/bucket/stats?bucketName=` | **401** 84 B，body `{"data":null,"success":false,"code":401,"message":"处理失败\|Invalid access key"}`（说明 handler 在、鉴权拦截器跑到） |
| 对照（根本不存在） | `/api/v1/__nope__`、`/api/v1/bucketx`、`/api/v1/user/nosuch` | 也是 **500** ⇒ 合并 JVM 里"未注册路径"拿不到 404 |

复现：

```bash
for p in '/api/v1/bucket/sweep-demo/stats' '/api/v1/bucket/stats?bucketName=sweep-demo' '/api/v1/__nope__'; do
  printf '%s ' "$p"; curl -s -o /dev/null -w '%{http_code} %{size_download}\n' \
    -H 'X-Zoss-Access-Key: SENTINEL_AK_NOT_A_CRED' -H 'X-Zoss-Secret-Key: SENTINEL_SK_NOT_A_CRED' "http://127.0.0.1:8888$p"
done
```

（注意 URL 必须整体加引号，否则 shell 会把 `?` 当通配。）

- 次生缺陷：`services/api.js` 的失败分支取 `error.response.data?.message`，而 Spring 默认 500 body 里**没有** `message` 键，
  于是 toast 只能说 `Request failed with status code 500` —— 用户拿不到任何定位信息。
- 结论：即便 AK/SK 正确、后端已注册 z-oss 控制器，这 7 类请求也全数失败；`BucketList`/`ObjectList`/`Dashboard` 的数据面结构性不可达。

## 缺陷 3｜`services/api.js` 把信封整个交给页面，而页面按"已解包的载荷"用 ⇒ 契约对上也是白屏

- 后端成功响应是信封（由 401 body 实证字段集 `{data,success,code,message}`；控制器签名为 `Result<List<BucketVO>>` 等）。
- `src/services/api.js` 的响应拦截是 `(response) => response.data` ⇒ 交出去的是**整个信封**；
  页面写的却是 `const data = await bucketApi.list(); setBuckets(data || [])`，直接把信封塞进 antd 的 `dataSource`。
- 三跑 A/B/C（同一盘面，只换桩的契约；`off` 走真 `:8888`）：

| 桩契约 | `/dashboard` | `/buckets` | `/objects/sweep-demo-bucket` | `/users` |
|---|---|---|---|---|
| `off` 真后端 | text 0 / req 0 | text 0 / req 2 | text 0 / req 4 | text 0 / req 2（401 后被弹走） |
| `env`＝`{code:200,data:[…]}`（后端真实契约） | 0 / 0 | **text 0 rows 0** | **text 0 rows 0** | text 313 rows 3 |
| `bare`＝裸数组（页面代码期望的契约） | 0 / 0 | **text 204 rows 2** | **text 170 rows 2** | text 396 rows 3 |

- 同一仓库里现成的解包层没被用：`node_modules/@yuku123/z-frontend-common/src/utils/request.ts` 的 `defaultUnwrap`
  认 `{code,data}` 且非 200 即 reject；`src/utils/request.js` 也照 `createRequest({baseURL:'/api/v1', tokenKey:'accessKey'})` 建好了实例——
  **但四个页面全在用 `services/api.js` 那个不解包的实例**，`src/utils/request.js` 实测无人 import（死码）。
- `/users` 是唯一"吃信封也不崩"的页（只 `res.username` 取字段，取不到渲染空值），313 vs 396 字符之差就是这些空字段。

## 附带观察（口径，不是缺陷判定）

- 本控制台**完全不走 SSO**：进门只看 `localStorage.accessKey` 非空，请求靠 `X-Zoss-Access-Key` / `X-Zoss-Secret-Key` 头；
  与 z-opc 主前端的 `sso_token` cookie 通道无交集，两者的登录态注入方式不可互抄。
- 401 处理：`services/api.js` 与 `z-frontend-common` 的拦截器都执行 `localStorage.removeItem(...)` + `window.location.href = '/login'`
  ⇒ 任何一次瞬时 401 都会整页重载并丢掉应用内状态（`off` 跑里 `/users` 最终落在 text=0 的 `/dashboard`）。
- `/objects/:bucketName` 在路由表里没有菜单项，但 `BucketList` 行名是 `<a onClick={() => goToObjects(text)}>` ⇒
  实测点行名可从 `/buckets` 进入该页（"菜单里没有"不等于"进不去"）。

## 本轮主动没提交的破坏性控件

普查闸门：非 GET/HEAD 一律在浏览器层 `route.abort()` 并记账；点开层后点"取消"。
账上 `destructive=True` 而未提交的控件共 9 类：创建存储桶、删除（桶行内）、新建文件夹、上传文件、复制、删除（对象行内）、
修改用户名、重置 AK/SK、修改密码。"拦非 GET"这一支做了阳性对照：故意点"重置密钥"（`POST /api/v1/user/reset-key`）
→ 账上 `{"ev":"BLOCK_NONGET","method":"POST",…}` 且请求未出浏览器；所用凭证为哨兵串，双保险。

## 建议修法（未动代码，等裁定）

1. `Dashboard.jsx` 补 `import {FileSizeFormatter} from '@yuku123/z-oss-frontend-component'`（或从 `ObjectList` 同款路径复用），
   并给 `AppLayout` 的 `*` 分支一个真 404 页而不是重定向到会崩的页。
2. 统一 URL 契约：要么前端改 query-param 形状（与控制器一致），要么控制器补 `@PathVariable` 别名；
   并让未注册路径返回 404 而非 500（现在这个形状会把"路径写错"伪装成"服务器坏了"）。
3. 前端数据层统一走 `createRequest` 的解包实例，或让 `services/api.js` 也 `return response.data.data`（按 `Result` 约定），
   使 `dataSource` 永远拿到数组；`/users` 的空字段问题同源。
4. 500/404 的兜底：`error.response.data?.message` 取不到时，把 `status + path` 显示出来，别只剩 axios 的英文串。
