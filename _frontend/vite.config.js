import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    resolve: {
        preserveSymlinks: true,
    },
    server: {
        port: 3004,
        proxy: {'/api': {target: 'http://localhost:8888', changeOrigin: true}},
    },
})
