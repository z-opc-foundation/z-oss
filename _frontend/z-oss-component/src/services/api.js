import axios from 'axios'
import {message} from 'antd'

const api = axios.create({
    baseURL: '/api/v1',
    timeout: 30000,
})

// 请求拦截器 - 添加认证头
api.interceptors.request.use(
    (config) => {
        const accessKey = localStorage.getItem('accessKey')
        const secretKey = localStorage.getItem('secretKey')
        if (accessKey && secretKey) {
            config.headers['X-Zoss-Access-Key'] = accessKey
            config.headers['X-Zoss-Secret-Key'] = secretKey
        }
        return config
    },
    (error) => Promise.reject(error)
)

// 响应拦截器
api.interceptors.response.use(
    (response) => response.data,
    (error) => {
        if (error.response) {
            const msg = error.response.data?.message || error.message
            message.error(msg)
            if (error.response.status === 401) {
                localStorage.removeItem('accessKey')
                localStorage.removeItem('secretKey')
                window.location.href = '/login'
            }
        } else {
            message.error('网络错误')
        }
        return Promise.reject(error)
    }
)

export default api

// 用户API
export const userApi = {
    register: (data) => api.post('/user/register', data),
    login: (data) => api.post('/user/login', data),
    getInfo: () => api.get('/user/info'),
    updateInfo: (data) => api.put('/user/info', data),
    resetKeys: () => api.post('/user/reset-key'),
    changePassword: (data) => api.post('/user/password', data),
}

// 存储桶API
export const bucketApi = {
    create: (data) => api.post('/bucket', data),
    delete: (name) => api.delete(`/bucket/${name}`),
    list: () => api.get('/bucket'),
    get: (name) => api.get(`/bucket/${name}`),
    update: (name, data) => api.put(`/bucket/${name}`, data),
    getAcl: (name) => api.get(`/bucket/${name}/acl`),
    setAcl: (name, acl) => api.put(`/bucket/${name}/acl`, {acl}),
    getPolicy: (name) => api.get(`/bucket/${name}/policy`),
    setPolicy: (name, policy) => api.put(`/bucket/${name}/policy`, {policy}),
    deletePolicy: (name) => api.delete(`/bucket/${name}/policy`),
    getStats: (name) => api.get(`/bucket/${name}/stats`),
}

// 对象API
export const objectApi = {
    upload: (bucketName, objectKey, file) => {
        const formData = new FormData()
        formData.append('file', file)
        return api.post(`/object/${bucketName}/${objectKey}`, formData, {
            headers: {'Content-Type': 'multipart/form-data'},
        })
    },
    download: (bucketName, objectKey) =>
        api.get(`/object/${bucketName}/${objectKey}`, {responseType: 'blob'}),
    delete: (bucketName, objectKey) => api.delete(`/object/${bucketName}/${objectKey}`),
    list: (bucketName, prefix) => api.get(`/object/${bucketName}`, {params: {prefix}}),
    head: (bucketName, objectKey) => api.head(`/object/${bucketName}/${objectKey}`),
    copy: (bucketName, objectKey, data) =>
        api.post(`/object/${bucketName}/${objectKey}/copy`, data),
    batchDelete: (bucketName, objectKeys) =>
        api.post(`/object/${bucketName}/batch-delete`, objectKeys),
    getUrl: (bucketName, objectKey, expires) =>
        api.get(`/object/${bucketName}/${objectKey}/url`, {params: {expires}}),
    createFolder: (bucketName, folderKey) => api.post(`/folder/${bucketName}/${folderKey}`, {}),
}
