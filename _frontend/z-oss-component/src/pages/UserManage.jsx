import React, {useEffect, useState} from 'react'
import {Alert, Button, Card, Descriptions, Form, Input, message, Modal, Tag} from 'antd'
import {EditOutlined, LockOutlined, ReloadOutlined,} from '@ant-design/icons'
import {ErrorState} from '@yuku123/z-frontend-common/ui'
import {userApi} from '../services/api'

function formatTime(time) {
    if (!time) return '-'
    return new Date(time).toLocaleString('zh-CN')
}

export default function UserManage() {
    const [userInfo, setUserInfo] = useState({})
    const [loading, setLoading] = useState(false)
    // lead 008 §17：读失败常驻报错 + 重试，避免空白字段被误读成“用户没有密钥”。
    const [loadError, setLoadError] = useState(null)

    // Username modal
    const [usernameOpen, setUsernameOpen] = useState(false)
    const [usernameLoading, setUsernameLoading] = useState(false)
    const [usernameForm] = Form.useForm()

    // Keys modal
    const [keysOpen, setKeysOpen] = useState(false)
    const [newKeys, setNewKeys] = useState(null)
    const [resetLoading, setResetLoading] = useState(false)

    // Password modal
    const [passwordOpen, setPasswordOpen] = useState(false)
    const [passwordLoading, setPasswordLoading] = useState(false)
    const [passwordForm] = Form.useForm()

    useEffect(() => {
        loadUserInfo()
    }, [])

    const loadUserInfo = async () => {
        setLoading(true)
        try {
            const res = await userApi.getInfo()
            setUserInfo(res || {})
            setLoadError(null)
        } catch (error) {
            setLoadError(error)
        } finally {
            setLoading(false)
        }
    }

    const handleUpdateUsername = async (values) => {
        try {
            setUsernameLoading(true)
            await userApi.updateInfo({username: values.username})
            message.success('用户名修改成功')
            setUserInfo((prev) => ({...prev, username: values.username}))
            localStorage.setItem('username', values.username)
            setUsernameOpen(false)
            usernameForm.resetFields()
        } catch (error) {
            console.error('修改用户名失败:', error)
            message.error(error?.response?.data?.message || error?.message || '修改用户名失败')
        } finally {
            setUsernameLoading(false)
        }
    }

    const handleResetKeys = async () => {
        try {
            setResetLoading(true)
            const res = await userApi.resetKeys()
            setNewKeys(res)
            localStorage.setItem('accessKey', res.accessKey)
            localStorage.setItem('secretKey', res.secretKey)
            setUserInfo((prev) => ({...prev, accessKey: res.accessKey, secretKey: res.secretKey}))
            message.success('AK/SK已重置，请妥善保管')
        } catch (error) {
            console.error('重置密钥失败:', error)
            message.error(error?.response?.data?.message || error?.message || '重置密钥失败')
        } finally {
            setResetLoading(false)
        }
    }

    const handleChangePassword = async (values) => {
        try {
            setPasswordLoading(true)
            await userApi.changePassword({
                oldPassword: values.oldPassword,
                newPassword: values.newPassword,
            })
            message.success('密码修改成功')
            setPasswordOpen(false)
            passwordForm.resetFields()
        } catch (error) {
            console.error('修改密码失败:', error)
            message.error(error?.response?.data?.message || error?.message || '修改密码失败')
        } finally {
            setPasswordLoading(false)
        }
    }

    const copyToClipboard = (text) => {
        navigator.clipboard.writeText(text)
        message.success('已复制到剪贴板')
    }

    return (
        <div>
            {loadError && (
                <ErrorState
                    error={loadError}
                    title="用户信息没有读到"
                    description="接口没有返回当前用户资料，下面的空字段不代表账号没有密钥。"
                    onRetry={loadUserInfo}
                    style={{marginBottom: 16}}
                />
            )}
            <Card title="用户信息" loading={loading}>
                <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label="用户名">{userInfo.username || '-'}</Descriptions.Item>
                    <Descriptions.Item label="Access Key">
                        {userInfo.accessKey
                            ? <Tag style={{cursor: 'pointer'}} onClick={() => copyToClipboard(userInfo.accessKey)}>
                                {userInfo.accessKey}
                            </Tag>
                            : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Secret Key">
                        {userInfo.secretKey
                            ? <Tag color="warning" style={{cursor: 'pointer'}}
                                   onClick={() => copyToClipboard(userInfo.secretKey)}>
                                {userInfo.secretKey}
                            </Tag>
                            : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                        {userInfo.status === 1
                            ? <Tag color="success">启用</Tag>
                            : userInfo.status === 0
                                ? <Tag color="error">禁用</Tag>
                                : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="创建时间">
                        {formatTime(userInfo.createTime)}
                    </Descriptions.Item>
                </Descriptions>

                <div style={{marginTop: 20, display: 'flex', gap: 10}}>
                    <Button icon={<EditOutlined/>} onClick={() => {
                        // lead 008 §19：编辑弹窗回填当前用户名，不是让用户从空白重打一遍。
                        usernameForm.resetFields();
                        usernameForm.setFieldsValue({username: userInfo.username});
                        setUsernameOpen(true)
                    }}>
                        修改用户名
                    </Button>
                    <Button icon={<ReloadOutlined/>} onClick={() => {
                        setNewKeys(null);
                        setKeysOpen(true)
                    }}>
                        重置AK/SK
                    </Button>
                    <Button icon={<LockOutlined/>} onClick={() => {
                        passwordForm.resetFields();
                        setPasswordOpen(true)
                    }}>
                        修改密码
                    </Button>
                </div>
            </Card>

            <Card title="API密钥说明" style={{marginTop: 20}}>
                <Alert
                    message="如何访问API"
                    type="info"
                    showIcon={false}
                    description={
                        <div>
                            <p>使用以下HTTP头来访问API：</p>
                            <ul>
                                <li><code>X-Zoss-Access-Key</code>: 您的Access Key</li>
                                <li><code>X-Zoss-Secret-Key</code>: 您的Secret Key</li>
                            </ul>
                            <p>示例：</p>
                            <pre
                                style={{
                                    background: '#f5f7fa',
                                    padding: 10,
                                    borderRadius: 4,
                                    overflowX: 'auto',
                                    fontFamily: 'monospace',
                                }}
                            >
{`curl -H "X-Zoss-Access-Key: ${userInfo.accessKey || '<你的 Access Key>'}" \\
     -H "X-Zoss-Secret-Key: ${userInfo.secretKey || '<你的 Secret Key>'}" \\
     ${window.location.origin}/api/v1/bucket`}
              </pre>
                        </div>
                    }
                />
            </Card>

            {/* Edit Username Modal */}
            <Modal
                title="修改用户名"
                open={usernameOpen}
                maskClosable={false}
                onCancel={() => setUsernameOpen(false)}
                footer={[
                    <Button key="cancel" onClick={() => setUsernameOpen(false)}>取消</Button>,
                    <Button key="save" type="primary" loading={usernameLoading} onClick={() => usernameForm.submit()}>
                        保存
                    </Button>,
                ]}
                width={400}
            >
                <Form
                    form={usernameForm}
                    layout="vertical"
                    onFinish={handleUpdateUsername}
                >
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input placeholder="请输入新用户名"/>
                    </Form.Item>
                </Form>
            </Modal>

            {/* Reset Keys Modal */}
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
                            <Input
                                value={newKeys.accessKey}
                                readOnly
                                addonAfter={
                                    <a onClick={() => copyToClipboard(newKeys.accessKey)}>复制</a>
                                }
                            />
                        </Form.Item>
                        <Form.Item label="Secret Key">
                            <Input
                                value={newKeys.secretKey}
                                readOnly
                                addonAfter={
                                    <a onClick={() => copyToClipboard(newKeys.secretKey)}>复制</a>
                                }
                            />
                        </Form.Item>
                    </Form>
                )}
            </Modal>

            {/* Change Password Modal */}
            <Modal
                title="修改密码"
                open={passwordOpen}
                onCancel={() => {
                    setPasswordOpen(false);
                    passwordForm.resetFields()
                }}
                footer={[
                    <Button key="cancel" onClick={() => {
                        setPasswordOpen(false);
                        passwordForm.resetFields()
                    }}>取消</Button>,
                    <Button key="ok" type="primary" loading={passwordLoading} onClick={() => passwordForm.submit()}>
                        确定
                    </Button>,
                ]}
                width={400}
            >
                <Form
                    form={passwordForm}
                    layout="vertical"
                    onFinish={handleChangePassword}
                >
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
        </div>
    )
}
