import { TEXTO_SIN_MENSAJES } from "../config/constantes";
import {
    ALMACEN_CHATS,
    actualizarEnAlmacen,
    borrarPorClave,
    obtenerPorClave,
    obtenerTodos
} from "./database";

export function obtenerChats() {
    return obtenerTodos(ALMACEN_CHATS);
}

export function buscarChat(contactUsername) {
    return obtenerPorClave(ALMACEN_CHATS, contactUsername);
}

export function borrarChat(contactUsername) {
    return borrarPorClave(ALMACEN_CHATS, contactUsername);
}

export function asegurarChat(contactUsername) {
    const ahora = Date.now();

    return actualizarEnAlmacen(ALMACEN_CHATS, contactUsername, (existente) =>
        existente || {
            contactUsername,
            createdAt: ahora,
            updatedAt: ahora,
            lastMessagePreview: TEXTO_SIN_MENSAJES
        }
    );
}

export function actualizarResumenChat(contactUsername, resumen, actualizadoEn) {
    const ahora = actualizadoEn || Date.now();

    return actualizarEnAlmacen(ALMACEN_CHATS, contactUsername, (existente) => ({
        contactUsername,
        createdAt: existente?.createdAt || ahora,
        updatedAt: ahora,
        lastMessagePreview: resumen
    }));
}
