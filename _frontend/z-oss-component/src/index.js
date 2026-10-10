// z-oss 管理面（只读）前端模块出口
//
// 与其他中间件模块的关键区别：数据源不是 z-oss 的原生 HTTP 面 /api/v1/**（那要 AK/SK），
// 而是 z-opc 自己的 /api/oss-admin/**（main-starter 的 OssAdminController，进程内直调 z-oss 服务）。
// 因此本模块没有"透传上游报文"的函数，也不该有；字段含义见 services/api.js 顶部的报文形状。
export {default as OssApp} from './pages/OssApp'
export {ossApi, ossErrorText, isUnprovisionedError, fmtBytes} from './services/api'
