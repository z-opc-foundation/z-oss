import React from 'react'
import ReactDOM from 'react-dom/client'
import FileSizeFormatter from './FileSizeFormatter'

const root = ReactDOM.createRoot(document.getElementById('root'))
root.render(
    <div style={{padding: 40, display: 'flex', flexDirection: 'column', gap: 8}}>
        <FileSizeFormatter bytes={512}/>
        <FileSizeFormatter bytes={1024 * 50}/>
        <FileSizeFormatter bytes={1024 * 1024 * 5}/>
        <FileSizeFormatter bytes={1024 * 1024 * 50}/>
        <FileSizeFormatter bytes={1024 * 1024 * 1024 * 2}/>
    </div>
)
