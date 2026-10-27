import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout, ErrorBoundary } from '@yuku123/z-frontend-common'
import { menuItems, routes, HomePage, Login } from '@yuku123/z-oss-component/pages'
import { Result, Button } from 'antd'

function NotFound() {
    return (
        <Result
            status="404"
            title="页面不存在"
            subTitle="路由表里没有这条路径。"
            extra={<Button type="primary" onClick={() => { window.location.href = '/z-oss/home' }}>去首页</Button>}
        />
    )
}

// 用真 Login（POST /api/v1/user/login）。共享的 mock LoginPage 存的是 `token`，
// 而 z-oss 的桶/对象接口要的是 accessKey/secretKey 头 —— 走 mock 登录进去后每个请求都 401。
function LoginRoute() {
    return <Login />
}

function ProtectedShell() {
    const [user, setUser] = useState(null)
    const [ready, setReady] = useState(false)

    useEffect(() => {
        // 判据跟 pages-manifest 的 isAuthenticated 一致：accessKey 才是 z-oss 的会话凭证
        if (!localStorage.getItem('accessKey')) {
            window.location.replace('/z-oss/login')
            return
        }
        setUser({name: localStorage.getItem('username') || ''})
        setReady(true)
    }, [])

    if (!ready) return null

    return (
        <AppLayout
            menuItems={menuItems}
            appTitle="z-oss"
            appVersion="0.1.0"
            appUser={user}
            appIcon={{ icon: <img src="/icon.png" alt="z-oss" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }} />, color: '#7c3aed', label: 'z-oss' }}
        />
    )
}

/** lead 008 §16 suit 一次整合：登录路由 + 鉴权壳 + URL 即状态（§11/§14）。 */
export default function App() {
    return (
        <ErrorBoundary>
            <BrowserRouter>
                <Routes>
                    <Route path="/z-oss/login" element={<LoginRoute />} />
                    <Route element={<ProtectedShell />}>
                        <Route path="/" element={<Navigate to={menuItems[0].key} replace />} />
                        {routes.map((r) => (
                            <Route key={r.path} path={r.path} element={<r.Component />} />
                        ))}
                        <Route path="*" element={<NotFound />} />
                    </Route>
                </Routes>
            </BrowserRouter>
        </ErrorBoundary>
    )
}