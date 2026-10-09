import React, {useEffect, useMemo, useState} from 'react'
import {Breadcrumb, Button, Card, Form, Input, message, Modal, Select, Table, Upload,} from 'antd'
import {
    ArrowLeftOutlined,
    CopyOutlined,
    DeleteOutlined,
    DownloadOutlined,
    FileOutlined,
    FolderAddOutlined,
    FolderOutlined,
    UploadOutlined,
} from '@ant-design/icons'
import {useNavigate, useParams} from 'react-router-dom'
import {bucketApi, objectApi} from '../services/api'
import FileSizeFormatter from './FileSizeFormatter'

function formatTime(time) {
    if (!time) return '-'
    return new Date(time).toLocaleString('zh-CN')
}

export default function ObjectList() {
    const {bucketName} = useParams()
    const navigate = useNavigate()
    const [objects, setObjects] = useState([])
    const [buckets, setBuckets] = useState([])
    const [loading, setLoading] = useState(false)
    const [currentPrefix, setCurrentPrefix] = useState('')
    const [selectedRowKeys, setSelectedRowKeys] = useState([])

    // Folder dialog
    const [folderOpen, setFolderOpen] = useState(false)
    const [folderName, setFolderName] = useState('')
    const [folderLoading, setFolderLoading] = useState(false)

    // Copy dialog
    const [copyOpen, setCopyOpen] = useState(false)
    const [copyLoading, setCopyLoading] = useState(false)
    const [copyItem, setCopyItem] = useState(null)
    const [copyForm] = Form.useForm()

    const prefixList = useMemo(() => {
        if (!currentPrefix) return []
        const parts = currentPrefix.split('/').filter(Boolean)
        let path = ''
        return parts.map((part) => {
            path += part + '/'
            return {name: part, path}
        })
    }, [currentPrefix])

    const loadObjects = async () => {
        try {
            setLoading(true)
            const data = await objectApi.list(bucketName, currentPrefix)
            setObjects(data || [])
        } catch (error) {
            console.error('加载对象失败:', error)
        } finally {
            setLoading(false)
        }
    }

    const loadBuckets = async () => {
        try {
            const data = await bucketApi.list()
            setBuckets(data || [])
        } catch (error) {
            console.error('加载桶列表失败:', error)
        }
    }

    useEffect(() => {
        loadObjects()
        loadBuckets()
    }, [bucketName, currentPrefix])

    const handleObjectClick = (row) => {
        if (row.folder) {
            setCurrentPrefix(row.key)
        } else {
            handleDownload(row)
        }
    }

    const goToPrefix = (prefix) => {
        setCurrentPrefix(prefix)
    }

    const handleCreateFolder = async () => {
        if (!folderName.trim()) {
            message.warning('请输入文件夹名称')
            return
        }
        try {
            setFolderLoading(true)
            const folderKey = currentPrefix + folderName.trim() + '/'
            await objectApi.createFolder(bucketName, folderKey)
            message.success('创建成功')
            setFolderOpen(false)
            setFolderName('')
            loadObjects()
        } catch (error) {
            console.error('创建文件夹失败:', error)
        } finally {
            setFolderLoading(false)
        }
    }

    const handleDownload = async (row) => {
        try {
            const response = await objectApi.download(bucketName, row.key)
            const url = window.URL.createObjectURL(new Blob([response]))
            const link = document.createElement('a')
            link.href = url
            link.download = row.name
            link.click()
            window.URL.revokeObjectURL(url)
        } catch (error) {
            message.error('下载失败')
        }
    }

    const handleCopy = (row) => {
        setCopyItem(row)
        copyForm.setFieldsValue({
            destBucketName: bucketName,
            destObjectKey: row.key,
        })
        setCopyOpen(true)
    }

    const handleCopySubmit = async (values) => {
        try {
            setCopyLoading(true)
            await objectApi.copy(bucketName, copyItem.key, {
                destBucketName: values.destBucketName,
                destObjectKey: values.destObjectKey,
            })
            message.success('复制成功')
            setCopyOpen(false)
            setCopyItem(null)
        } catch (error) {
            console.error('复制失败:', error)
        } finally {
            setCopyLoading(false)
        }
    }

    const handleDelete = (row) => {
        Modal.confirm({
            title: '警告',
            content: `确定要删除 "${row.name}" 吗？`,
            okText: '确定',
            cancelText: '取消',
            onOk: async () => {
                try {
                    await objectApi.delete(bucketName, row.key)
                    message.success('删除成功')
                    loadObjects()
                } catch {
                    // handled by interceptor
                }
            },
        })
    }

    const handleBatchDelete = () => {
        Modal.confirm({
            title: '警告',
            content: `确定要删除选中的 ${selectedRowKeys.length} 个对象吗？`,
            okText: '确定',
            cancelText: '取消',
            onOk: async () => {
                try {
                    await objectApi.batchDelete(bucketName, selectedRowKeys)
                    message.success('批量删除成功')
                    setSelectedRowKeys([])
                    loadObjects()
                } catch {
                    // handled by interceptor
                }
            },
        })
    }

    // Upload handler
    const uploadProps = {
        name: 'file',
        multiple: true,
        action: `/api/v1/object/${bucketName}/`,
        headers: {
            'X-Zoss-Access-Key': localStorage.getItem('accessKey') || '',
            'X-Zoss-Secret-Key': localStorage.getItem('secretKey') || '',
        },
        data: {prefix: currentPrefix},
        onChange(info) {
            if (info.file.status === 'done') {
                message.success(`${info.file.name} 上传成功`)
                loadObjects()
            } else if (info.file.status === 'error') {
                message.error(`${info.file.name} 上传失败`)
            }
        },
        showUploadList: false,
    }

    const columns = [
        {
            title: '名称',
            dataIndex: 'name',
            minWidth: 300,
            render: (text, record) => (
                <div
                    style={{display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer'}}
                    onClick={() => handleObjectClick(record)}
                >
                    {record.folder ? (
                        <FolderOutlined style={{color: '#e6a23c'}}/>
                    ) : (
                        <FileOutlined style={{color: '#909399'}}/>
                    )}
                    <span>{text}</span>
                </div>
            ),
        },
        {
            title: '大小',
            dataIndex: 'size',
            width: 120,
            render: (size, record) => (record.folder ? '-' : <FileSizeFormatter bytes={size}/>),
        },
        {
            title: '类型',
            dataIndex: 'contentType',
            width: 150,
        },
        {
            title: '修改时间',
            dataIndex: 'lastModified',
            width: 180,
            render: (time) => formatTime(time),
        },
        {
            title: '操作',
            width: 200,
            fixed: 'right',
            render: (_, record) => (
                <>
                    {!record.folder && (
                        <Button type="link" icon={<DownloadOutlined/>} onClick={() => handleDownload(record)}>
                            下载
                        </Button>
                    )}
                    <Button type="link" icon={<CopyOutlined/>} onClick={() => handleCopy(record)}>
                        复制
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
                title={
                    <div style={{display: 'flex', alignItems: 'center', gap: 10}}>
                        <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate(-1)}>
                            返回
                        </Button>
                        <span style={{fontWeight: 'bold', fontSize: 16}}>
              {bucketName} / {currentPrefix || '根目录'}
            </span>
                    </div>
                }
                extra={
                    <div style={{display: 'flex', gap: 10}}>
                        <Button icon={<FolderAddOutlined/>} onClick={() => setFolderOpen(true)}>
                            新建文件夹
                        </Button>
                        <Upload {...uploadProps}>
                            <Button type="primary" icon={<UploadOutlined/>}>
                                上传文件
                            </Button>
                        </Upload>
                    </div>
                }
            >
                <div style={{padding: '10px 0', borderBottom: '1px solid #eee', marginBottom: 10}}>
                    <Breadcrumb
                        separator="/"
                        items={[
                            {
                                title: (
                                    <a onClick={() => goToPrefix('')} style={{cursor: 'pointer'}}>
                                        桶根目录
                                    </a>
                                ),
                            },
                            ...prefixList.map((item) => ({
                                title: (
                                    <a onClick={() => goToPrefix(item.path)} style={{cursor: 'pointer'}}>
                                        {item.name}
                                    </a>
                                ),
                            })),
                        ]}
                    />
                </div>

                <Table
                    dataSource={objects}
                    columns={columns}
                    loading={loading}
                    rowKey="key"
                    stripe
                    pagination={false}
                    rowSelection={{
                        selectedRowKeys,
                        onChange: setSelectedRowKeys,
                        getCheckboxProps: (record) => ({name: record.key}),
                    }}
                />

                {selectedRowKeys.length > 0 && (
                    <div style={{marginTop: 15, paddingTop: 15, borderTop: '1px solid #eee'}}>
                        <Button type="primary" danger onClick={handleBatchDelete}>
                            批量删除 ({selectedRowKeys.length})
                        </Button>
                    </div>
                )}
            </Card>

            {/* Create Folder Dialog */}
            <Modal
                title="新建文件夹"
                open={folderOpen}
                onCancel={() => {
                    setFolderOpen(false);
                    setFolderName('')
                }}
                footer={[
                    <Button key="cancel" onClick={() => {
                        setFolderOpen(false);
                        setFolderName('')
                    }}>取消</Button>,
                    <Button key="create" type="primary" loading={folderLoading}
                            onClick={handleCreateFolder}>创建</Button>,
                ]}
                width={400}
            >
                <Form layout="vertical">
                    <Form.Item label="文件夹名称" required>
                        <Input
                            placeholder="请输入文件夹名称"
                            value={folderName}
                            onChange={(e) => setFolderName(e.target.value)}
                            onPressEnter={handleCreateFolder}
                        />
                    </Form.Item>
                </Form>
            </Modal>

            {/* Copy Dialog */}
            <Modal
                title="复制对象"
                open={copyOpen}
                onCancel={() => {
                    setCopyOpen(false);
                    setCopyItem(null)
                }}
                footer={[
                    <Button key="cancel" onClick={() => {
                        setCopyOpen(false);
                        setCopyItem(null)
                    }}>取消</Button>,
                    <Button key="copy" type="primary" loading={copyLoading}
                            onClick={() => copyForm.submit()}>复制</Button>,
                ]}
                width={500}
            >
                <Form
                    form={copyForm}
                    layout="vertical"
                    onFinish={handleCopySubmit}
                >
                    <Form.Item
                        label="目标桶"
                        name="destBucketName"
                        rules={[{required: true, message: '请选择目标桶'}]}
                    >
                        <Select placeholder="选择目标桶">
                            {buckets.map((b) => (
                                <Select.Option key={b.name} value={b.name}>
                                    {b.name}
                                </Select.Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item
                        label="目标路径"
                        name="destObjectKey"
                        rules={[{required: true, message: '请输入目标路径'}]}
                    >
                        <Input placeholder="请输入目标路径"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}
