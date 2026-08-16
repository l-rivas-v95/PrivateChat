import { TEXTO_SIN_MENSAJES } from "../config/constantes";
import { ALMACEN_CHATS, borrarPorClave, guardar, obtenerPorClave, obtenerTodos } from "./database";

export function obtenerChats() {
    return obtenerTodos(ALMACEN_CHATS);
}

export function buscarChat(contactUsername) {
    return obtenerPorClave(ALMACEN_CHATS, contactUsername);
}

export function borrarChat(contactUsername) {
    return borrarPorClave(ALMACEN_CHATS, contactUsername);
}

export async function asegurarChat(contactUsername) {
    const existente = await buscarChat(contactUsername);
    if (existente) return existente;

    const ahora = Date.now();
    const chat = {
        contactUsername,
        createdAt: ahora,
        updatedAt: ahora,
        lastMessagePreview: TEXTO_SIN_MENSAJES
    };

    await guardar(ALMACEN_CHATS, chat);
    return chat;
}

export async function actualizarResumenChat(contactUsername, resumen, actualizadoEn) {
    const chat = await asegurarChat(contactUsername);
    const siguiente = {
        ...chat,
        lastMessagePreview: resumen,
        updatedAt: actualizadoEn || Date.now()
    };

    await guardar(ALMACEN_CHATS, siguiente);
    return siguiente;
}
