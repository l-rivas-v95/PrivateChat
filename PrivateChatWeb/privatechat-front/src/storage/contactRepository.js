import {
    ESTADO_CONTACTO_ACEPTADO,
    ESTADO_CONTACTO_PENDIENTE,
    NOMBRE_CONTACTO_DESCONOCIDO
} from "../config/constantes";
import {
    ALMACEN_CONTACTOS,
    actualizarEnAlmacen,
    borrarPorClave,
    obtenerPorClave,
    obtenerTodos
} from "./database";

export function obtenerContactos() {
    return obtenerTodos(ALMACEN_CONTACTOS);
}

export function buscarContacto(username) {
    return obtenerPorClave(ALMACEN_CONTACTOS, username);
}

export function borrarContacto(username) {
    return borrarPorClave(ALMACEN_CONTACTOS, username);
}

/**
 * Mismas reglas de fusión que saveContactInternal() en el ViewModel de Android:
 * el nombre real nunca se pisa con "Usuario desconocido", un contacto aceptado
 * no vuelve a pendiente, y una clave pública que ya existe no se borra nunca.
 *
 * Todo va en una sola transacción para que dos payloads simultáneos no se
 * pisen el uno al otro.
 */
export function guardarContacto(username, displayName, publicKey, estado) {
    const ahora = Date.now();

    return actualizarEnAlmacen(ALMACEN_CONTACTOS, username, (existente) => ({
        username,
        displayName: siguienteNombre(existente, displayName) || username,
        publicKey: publicKey || existente?.publicKey || null,
        avatarBase64: existente?.avatarBase64 || null,
        avatarUpdatedAt: existente?.avatarUpdatedAt || null,
        createdAt: existente?.createdAt || ahora,
        lastSeenAt: ahora,
        status: siguienteEstado(existente, estado)
    }));
}

export function guardarAvatarContacto(username, avatarBase64, actualizadoEn) {
    const ahora = Date.now();

    return actualizarEnAlmacen(ALMACEN_CONTACTOS, username, (existente) => {
        // Si el avatar que ya tenemos es más nuevo, no lo pisamos.
        if (existente?.avatarUpdatedAt && existente.avatarUpdatedAt > actualizadoEn) {
            return null;
        }

        return {
            username,
            displayName: existente?.displayName || NOMBRE_CONTACTO_DESCONOCIDO,
            publicKey: existente?.publicKey || null,
            avatarBase64,
            avatarUpdatedAt: actualizadoEn,
            createdAt: existente?.createdAt || ahora,
            lastSeenAt: ahora,
            status: existente?.status || ESTADO_CONTACTO_PENDIENTE
        };
    });
}

export function renombrarContacto(username, nuevoNombre) {
    return actualizarEnAlmacen(ALMACEN_CONTACTOS, username, (existente) =>
        existente ? { ...existente, displayName: nuevoNombre } : null
    );
}

function siguienteNombre(existente, displayName) {
    if (!existente) return displayName;
    if (displayName === NOMBRE_CONTACTO_DESCONOCIDO) return existente.displayName;
    return displayName;
}

function siguienteEstado(existente, estado) {
    if (existente?.status === ESTADO_CONTACTO_ACEPTADO) return ESTADO_CONTACTO_ACEPTADO;
    if (estado === ESTADO_CONTACTO_ACEPTADO) return ESTADO_CONTACTO_ACEPTADO;
    return ESTADO_CONTACTO_PENDIENTE;
}
