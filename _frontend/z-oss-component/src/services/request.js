import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({baseURL: '/api/v1', tokenKey: 'accessKey', userInfoKey: 'secretKey'})

// X-Zoss-Access-Key / Secret-Key 头（z-oss 特有）
request.interceptors.request.use((config) => {
    const ak = localStorage.getItem('accessKey')
    const sk = localStorage.getItem('secretKey')
    if (ak) config.headers['X-Zoss-Access-Key'] = ak
    if (sk) config.headers['X-Zoss-Secret-Key'] = sk
    return config
})

export default request

// 前缀参数注入（lead 005 section 9.3）：默认 '/api/v1'。
export function configureOss(config) {
    if (config && config.apiBase !== undefined) {
        request.defaults.baseURL = config.apiBase
    }
}
