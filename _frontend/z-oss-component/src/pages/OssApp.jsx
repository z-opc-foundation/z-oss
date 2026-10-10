import {Navigate, Route, Routes} from 'react-router-dom'
import AccountPage from './AccountPage'
import BucketListPage from './BucketListPage'
import ObjectListPage from './ObjectListPage'

/**
 * z-oss（对象存储）管理面 — OpsWorkbench 以 /oss/* 通配挂进来。
 *
 * 后端是 z-opc 侧的 OssAdminController（/api/oss-admin/**）：在同一 JVM 内直接调 z-oss 的
 * IOssBucketService / IOssObjectService，鉴权用平台 SSO —— 因为 z-oss 原生 /api/v1/** 要
 * X-Zoss-Access-Key/Secret-Key，浏览器侧没有凭证下发通道（这正是 003 卡原来的阻塞点）。
 * 全部只读：后端只有 @GetMapping，这里也就没有任何写入口。
 *
 * 直达/刷新需要四处接线都齐（大需求 004 的四级路由契约）：
 *   MENU_GROUPS「对象存储」组 ↔ OPS_ROUTES 的 /oss + /oss/* ↔ main.jsx 顶层 <Route path="/oss/*">
 *   ↔ MainWebConfig.spaPaths 的 "/oss/**"。
 */
export default function OssApp() {
    return (
        <Routes>
            <Route index element={<Navigate to="buckets" replace/>}/>
            <Route path="buckets" element={<BucketListPage/>}/>
            <Route path="bucket" element={<BucketListPage/>}/>
            <Route path="objects" element={<ObjectListPage/>}/>
            <Route path="account" element={<AccountPage/>}/>
        </Routes>
    )
}
