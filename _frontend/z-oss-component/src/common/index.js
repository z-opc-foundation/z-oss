/**
 * z-oss 的 `@/common` 桶。
 *
 * UI 组件走 `@/common/components/ui`。
 *
 * 请求实例这里自建 axios 而不是转发 @yuku123/z-frontend-common 的 createRequest：
 * 本仓 services/api.js 用的是 axios 语义（`request.get(url, {params})`、读
 * `e.response.data.message`），而共享层是 Result 信封解包那一套，两边对不上。
 * baseURL 留空 —— 调用方（suit / 主壳）按部署形态决定前缀，同源反代即可。
 */
import axios from 'axios'

export const request = axios.create({
    baseURL: '',
    timeout: 30000,
    headers: {Accept: 'application/json'},
})

export default request
