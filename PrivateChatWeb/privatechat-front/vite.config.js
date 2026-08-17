import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// La web se sirve en https://lrivasvilla95.duckdns.org/privatechat/ porque la
// raíz del dominio ya la ocupa SwapCloset. `base` hace que todas las rutas de
// los assets se generen con ese prefijo.
//
// El WebSocket y la API siguen en la raíz (/chat, /upload, /file), que es donde
// los tiene el nginx del servidor, así que esos no llevan prefijo.
const BASE = '/privatechat/'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const target = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

    const proxy = {
        '/upload': { target, changeOrigin: true },
        '/file': { target, changeOrigin: true }
    }

    return {
        base: BASE,
        plugins: [react()],
        server: { proxy },
        preview: { proxy }
    }
})
