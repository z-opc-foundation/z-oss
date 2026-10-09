import {
    AppstoreOutlined, CloudUploadOutlined, DashboardOutlined, UserOutlined,
} from '@ant-design/icons'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import BucketList from './pages/BucketList'
import ObjectList from './pages/ObjectList'
import UserManage from './pages/UserManage'

export const isAuthenticated = () => !!localStorage.getItem('accessKey')

export const menuItems = [
    {key: '/dashboard', icon: <DashboardOutlined/>, label: '概览'},
    {key: '/bucket', icon: <AppstoreOutlined/>, label: '存储桶'},
    {key: '/object', icon: <CloudUploadOutlined/>, label: '对象管理'},
    {key: '/user', icon: <UserOutlined/>, label: '用户管理'},
]

const routeTable = [
    {path: 'dashboard', Component: Dashboard},
    {path: 'bucket', Component: BucketList},
    {path: 'object', Component: ObjectList},
    {path: 'user', Component: UserManage},
]
export {routeTable}
export {default as Dashboard} from './pages/Dashboard'
export {default as Login} from './pages/Login'
export {default as BucketList} from './pages/BucketList'
export {default as ObjectList} from './pages/ObjectList'
export {default as UserManage} from './pages/UserManage'
