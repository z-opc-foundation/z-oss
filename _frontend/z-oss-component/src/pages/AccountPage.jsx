import {useCallback, useEffect, useState} from 'react'
import {Alert, Button, Card, Descriptions, Tag} from 'antd'
import {ReloadOutlined} from '@ant-design/icons'
import {ossApi, ossErrorText} from '../services/api'
import {PageHeader} from '@/common/components/ui'

/**
 * 对象存储身份页 —— 先回答"你是谁、能不能看桶"，再谈列表。
 *
 * 这一页存在的理由是一个容易混淆的区别：
 * "这个平台账号在 z-oss 里没开户" 与 "你名下确实没有桶" 在前端长得很像（都是空表），
 * 但前者是配置缺口、后者是数据为空。OssAdminController 用 409 + message 把两者分开，
 * 这里就照这个区别显示：未开户时明确写"不返回空列表冒充没有桶"，并给出去 z-oss 开户的动作。
 */
export default function AccountPage() {
    const [data, setData] = useState(null)
    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            setData(await ossApi.whoami())
            setError(null)
        } catch (e) {
            setData(null)
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
                title="对象存储 · 访问身份"
                subtitle="z-oss 原生接口要 AK/SK；本平台走 /api/oss-admin/**，在同一 JVM 内直接调 z-oss 服务，鉴权用平台 SSO"
                extra={<Button icon={<ReloadOutlined/>} onClick={load} loading={loading}>刷新</Button>}
            />
            {error && (
                <Alert
                    style={{marginBottom: 16}}
                    type="error"
                    showIcon
                    message="身份映射查询失败"
                    description={ossErrorText(error)}
                />
            )}
            {data && (
                <Card size="small">
                    <Descriptions column={2} size="small">
                        <Descriptions.Item label="平台登录账号">{data.platform_username || '-'}</Descriptions.Item>
                        <Descriptions.Item label="租户">{data.platform_tenant || '-'}</Descriptions.Item>
                        <Descriptions.Item label="z-oss 开户">
                            {data.provisioned
                                ? <Tag color="success">已开户</Tag>
                                : <Tag color="warning">未开户</Tag>}
                        </Descriptions.Item>
                        <Descriptions.Item label="z_oss_user id">{data.zoss_user_id ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label="账号状态">{data.zoss_status ?? '-'}</Descriptions.Item>
                    </Descriptions>
                    {!data.provisioned && (
                        <Alert
                            style={{marginTop: 12}}
                            type="warning"
                            showIcon
                            message={`平台账号 ${data.platform_username} 在 z-oss 的 z_oss_user 表里没有同名记录`}
                            description="桶列表/对象浏览会因此报 409，而不是显示空表 —— 需要先在 z-oss 侧为该用户名开户（POST /api/v1/user/register，需管理员凭证），本平台不在页面上代做写操作。"
                        />
                    )}
                </Card>
            )}
        </div>
    )
}
