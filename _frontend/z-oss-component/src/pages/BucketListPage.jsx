import {useCallback, useEffect, useState} from 'react'
import {Alert, Button, Card, Space, Table, Tag} from 'antd'
import {ReloadOutlined, RightOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import {ossApi, ossErrorText, isUnprovisionedError} from '../services/api'
import {PageHeader} from '@/common/components/ui'

const COLUMNS = [
    {
        title: 'Bucket', dataIndex: 'name', key: 'name',
        render: (v) => <code style={{fontSize: 12}}>{v}</code>,
    },
    {title: 'Region', dataIndex: 'region', key: 'region', width: 140, render: (v) => v || '-'},
    {
        title: 'ACL', dataIndex: 'acl', key: 'acl', width: 130,
        render: (v) => (v ? <Tag color={v === 'private' ? 'blue' : 'orange'}>{v}</Tag> : '-'),
    },
    {title: '创建时间', dataIndex: 'create_time', key: 'create_time', width: 190, render: (v) => v || '-'},
]

/**
 * Bucket 列表 —— GET /api/oss-admin/buckets（z-oss 的 IOssBucketService#listUserBuckets）。
 *
 * 显示的是"当前平台账号在 z-oss 名下可见的桶"，不是全平台桶：
 * service 层按 user_id 过滤，越权读别人的桶会由 validateBucket 抛 BucketException。
 *
 * 空表有两种可能，本页面把它们分开：接口成功且确实 0 条 ⇒ 明写"接口通了、确实是空"；
 * 报错（含 409 未开户）⇒ 显示错误原文，绝不退化成空表。
 */
export default function BucketListPage() {
    const navigate = useNavigate()
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const r = await ossApi.buckets()
            setRows(r || [])
            setError(null)
        } catch (e) {
            setRows([])
            setError(e)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        load()
    }, [load])

    return (
        <div>
            <PageHeader
                title="Bucket 列表"
                subtitle="GET /api/oss-admin/buckets · 只读，本平台不开建桶/删桶入口"
            />
            {error && (
                <Alert
                    style={{marginBottom: 12}}
                    type={isUnprovisionedError(error) ? 'warning' : 'error'}
                    showIcon
                    message={isUnprovisionedError(error) ? '当前账号在 z-oss 未开户，取不到桶' : '读不到桶列表'}
                    description={ossErrorText(error)}
                />
            )}
            {!error && rows.length === 0 && (
                <Alert
                    style={{marginBottom: 12}}
                    type="info"
                    showIcon
                    message="接口通了，这个账号名下确实还没有桶"
                />
            )}
            <Card size="small"
                  extra={<Button icon={<ReloadOutlined/>} onClick={load} loading={loading}>刷新</Button>}>
                <Table
                    rowKey={(r) => r.id ?? r.name}
                    size="small"
                    loading={loading}
                    columns={[
                        ...COLUMNS,
                        {
                            title: '', key: 'go', width: 96,
                            render: (_, r) => (
                                <Space>
                                    <Button size="small" type="link"
                                            onClick={() => navigate(`/oss/objects?bucket=${encodeURIComponent(r.name)}`)}>
                                        浏览对象 <RightOutlined/>
                                    </Button>
                                </Space>
                            ),
                        },
                    ]}
                    dataSource={rows}
                    pagination={{pageSize: 20, hideOnSinglePage: true}}
                />
            </Card>
        </div>
    )
}
