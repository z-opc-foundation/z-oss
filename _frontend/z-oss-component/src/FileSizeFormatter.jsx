import React from 'react'
import {Tag} from 'antd'

/**
 * 文件大小格式化（B / KB / MB / GB / TB）
 * <p>
 * 抽自 z-oss/_frontend/src/pages/ObjectList.jsx
 *
 * @param {number} bytes
 * @param {number} decimals - 保留小数位（默认 2）
 */
function formatSize(bytes, decimals = 2) {
    if (!bytes || bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(decimals))} ${sizes[i]}`
}

export default function FileSizeFormatter({bytes, decimals = 2, colored = true}) {
    if (!colored) return <span>{formatSize(bytes, decimals)}</span>

    const kb = bytes / 1024
    const mb = kb / 1024
    let color = 'blue'
    if (mb >= 100) color = 'red'
    else if (mb >= 10) color = 'orange'
    else if (mb >= 1) color = 'gold'

    return <Tag color={color}>{formatSize(bytes, decimals)}</Tag>
}

// 命名导出：纯函数版本（不依赖 antd）
export {formatSize}
