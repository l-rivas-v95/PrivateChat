/**
 * El front habla con el servidor por dos vías, y cada una tiene su regla:
 *
 * - WebSocket (/chat): va DIRECTO al servidor. El handler ya tiene
 *   setAllowedOrigins("*"), así que acepta la conexión aunque el front se
 *   sirva desde otro puerto. No hace falta proxy.
 *
 * - HTTP (/upload, /file): va SIEMPRE al mismo origen que el front, porque
 *   esos controladores no mandan cabeceras CORS. En desarrollo lo resuelve el
 *   proxy de Vite; en producción el front se sirve desde el propio servidor.
 */

const SERVIDOR_DEV_POR_DEFECTO = "ws://localhost:8080";

const configurado = (import.meta.env.VITE_CHAT_SERVER_URL || "").trim().replace(/\/+$/, "");
const servidor = configurado || (import.meta.env.DEV ? SERVIDOR_DEV_POR_DEFECTO : "");

export const WS_BASE_URL = servidor
    ? servidor.replace(/^https:/, "wss:").replace(/^http:/, "ws:")
    : `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`;

/** Cadena vacía = mismo origen que el front. */
export const HTTP_BASE_URL = "";

export function buildChatSocketUrl(userId) {
    return `${WS_BASE_URL}/chat?user=${encodeURIComponent(userId)}`;
}

export function buildHttpUrl(path) {
    return `${HTTP_BASE_URL}${path}`;
}
