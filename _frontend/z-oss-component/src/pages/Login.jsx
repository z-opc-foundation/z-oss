import React, {useState} from 'react'
import {Button, Card, Divider, Form, Input, message, Modal} from 'antd'
import {LockOutlined, UserOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import {userApi} from '../services/api'

export default function Login() {
    const navigate = useNavigate()
    const [loginLoading, setLoginLoading] = useState(false)
    const [registerOpen, setRegisterOpen] = useState(false)
    const [registerLoading, setRegisterLoading] = useState(false)
    const [loginForm] = Form.useForm()
    const [registerForm] = Form.useForm()

    const handleLogin = async (values) => {
        try {
            setLoginLoading(true)
            const res = await userApi.login(values)
            localStorage.setItem('accessKey', res.user.accessKey)
            localStorage.setItem('secretKey', res.user.secretKey)
            localStorage.setItem('username', res.user.username)
            message.success('登录成功')
            navigate('/')
        } catch (error) {
            console.error('登录失败:', error)
        } finally {
            setLoginLoading(false)
        }
    }

    const handleRegister = async (values) => {
        try {
            setRegisterLoading(true)
            await userApi.register(values)
            message.success('注册成功，请登录')
            setRegisterOpen(false)
            loginForm.setFieldsValue({username: values.username, password: ''})
            registerForm.resetFields()
        } catch (error) {
            console.error('注册失败:', error)
        } finally {
            setRegisterLoading(false)
        }
    }

    return (
        <div
            style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                height: '100vh',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            }}
        >
            <Card
                style={{width: 400}}
                styles={{
                    header: {textAlign: 'center'},
                }}
                title={
                    <div>
                        <h2 style={{margin: 0}}>Z-OSS 对象存储</h2>
                        <p style={{margin: '5px 0 0', color: '#666', fontSize: 14}}>请登录您的账户</p>
                    </div>
                }
            >
                <Form form={loginForm} onFinish={handleLogin} layout="vertical" size="large">
                    <Form.Item
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input prefix={<UserOutlined/>} placeholder="用户名"/>
                    </Form.Item>
                    <Form.Item
                        name="password"
                        rules={[{required: true, message: '请输入密码'}]}
                    >
                        <Input.Password prefix={<LockOutlined/>} placeholder="密码"/>
                    </Form.Item>
                    <Form.Item>
                        <Button type="primary" htmlType="submit" block loading={loginLoading}>
                            登录
                        </Button>
                    </Form.Item>
                    <Divider plain>或</Divider>
                    <Form.Item>
                        <Button block onClick={() => setRegisterOpen(true)}>
                            注册新账户
                        </Button>
                    </Form.Item>
                </Form>
            </Card>

            <Modal
                title="注册新账户"
                open={registerOpen}
                onCancel={() => setRegisterOpen(false)}
                footer={[
                    <Button key="cancel" onClick={() => setRegisterOpen(false)}>取消</Button>,
                    <Button key="register" type="primary" loading={registerLoading}
                            onClick={() => registerForm.submit()}>
                        注册
                    </Button>,
                ]}
                width={400}
            >
                <Form
                    form={registerForm}
                    layout="vertical"
                    onFinish={handleRegister}
                >
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input placeholder="请输入用户名"/>
                    </Form.Item>
                    <Form.Item
                        label="密码"
                        name="password"
                        rules={[
                            {required: true, message: '请输入密码'},
                            {min: 6, message: '密码至少6位'},
                        ]}
                    >
                        <Input.Password placeholder="请输入密码"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}
