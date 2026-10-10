import { AppstoreOutlined, CloudUploadOutlined, DashboardOutlined, HomeOutlined, UserOutlined } from '@ant-design/icons'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import BucketList from './pages/BucketList'
import ObjectList from './pages/ObjectList'
import UserManage from './pages/UserManage'

export const isAuthenticated = () => !!localStorage.getItem('accessKey')


export {default as Dashboard} from './pages/Dashboard'
export {default as Login} from './pages/Login'
export {default as BucketList} from './pages/BucketList'
export {default as ObjectList} from './pages/ObjectList'
export {default as UserManage} from './pages/UserManage'
import HomePage from './pages/HomePage'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-oss', short: 'z-oss' }

export const menuItems = [
    { key: '/z-oss/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-oss/dashboard', label: '概览', icon: <DashboardOutlined /> },
    { key: '/z-oss/bucket', label: '存储桶', icon: <AppstoreOutlined /> },
    { key: '/z-oss/object', label: '对象管理', icon: <CloudUploadOutlined /> },
    { key: '/z-oss/user', label: '用户管理', icon: <UserOutlined /> },
]

export const routes = [
    { path: '/z-oss/home', Component: HomePage },
    { path: '/z-oss/dashboard', Component: Dashboard },
    { path: '/z-oss/bucket', Component: BucketList },
    { path: '/z-oss/object', Component: ObjectList },
    { path: '/z-oss/user', Component: UserManage },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
