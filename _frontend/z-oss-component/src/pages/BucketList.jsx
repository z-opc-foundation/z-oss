import React, {useEffect, useState} from 'react'
import {Button, Card, Form, Input, message, Modal, Select, Table, Tag} from 'antd'
import {DeleteOutlined, EditOutlined, FolderOpenOutlined, PlusOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import {bucketApi} from '../services/api'

function formatTime(time) {
    if (!time) return '-'
    return new Date(time).toLocaleString('zh-CN')
}

function getAclType(acl) {
    const map = {
        'private': 'default',
        'public-read': 'success',
        'public-read-write': 'warning',
    }
    return map[acl] || 'default'
}

export default function BucketList() {
    const navigate = useNavigate()
    const [buckets, setBuckets] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalOpen, setModalOpen] = useState(false)
    const [editingBucket, setEditingBucket] = useState(null)
    const [submitLoading, setSubmitLoading] = useState(false)
    const [form] = Form.useForm()

    useEffect(() => {
        loadBuckets()
    }, [])

    const loadBuckets = async () => {
        try {
            setLoading(true)
            const data = await bucketApi.list()
            setBuckets(data || [])
        } catch (error) {
            console.error('加载存储桶失败:', error)
        } finally {
            setLoading(false)
        }
    }

    const goToObjects = (bucketName) => {
        navigate(`/z-oss/objects/${bucketName}`)
    }

    const showEditDialog = (bucket) => {
        setEditingBucket(bucket)
        form.setFieldsValue({
            name: bucket.name,
            region: bucket.region,
            acl: bucket.acl,
        })
        setModalOpen(true)
    }

    const showCreateDialog = () => {
        setEditingBucket(null)
        form.resetFields()
        form.setFieldsValue({region: 'default', acl: 'private'})
        setModalOpen(true)
    }

    const handleSubmit = async (values) => {
        try {
            setSubmitLoading(true)
            if (editingBucket) {
                await bucketApi.update(editingBucket.name, {
                    region: values.region,
                    acl: values.acl,
                })
                message.success('更新成功')
            } else {
                await bucketApi.create(values)
                message.success('创建成功')
            }
            setModalOpen(false)
            setEditingBucket(null)
            form.resetFields()
            loadBuckets()
        } catch (error) {
            console.error('操作失败:', error)
        } finally {
            setSubmitLoading(false)
        }
    }

    const handleDelete = (bucket) => {
        Modal.confirm({
            title: '警告',
            content: `确定要删除存储桶 "${bucket.name}" 吗？`,
            okText: '确定',
            cancelText: '取消',
            onOk: async () => {
                try {
                    await bucketApi.delete(bucket.name)
                    message.success('删除成功')
                    loadBuckets()
                } catch {
                    // error handled by interceptor
                }
            },
        })
    }

    const columns = [
        {
            title: '桶名称',
            dataIndex: 'name',
            minWidth: 150,
            render: (text) => (
                <a onClick={() => goToObjects(text)} style={{cursor: 'pointer'}}>
                    {text}
                </a>
            ),
        },
        {
            title: '区域',
            dataIndex: 'region',
            width: 120,
        },
        {
            title: 'ACL',
            dataIndex: 'acl',
            width: 100,
            render: (acl) => <Tag color={getAclType(acl)}>{acl}</Tag>,
        },
        {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 180,
            render: (time) => formatTime(time),
        },
        {
            title: '操作',
            width: 200,
            fixed: 'right',
            render: (_, record) => (
                <>
                    <Button type="link" icon={<FolderOpenOutlined/>} onClick={() => goToObjects(record.name)}>
                        文件
                    </Button>
                    <Button type="link" icon={<EditOutlined/>} onClick={() => showEditDialog(record)}>
                        编辑
                    </Button>
                    <Button type="link" danger icon={<DeleteOutlined/>} onClick={() => handleDelete(record)}>
                        删除
                    </Button>
                </>
            ),
        },
    ]

    return (
        <div>
            <Card
                title="存储桶管理"
                extra={
                    <Button type="primary" icon={<PlusOutlined/>} onClick={showCreateDialog}>
                        创建存储桶
                    </Button>
                }
            >
                <Table
                    dataSource={buckets}
                    columns={columns}
                    loading={loading}
                    rowKey="name"
                    stripe
                    pagination={false}
                />
            </Card>

            <Modal
                title={editingBucket ? '编辑存储桶' : '创建存储桶'}
                open={modalOpen}
                onCancel={() => {
                    setModalOpen(false)
                    setEditingBucket(null)
                }}
                footer={[
                    <Button key="cancel" onClick={() => {
                        setModalOpen(false);
                        setEditingBucket(null)
                    }}>取消</Button>,
                    <Button key="submit" type="primary" loading={submitLoading} onClick={() => form.submit()}>
                        {editingBucket ? '保存' : '创建'}
                    </Button>,
                ]}
                width={500}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
                    initialValues={{region: 'default', acl: 'private'}}
                >
                    {!editingBucket && (
                        <Form.Item
                            label="桶名称"
                            name="name"
                            rules={[
                                {required: true, message: '请输入桶名称'},
                                {pattern: /^[a-z0-9][a-z0-9-]{2,62}[a-z0-9]$/, message: '桶名称格式不正确'},
                            ]}
                        >
                            <Input placeholder="小写字母、数字、连字符"/>
                        </Form.Item>
                    )}
                    <Form.Item label="区域" name="region">
                        <Select placeholder="选择区域">
                            <Select.Option value="default">默认区域 (default)</Select.Option>
                            <Select.Option value="cn-south">华南 (cn-south)</Select.Option>
                            <Select.Option value="cn-north">华北 (cn-north)</Select.Option>
                            <Select.Option value="cn-east">华东 (cn-east)</Select.Option>
                        </Select>
                    </Form.Item>
                    <Form.Item label="ACL" name="acl">
                        <Select placeholder="选择ACL">
                            <Select.Option value="private">私有 (private)</Select.Option>
                            <Select.Option value="public-read">公共读 (public-read)</Select.Option>
                            <Select.Option value="public-read-write">公共读写 (public-read-write)</Select.Option>
                        </Select>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}
