import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
  resolve: { dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'] ,
        alias: process.env.LOCAL_SIBLINGS === '1' ? { '@yuku123/z-oss-component': '../z-oss-component/src' } : {}},
    server: {port: 3007, fs: {allow: ['..']}, proxy: {'/api': {target: 'http://localhost:8888', changeOrigin: true}}},
})
