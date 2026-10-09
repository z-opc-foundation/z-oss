import {Tag} from 'antd'

function formatSize(bytes, decimals = 2) {
    if (!bytes || bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(decimals))} ${sizes[i]}`
}

export default function FileSizeFormatter({bytes, decimals = 2, colored = true}) {
    if (!colored) return <span>{formatSize(bytes, decimals)}</span>
    const mb = bytes / 1024 / 1024
    let color = 'blue'
    if (mb >= 100) color = 'red'
    else if (mb >= 10) color = 'orange'
    else if (mb >= 1) color = 'gold'
    return <Tag color={color}>{formatSize(bytes, decimals)}</Tag>
}

export {formatSize}
