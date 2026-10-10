import {useCallback, useEffect, useState} from 'react'
import {Alert, Button, Card, Col, Descriptions, Input, Row, Select, Space, Statistic, Table, Tag} from 'antd'
import {FolderOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {useSearchParams} from 'react-router-dom'
import {fmtBytes, ossApi, ossErrorText, isUnprovisionedError} from '../services/api'
import {PageHeader, TableToolbar} from '@/common/components/ui'

const COLUMNS = [
    {
        title: 'Key', dataIndex: 'object_key', key: 'object_key',
        render: (v, r) => (
            <Space size={6}>
                {r.is_folder ? <FolderOutlined style={{color: '#faad14'}}/> : null}
                <code style={{fontSize: 12, wordBreak: 'break-all'}}>{v}</code>
            </Space>
        ),
    },
    {title: '大小', dataIndex: 'content_length', key: 'content_length', width: 110, render: (v) => fmtBytes(v)},
    {title: 'Content-Type', dataIndex: 'content_type', key: 'content_type', width: 190, render: (v) => v || '-'},
    {title: 'ETag', dataIndex: 'etag', key: 'etag', width: 160, render: (v) => (v ? <code style={{fontSize: 11}}>{String(v).slice(0, 16)}…</code> : '-')},
    {title: '创建时间', dataIndex: 'create_time', key: 'create_time', width: 190, render: (v) => v || '-'},
]

/**
 * 对象浏览 —— GET /api/oss-admin/objects + /api/oss-admin/bucket/detail。
 *
 * 统计分两栏是刻意的：对象数/总大小来自 IOssObjectService，physicalExists/activeProvider
 * 来自 IOssBucketService（两份 z-oss 自己的实现，字段互不覆盖）。
 * 尤其 "存储提供方里没有这个桶"（physicalExists=false）与 "桶在、里面 0 个对象" 是两件事，
 * 混在一格里就会把配置问题显示成"没数据" —— 这一格与 TASK-20260924-010 里
 * "内嵌没 bind 却被读成缓存为空" 是同一类错误，所以这里不允许合并显示。
 *
 * 只读：没有上传、没有删除、没有清空。
 */
export default function ObjectListPage() {
    const [params, setParams] = useSearchParams()
    const bucket = params.get('bucket') || ''
    const [buckets, setBuckets] = useState([])
    const [prefix, setPrefix] = useState(params.get('prefix') || '')
    const [rows, setRows] = useState([])
    const [detail, setDetail] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        ossApi.buckets()
            .then((r) => {
                setBuckets(r || [])
                if (!bucket && r && r.length) {
                    const q = new URLSearchParams(params)
                    q.set('bucket', r[0].name)
                    setParams(q, {replace: true})
                }
            })
            .catch((e) => setError(e))
        // 只在挂载时拉一次桶清单；bucket 变化由下面的 load 处理
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const load = useCallback(async () => {
        if (!bucket) {
            setRows([])
            setDetail(null)
            return
        }
        setLoading(true)
        try {
            const [list, d] = await Promise.all([
                ossApi.objects(bucket, prefix),
                ossApi.bucketDetail(bucket),
            ])
            setRows(list || [])
            setDetail(d)
            setError(null)
        } catch (e) {
            setRows([])
            setDetail(null)
            setError(e)
        } finally {
            setLoading(false)
        }
    }, [bucket, prefix])

    useEffect(() => {
        load()
    }, [load])

    const objStats = detail?.object_stats || {}
    const bStats = detail?.bucket_stats || {}

    return (
        <div>
            <PageHeader
                title="对象浏览"
                subtitle="GET /api/oss-admin/objects · 只读，不提供上传/删除"
            />
            <TableToolbar
                left={(
                    <>
                        <Select
                            style={{width: 220}}
                            placeholder="选择 Bucket"
                            value={bucket || undefined}
                            onChange={(v) => {
                                const q = new URLSearchParams(params)
                                q.set('bucket', v)
                                setParams(q)
                            }}
                            options={buckets.map((b) => ({value: b.name, label: b.name}))}
                            notFoundContent="该账号名下没有可见桶"
                        />
                        <Input
                            style={{width: 220}}
                            placeholder="前缀（目录）"
                            value={prefix}
                            onChange={(e) => setPrefix(e.target.value)}
                            onPressEnter={load}
                            allowClear
                        />
                    </>
                )}
                right={(
                    <>
                        <Button icon={<SearchOutlined/>} onClick={load}>查询</Button>
                        <Button icon={<ReloadOutlined/>} onClick={load} loading={loading}>刷新</Button>
                    </>
                )}
            />
            {error && (
                <Alert
                    style={{marginBottom: 12}}
                    type={isUnprovisionedError(error) ? 'warning' : 'error'}
                    showIcon
                    message={isUnprovisionedError(error) ? '当前账号在 z-oss 未开户' : '读不到对象'}
                    description={ossErrorText(error)}
                />
            )}
            {!error && !bucket && (
                <Alert style={{marginBottom: 12}} type="info" showIcon
                       message="还没有可选的 Bucket：先去「Bucket 列表」确认账号名下有桶"/>
            )}
            {detail && (
                <Row gutter={12} style={{marginBottom: 12}}>
                    <Col span={6}>
                        <Card size="small"><Statistic title="对象数" value={objStats.objectCount ?? '-'}/></Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small"><Statistic title="总大小" value={fmtBytes(objStats.totalSize)}/></Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small">
                            <Statistic
                                title="存储侧桶存在"
                                value={bStats.physicalExists == null ? '-' : (bStats.physicalExists ? '是' : '否')}
                                valueStyle={bStats.physicalExists === false ? {color: '#cf1322'} : undefined}
                            />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small">
                            <Descriptions column={1} size="small" title="提供方 / ACL">
                                <Descriptions.Item label="activeProvider">
                                    {objStats.activeProvider || bStats.activeProvider || '-'}
                                </Descriptions.Item>
                                <Descriptions.Item label="ACL">
                                    {detail.acl ? <Tag color="blue">{detail.acl}</Tag> : '-'}
                                </Descriptions.Item>
                            </Descriptions>
                        </Card>
                    </Col>
                </Row>
            )}
            {bStats.physicalExists === false && (
                <Alert
                    style={{marginBottom: 12}}
                    type="warning"
                    showIcon
                    message="元数据里有这个桶，但存储提供方侧不存在（physicalExists=false）"
                    description="下面的对象列表来自 z-oss 的元数据库，不代表存储侧真有这些对象 —— 这是配置/存储不一致，不是「没数据」。"
                />
            )}
            <Card size="small">
                <Table
                    rowKey={(r) => r.id ?? r.object_key}
                    size="small"
                    loading={loading}
                    columns={COLUMNS}
                    dataSource={rows}
                    pagination={{pageSize: 20, showSizeChanger: false}}
                    locale={{emptyText: error ? '查询失败（见上方错误）' : '接口通了，该前缀下没有对象'}}
                />
            </Card>
        </div>
    )
}
