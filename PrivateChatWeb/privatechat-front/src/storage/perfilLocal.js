import { COLOR_APP_POR_DEFECTO } from "../config/constantes";

/**
 * Equivalente a las SharedPreferences "private_chat_settings" de Android.
 */

const CLAVE_USER_ID = "private_chat_local_user_id";
const CLAVE_NOMBRE = "private_chat_display_name";
const CLAVE_AVATAR = "private_chat_local_avatar";
const CLAVE_COLOR = "private_chat_color";

export function obtenerUserIdLocal() {
    const guardado = localStorage.getItem(CLAVE_USER_ID);
    if (guardado) return guardado;

    const nuevo = crearUuid();
    localStorage.setItem(CLAVE_USER_ID, nuevo);
    return nuevo;
}

export function obtenerNombreLocal() {
    return localStorage.getItem(CLAVE_NOMBRE) || "User";
}

export function guardarNombreLocal(nombre) {
    localStorage.setItem(CLAVE_NOMBRE, nombre);
}

export function obtenerAvatarLocal() {
    return localStorage.getItem(CLAVE_AVATAR);
}

export function guardarAvatarLocal(avatarBase64) {
    if (avatarBase64) {
        localStorage.setItem(CLAVE_AVATAR, avatarBase64);
    } else {
        localStorage.removeItem(CLAVE_AVATAR);
    }
}

export function obtenerColorLocal() {
    return localStorage.getItem(CLAVE_COLOR) || COLOR_APP_POR_DEFECTO.id;
}

export function guardarColorLocal(colorId) {
    localStorage.setItem(CLAVE_COLOR, colorId);
}

export function crearUuid() {
    if (window.crypto?.randomUUID) {
        return window.crypto.randomUUID();
    }

    const bytes = window.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
