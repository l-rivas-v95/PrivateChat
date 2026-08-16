import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// El WebSocket NO pasa por aquí: va directo al servidor, que ya acepta
// cualquier origen (setAllowedOrigins("*") en WebSocketConfig.java).
//
// Lo que sí se reenvía es /upload y /file, porque esos controladores no mandan
// cabeceras CORS y el navegador bloquearía la petición desde otro puerto.
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const target = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

    return {
        plugins: [react()],
        server: {
            proxy: {
                '/upload': { target, changeOrigin: true },
                '/file': { target, changeOrigin: true }
            }
        }
    }
})
