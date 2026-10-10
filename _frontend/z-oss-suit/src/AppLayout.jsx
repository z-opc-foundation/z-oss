import React, {useCallback, useState} from 'react'
import {Alert, Button, Descriptions, Dropdown, Form, Input, Layout, Menu, message, Modal} from 'antd'
import {
    DashboardOutlined,
    DownOutlined,
    FolderOutlined,
    InfoCircleOutlined,
    KeyOutlined,
    LockOutlined,
    LogoutOutlined,
    UserOutlined,
} from '@ant-design/icons'
import {Navigate, Route, Routes, useLocation, useNavigate} from 'react-router-dom'
import {Dashboard, BucketList, ObjectList, UserManage} from '@yuku123/z-oss-component/pages'



import {userApi} from '@yuku123/z-oss-component/pages'

const {Header, Sider, Content} = Layout

export default function AppLayout() {
    const navigate = useNavigate()
    const location = useLocation()
    const [username, setUsername] = useState(localStorage.getItem('username') || 'User')
    const [collapsed, setCollapsed] = useState(false)

    // Info modal
    const [infoOpen, setInfoOpen] = useState(false)
    const [userInfo, setUserInfo] = useState({})

    // Keys modal
    const [keysOpen, setKeysOpen] = useState(false)
    const [newKeys, setNewKeys] = useState(null)
    const [resetLoading, setResetLoading] = useState(false)

    // Password modal
    const [passwordOpen, setPasswordOpen] = useState(false)
    const [passwordLoading, setPasswordLoading] = useState(false)
    const [passwordForm] = Form.useForm()

    const menuKey = '/' + location.pathname.split('/').filter(Boolean)[0] || 'dashboard'

    const loadUserInfo = useCallback(async () => {
        try {
            const res = await userApi.getInfo()
            setUserInfo(res)
        } catch (error) {
            console.error('Failed to load user info:', error)
        }
    }, [])

    const handleMenuClick = ({key}) => {
        navigate(key)
    }

    const handleDropdownClick = ({key}) => {
        switch (key) {
            case 'info':
                loadUserInfo()
                setInfoOpen(true)
                break
            case 'keys':
                setNewKeys(null)
                setKeysOpen(true)
                break
            case 'password':
                passwordForm.resetFields()
                setPasswordOpen(true)
                break
            case 'logout':
                Modal.confirm({
                    title: '提示',
                    content: '确定要退出登录吗？',
                    okText: '确定',
                    cancelText: '取消',
                    onOk: () => {
                        localStorage.removeItem('accessKey')
                        localStorage.removeItem('secretKey')
                        localStorage.removeItem('username')
                        navigate('/login')
                    },
                })
                break
        }
    }

    const handleResetKeys = async () => {
        try {
            setResetLoading(true)
            const res = await userApi.resetKeys()
            setNewKeys(res)
            localStorage.setItem('accessKey', res.accessKey)
            localStorage.setItem('secretKey', res.secretKey)
            message.success('AK/SK已重置，请妥善保管')
        } catch (error) {
            console.error('Reset keys failed:', error)
        } finally {
            setResetLoading(false)
        }
    }

    const handleChangePassword = async () => {
        try {
            const values = await passwordForm.validateFields()
            setPasswordLoading(true)
            await userApi.changePassword({
                oldPassword: values.oldPassword,
                newPassword: values.newPassword,
            })
            message.success('密码修改成功')
            setPasswordOpen(false)
            passwordForm.resetFields()
        } catch (error) {
            if (error.errorFields) return // form validation error
            console.error('Change password failed:', error)
        } finally {
            setPasswordLoading(false)
        }
    }

    const menuItems = [
        {key: '/dashboard', icon: <DashboardOutlined/>, label: '仪表盘'},
        {key: '/buckets', icon: <FolderOutlined/>, label: '存储桶'},
        {key: '/users', icon: <UserOutlined/>, label: '用户管理'},
    ]

    const dropdownItems = {
        items: [
            {key: 'info', icon: <InfoCircleOutlined/>, label: '个人信息'},
            {key: 'keys', icon: <KeyOutlined/>, label: 'AK/SK'},
            {key: 'password', icon: <LockOutlined/>, label: '修改密码'},
            {type: 'divider'},
            {key: 'logout', icon: <LogoutOutlined/>, label: '退出登录'},
        ],
        onClick: handleDropdownClick,
    }

    return (
        <Layout style={{minHeight: '100vh'}}>
            <Sider
                collapsible
                collapsed={collapsed}
                onCollapse={setCollapsed}
                theme="dark"
                width={200}
                style={{background: '#304156'}}
            >
                <div
                    style={{
                        padding: '20px',
                        textAlign: 'center',
                        color: '#fff',
                        borderBottom: '1px solid #3d4f66',
                    }}
                >
                    <img src="/icon.png" alt="OSS" style={{width: 28, height: 28, objectFit: "cover", borderRadius: 6}}/><span style={{fontSize: 16, fontWeight: 600, color: 'white'}}>OSS</span>
                    {!collapsed && <p style={{margin: '5px 0 0', fontSize: 12, color: '#8fa0b9'}}>对象存储管理</p>}
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[menuKey]}
                    items={menuItems}
                    onClick={handleMenuClick}
                    style={{background: '#304156', borderRight: 'none'}}
                />
            </Sider>
            <Layout>
                <Header
                    style={{
                        display: 'flex',
                        justifyContent: 'flex-end',
                        alignItems: 'center',
                        background: '#fff',
                        padding: '0 20px',
                        boxShadow: '0 1px 4px rgba(0,21,41,0.08)',
                    }}
                >
                    <Dropdown menu={dropdownItems}>
                        <Button type="text" style={{display: 'flex', alignItems: 'center', gap: 5}}>
                            <UserOutlined/>
                            {username}
                            <DownOutlined/>
                        </Button>
                    </Dropdown>
                </Header>
                <Content style={{background: '#f0f2f5', padding: 20, overflow: 'auto'}}>
                    <Routes>
                        <Route path="/dashboard" element={<Dashboard/>}/>
                        <Route path="/buckets" element={<BucketList/>}/>
                        <Route path="/objects/:bucketName" element={<ObjectList/>}/>
                        <Route path="/users" element={<UserManage/>}/>
                        <Route path="*" element={<Navigate to="/dashboard" replace/>}/>
                    </Routes>
                </Content>
            </Layout>

            {/* Info Modal */}
            <Modal title="个人信息" open={infoOpen} onCancel={() => setInfoOpen(false)} footer={null} width={500}>
                <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label="用户名">{userInfo.username}</Descriptions.Item>
                    <Descriptions.Item label="Access Key">{userInfo.accessKey}</Descriptions.Item>
                    <Descriptions.Item label="Secret Key">{userInfo.secretKey}</Descriptions.Item>
                    <Descriptions.Item label="状态">
                        {userInfo.status === 1 ? '启用' : '禁用'}
                    </Descriptions.Item>
                </Descriptions>
            </Modal>

            {/* Keys Modal */}
            <Modal
                title="重置AK/SK"
                open={keysOpen}
                onCancel={() => setKeysOpen(false)}
                footer={[
                    <Button key="close" onClick={() => setKeysOpen(false)}>关闭</Button>,
                    <Button key="reset" type="primary" loading={resetLoading} onClick={handleResetKeys}>
                        {newKeys ? '重新生成' : '重置密钥'}
                    </Button>,
                ]}
                width={500}
            >
                <Alert
                    message="警告"
                    description="重置AK/SK后，旧的密钥将失效，请妥善保管新密钥。"
                    type="warning"
                    showIcon
                    style={{marginBottom: 20}}
                />
                {newKeys && (
                    <Form layout="vertical">
                        <Form.Item label="Access Key">
                            <Input value={newKeys.accessKey} readOnly/>
                        </Form.Item>
                        <Form.Item label="Secret Key">
                            <Input value={newKeys.secretKey} readOnly/>
                        </Form.Item>
                    </Form>
                )}
            </Modal>

            {/* Password Modal */}
            <Modal
                title="修改密码"
                open={passwordOpen}
                onCancel={() => {
                    setPasswordOpen(false)
                    passwordForm.resetFields()
                }}
                footer={[
                    <Button key="cancel" onClick={() => {
                        setPasswordOpen(false);
                        passwordForm.resetFields()
                    }}>取消</Button>,
                    <Button key="ok" type="primary" loading={passwordLoading}
                            onClick={handleChangePassword}>确定</Button>,
                ]}
                width={400}
            >
                <Form form={passwordForm} layout="vertical">
                    <Form.Item
                        label="旧密码"
                        name="oldPassword"
                        rules={[{required: true, message: '请输入旧密码'}]}
                    >
                        <Input.Password placeholder="请输入旧密码"/>
                    </Form.Item>
                    <Form.Item
                        label="新密码"
                        name="newPassword"
                        rules={[
                            {required: true, message: '请输入新密码'},
                            {min: 6, message: '密码至少6位'},
                        ]}
                    >
                        <Input.Password placeholder="请输入新密码"/>
                    </Form.Item>
                </Form>
            </Modal>
        </Layout>
    )
}
