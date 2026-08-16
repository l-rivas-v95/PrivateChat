import {
    ESTADO_CONTACTO_ACEPTADO,
    ESTADO_CONTACTO_PENDIENTE,
    NOMBRE_CONTACTO_DESCONOCIDO
} from "../config/constantes";
import { ALMACEN_CONTACTOS, borrarPorClave, guardar, obtenerPorClave, obtenerTodos } from "./database";

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
 * el nombre real nunca se pisa con "Usuario desconocido" y un contacto aceptado
 * no vuelve a pendiente.
 */
export async function guardarContacto(username, displayName, publicKey, estado) {
    const ahora = Date.now();
    const existente = await buscarContacto(username);

    const siguienteNombre = (() => {
        if (!existente) return displayName;
        if (displayName === NOMBRE_CONTACTO_DESCONOCIDO) return existente.displayName;
        return displayName;
    })();

    const siguienteEstado = (() => {
        if (existente?.status === ESTADO_CONTACTO_ACEPTADO) return ESTADO_CONTACTO_ACEPTADO;
        if (estado === ESTADO_CONTACTO_ACEPTADO) return ESTADO_CONTACTO_ACEPTADO;
        return ESTADO_CONTACTO_PENDIENTE;
    })();

    const contacto = {
        username,
        displayName: siguienteNombre || username,
        publicKey: publicKey || existente?.publicKey || null,
        avatarBase64: existente?.avatarBase64 || null,
        avatarUpdatedAt: existente?.avatarUpdatedAt || null,
        createdAt: existente?.createdAt || ahora,
        lastSeenAt: ahora,
        status: siguienteEstado
    };

    await guardar(ALMACEN_CONTACTOS, contacto);
    return contacto;
}

export async function guardarAvatarContacto(username, avatarBase64, actualizadoEn) {
    const ahora = Date.now();
    const existente = await buscarContacto(username);

    const contacto = {
        username,
        displayName: existente?.displayName || NOMBRE_CONTACTO_DESCONOCIDO,
        publicKey: existente?.publicKey || null,
        avatarBase64,
        avatarUpdatedAt: actualizadoEn,
        createdAt: existente?.createdAt || ahora,
        lastSeenAt: ahora,
        status: existente?.status || ESTADO_CONTACTO_PENDIENTE
    };

    await guardar(ALMACEN_CONTACTOS, contacto);
    return contacto;
}

export async function renombrarContacto(username, nuevoNombre) {
    const existente = await buscarContacto(username);
    if (!existente) return null;

    const contacto = { ...existente, displayName: nuevoNombre };
    await guardar(ALMACEN_CONTACTOS, contacto);
    return contacto;
}
