import {request} from '@yuku123/z-frontend-common'

/**
 * z-oss（对象存储）管理面数据源。
 *
 * 数据来自 z-opc 自己的 {@code /api/oss-admin/**}（main-starter 里的 OssAdminController），
 * **不是** z-oss 的原生 HTTP 面 {@code /api/v1/**} —— 后者要求 X-Zoss-Access-Key / X-Zoss-Secret-Key
 * 两个头，实测带不带平台会话都是 401（浏览器侧没有凭证下发通道，这正是 TASK-20260924-003 卡住的地方）。
 * OssAdminController 的做法是"在同一 JVM 内直接调 z-oss 的 service bean"，
 * 鉴权换成平台 SSO + username → z_oss_user 映射，所以这里既不需要 AK/SK，也没有假数据。
 *
 * ⚠️ 刻意不调的两条现成路（都是假的，别"顺手改回去"）：
 *   - {@code GET /api/oss/list}      = E2EAliasController 直查 DB，且 catch(Exception) 后回空列表
 *                                      ⇒ "查不到"与"没有桶"不可区分；
 *   - {@code GET /api/oss/{bucket}}  = 返回 new OssBucketDetailResponse(name)，
 *                                      objects=[] / totalObjects=0 / totalSize=0 是**构造出来的常量**。
 *
 * 报文形状（对着 OssAdminController 源码，不是猜的）：
 *   GET /api/oss-admin/whoami        → {platform_username, platform_tenant, provisioned, zoss_user_id?, zoss_status?}
 *   GET /api/oss-admin/buckets       → OssBucket[]，全局 SNAKE_CASE ⇒
 *                                      {id, name, user_id, region, policy, acl, create_time, update_time, deleted}
 *   GET /api/oss-admin/bucket/detail → {bucket_name, region, acl, policy, create_time,
 *                                       bucket_stats:{bucketName, region, acl, createTime, physicalExists, activeProvider},
 *                                       object_stats:{bucketName, objectCount, totalSize, activeProvider}}
 *                                      ⚠ 两份统计来自不同 service：对象数/总大小只在 object_stats 里。
 *                                      内层 key 是 Map 的键，Jackson 的 SNAKE_CASE 不改 Map 键 ⇒ 保持 camelCase。
 *   GET /api/oss-admin/objects       → OssObject[]：{id, bucket_id, bucket_name, object_key, object_name,
 *                                      content_type, content_length, etag, storage_path, user_id,
 *                                      version_id, is_folder, metadata, create_time, update_time, deleted}
 *
 * 全部只读：后端只有 @GetMapping，本文件也就不提供 create/delete/upload —— 页面上不该出现这类按钮。
 */
export const ossApi = {
    whoami: () => request.get('/oss-admin/whoami'),
    buckets: () => request.get('/oss-admin/buckets'),
    bucketDetail: (name) => request.get('/oss-admin/bucket/detail', {params: {name}}),
    objects: (name, prefix) => request.get('/oss-admin/objects', {params: {name, prefix: prefix || undefined}}),
}

/**
 * 用户面走 z-oss 原生 {@code /api/v1/user/**}（UserController），与上面那套 oss-admin 不同：
 * oss-admin 是 SSO 只读面（OssAdminController 里只有 @GetMapping，写接口刻意不开）；
 * 注册/登录/改密码/重置密钥只在这条 AK/SK 路上。
 *
 * 鉴权头是 {@code X-Zoss-Access-Key}，而 login 返回的 token 形状是 "accessKey:secretKey"
 * —— 取冒号前半段当 accessKey 用。
 */
const USER_BASE = '/api/v1/user'

function akHeaders() {
    const token = localStorage.getItem('accessKey') || ''
    const [akFromToken, skFromToken] = token.includes(':') ? token.split(':') : [token, '']
    const ak = localStorage.getItem('accessKey') || akFromToken
    const sk = localStorage.getItem('secretKey') || skFromToken
    const headers = {}
    if (ak) headers['X-Zoss-Access-Key'] = ak.includes(':') ? ak.split(':')[0] : ak
    if (sk) headers['X-Zoss-Secret-Key'] = sk
    return headers
}

/** z-oss 原生面统一走 Result 信封，取出业务负载 */
const unwrap = (r) => r.data?.data

export const userApi = {
    login: (values) => request.post(`${USER_BASE}/login`, values).then((r) => r.data),
    register: (values) => request.post(`${USER_BASE}/register`, values).then((r) => r.data),
    getInfo: () => request.get(`${USER_BASE}/info`, {headers: akHeaders()}).then((r) => r.data),
    updateInfo: (values) => request.put(`${USER_BASE}/info`, values, {headers: akHeaders()}).then((r) => r.data),
    resetKeys: () => request.post(`${USER_BASE}/reset-key`, null, {headers: akHeaders()}).then((r) => r.data),
    changePassword: (values) => request.post(`${USER_BASE}/password`, values, {headers: akHeaders()}).then((r) => r.data),
}

/**
 * 后端错误有两种形态，都要透出 message，否则页面只剩一句 axios 默认文案：
 *  1. Result 信封（success=false）：{data:null, success:false, code:409, message:"平台账号 xxx 在 z-oss 未开户…"}
 *     —— 共享 request 的 defaultUnwrap 会把它 reject 掉，错误体在 e.response.data；
 *  2. GlobalExceptionAdvice 把 OssException/BucketException 按 HTTP 语义原样返回（401/403/404/500）+ 同一个信封。
 */
export function ossErrorText(e) {
    const data = e?.response?.data
    if (data && typeof data === 'object' && data.message) {
        const code = data.code != null ? ` (code ${data.code})` : ''
        return `${data.message}${code}`
    }
    if (typeof data === 'string' && data.trim()) return data.slice(0, 300)
    return e?.message || String(e)
}

/** 未开户是"配置缺口"而不是"数据为空"，页面必须把它和空列表区分开 */
export function isUnprovisionedError(e) {
    const d = e?.response?.data
    return !!d && typeof d === 'object' && (d.code === 409 || /未开户/.test(String(d.message || '')))
}

/**
 * 存储桶写面走 z-oss 原生 {@code /api/v1/bucket}（BucketController），带 AK/SK 头。
 * 不带 bucketName 的 GET 是列表，带的是详情 —— 后端把两个裸 @GetMapping 合成了一个。
 */
export const bucketApi = {
    list: () => request.get('/api/v1/bucket', {headers: akHeaders()}).then(unwrap),
    detail: (bucketName) => request.get('/api/v1/bucket', {headers: akHeaders(), params: {bucketName}}).then(unwrap),
    create: (values) => request.post('/api/v1/bucket', values, {headers: akHeaders()}).then(unwrap),
    update: (bucketName, values) => request.put('/api/v1/bucket', values, {headers: akHeaders(), params: {bucketName}}).then(unwrap),
    delete: (bucketName) => request.delete('/api/v1/bucket', {headers: akHeaders(), params: {bucketName}}).then(unwrap),
    getStats: (bucketName) => request.get('/api/v1/bucket/stats', {headers: akHeaders(), params: {bucketName}}).then(unwrap),
}

/**
 * 对象面走 {@code /api/v1/object} + {@code /api/v1/folder}（ObjectController）。
 * 下载是唯一不走 Result 信封的端点 —— controller 直写 Servlet 输出流（Jackson 包过就成 base64 了），
 * 所以 responseType 必须是 blob，不能按 JSON 解析。
 */
export const objectApi = {
    list: (bucketName, prefix) => request.get('/api/v1/object/list', {headers: akHeaders(), params: {bucketName, prefix: prefix || undefined}}).then(unwrap),
    createFolder: (bucketName, folderKey) => request.post('/api/v1/folder', null, {headers: akHeaders(), params: {bucketName, folderKey}}).then(unwrap),
    download: (bucketName, objectKey) => request.get('/api/v1/object', {headers: akHeaders(), params: {bucketName, objectKey}, responseType: 'blob'}).then((r) => r.data),
    copy: (bucketName, objectKey, values) => request.post('/api/v1/object/copy', values, {headers: akHeaders(), params: {bucketName, objectKey}}).then(unwrap),
    delete: (bucketName, objectKey) => request.delete('/api/v1/object', {headers: akHeaders(), params: {bucketName, objectKey}}).then(unwrap),
    batchDelete: (bucketName, objectKeys) => request.post('/api/v1/object/batch-delete', objectKeys, {headers: akHeaders(), params: {bucketName}}).then(unwrap),
}

export function fmtBytes(b) {
    if (b == null) return '-'
    const n = Number(b)
    if (!isFinite(n)) return '-'
    if (n < 1024) return `${n} B`
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KiB`
    if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MiB`
    return `${(n / 1024 / 1024 / 1024).toFixed(2)} GiB`
}
