import {
    ALMACEN_MENSAJES,
    actualizarEnAlmacen,
    borrarPorClave,
    borrarPorIndice,
    guardar,
    guardarVarios,
    obtenerPorIndice,
    obtenerTodos
} from "./database";

export async function obtenerMensajes() {
    const mensajes = await obtenerTodos(ALMACEN_MENSAJES);
    return mensajes.sort((a, b) => a.timestamp - b.timestamp);
}

export async function obtenerMensajesDeContacto(contactUsername) {
    const mensajes = await obtenerPorIndice(ALMACEN_MENSAJES, "porContacto", contactUsername);
    return mensajes.sort((a, b) => a.timestamp - b.timestamp);
}

export function guardarMensaje(mensaje) {
    return guardar(ALMACEN_MENSAJES, mensaje);
}

export function borrarMensaje(messageId) {
    return borrarPorClave(ALMACEN_MENSAJES, messageId);
}

export function borrarMensajesDeContacto(contactUsername) {
    return borrarPorIndice(ALMACEN_MENSAJES, "porContacto", contactUsername);
}

export function actualizarEstadoEntrega(messageId, estado) {
    return actualizarEnAlmacen(ALMACEN_MENSAJES, messageId, (mensaje) =>
        mensaje ? { ...mensaje, status: estado } : null
    );
}

export async function marcarEntrantesComoLeidos(contactUsername) {
    const mensajes = await obtenerMensajesDeContacto(contactUsername);
    const noLeidos = mensajes.filter((mensaje) => !mensaje.mine && !mensaje.isRead);
    if (noLeidos.length === 0) return 0;

    await guardarVarios(
        ALMACEN_MENSAJES,
        noLeidos.map((mensaje) => ({ ...mensaje, isRead: true }))
    );

    return noLeidos.length;
}
