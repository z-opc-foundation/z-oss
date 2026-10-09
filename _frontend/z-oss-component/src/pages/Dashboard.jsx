import React, {useEffect, useState} from 'react'
import {Button, Card, Col, Empty, Row, Timeline} from 'antd'
import {
    DatabaseOutlined,
    FileOutlined,
    FolderOutlined,
    PlusOutlined,
    UploadOutlined,
    UserOutlined,
} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import {bucketApi} from '../services/api'

export default function Dashboard() {
    const navigate = useNavigate()
    const username = localStorage.getItem('username') || 'User'
    const [stats, setStats] = useState({bucketCount: 0, objectCount: 0, totalSize: 0})
    const [activities, setActivities] = useState([])

    useEffect(() => {
        loadStats()
    }, [])

    const loadStats = async () => {
        try {
            const buckets = await bucketApi.list()
            let totalObjects = 0
            let totalSize = 0

            for (const bucket of buckets || []) {
                try {
                    const bucketStats = await bucketApi.getStats(bucket.name)
                    totalObjects += bucketStats.objectCount || 0
                    totalSize += bucketStats.totalSize || 0
                } catch {
                    // skip individual bucket errors
                }
            }

            setStats({
                bucketCount: buckets?.length || 0,
                objectCount: totalObjects,
                totalSize,
            })
        } catch (error) {
            console.error('加载统计信息失败:', error)
        }
    }

    const statCards = [
        {icon: <FolderOutlined/>, value: stats.bucketCount, label: '存储桶数量', bg: '#409eff'},
        {icon: <FileOutlined/>, value: stats.objectCount, label: '对象数量', bg: '#67c23a'},
        {
            icon: <DatabaseOutlined/>,
            value: <FileSizeFormatter bytes={stats.totalSize}/>,
            label: '总存储量',
            bg: '#e6a23c'
        },
        {icon: <UserOutlined/>, value: username, label: '当前用户', bg: '#f56c6c'},
    ]

    return (
        <div style={{padding: 0}}>
            <Row gutter={20}>
                {statCards.map((card, idx) => (
                    <Col span={6} key={idx}>
                        <Card>
                            <div style={{display: 'flex', alignItems: 'center'}}>
                                <div
                                    style={{
                                        width: 60,
                                        height: 60,
                                        borderRadius: 10,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: 28,
                                        color: '#fff',
                                        background: card.bg,
                                        marginRight: 15,
                                        flexShrink: 0,
                                    }}
                                >
                                    {card.icon}
                                </div>
                                <div style={{flex: 1, minWidth: 0}}>
                                    <div style={{fontSize: 24, fontWeight: 'bold', color: '#333', lineHeight: 1.2}}>
                                        {card.value}
                                    </div>
                                    <div style={{fontSize: 14, color: '#999', marginTop: 5}}>{card.label}</div>
                                </div>
                            </div>
                        </Card>
                    </Col>
                ))}
            </Row>

            <Row gutter={20} style={{marginTop: 20}}>
                <Col span={12}>
                    <Card title="最近操作">
                        {activities.length > 0 ? (
                            <Timeline
                                items={activities.map((item) => ({
                                    children: (
                                        <>
                                            <div>{item.content}</div>
                                            <div style={{color: '#999', fontSize: 12}}>{item.timestamp}</div>
                                        </>
                                    ),
                                    key: item.timestamp,
                                }))}
                            />
                        ) : (
                            <Empty description="暂无操作记录"/>
                        )}
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="快速操作">
                        <div style={{display: 'flex', gap: 10, flexWrap: 'wrap'}}>
                            <Button type="primary" icon={<PlusOutlined/>} onClick={() => navigate('/buckets')}>
                                创建存储桶
                            </Button>
                            <Button type="success" icon={<UploadOutlined/>} onClick={() => navigate('/buckets')}>
                                上传文件
                            </Button>
                        </div>
                    </Card>
                </Col>
            </Row>
        </div>
    )
}
