/**
 * Portado de ChatPayloads.kt.
 * Los nombres de campo tienen que coincidir exactamente con los que espera
 * el servidor y la app Android.
 */

export const TIPOS_PAYLOAD = {
    MENSAJE: "message",
    MENSAJE_MULTIMEDIA: "media_message",
    ACK: "ack",
    INTERCAMBIO_CLAVES: "key_exchange",
    INVITACION_CONTACTO: "contact_invite",
    ACEPTACION_CONTACTO: "contact_accept",
    AVATAR_PERFIL: "profile_avatar",
    CONTACTO_QR: "privatechat_contact"
};

export function invitacionContacto(from, to, displayName, publicKey) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.INVITACION_CONTACTO,
        from,
        to,
        displayName,
        publicKey
    });
}

export function aceptacionContacto(from, to, displayName, publicKey) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.ACEPTACION_CONTACTO,
        from,
        to,
        displayName,
        publicKey
    });
}

export function mensajeCifrado(messageId, from, to, cipherText, iv) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.MENSAJE,
        id: messageId,
        from,
        to,
        cipherText,
        iv
    });
}

export function mensajePlano(messageId, from, to, text) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.MENSAJE,
        id: messageId,
        from,
        to,
        text
    });
}

export function mensajeMultimedia(messageId, from, to, fileId, ivBase64, mimeType) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.MENSAJE_MULTIMEDIA,
        id: messageId,
        from,
        to,
        fileId,
        iv: ivBase64,
        mimeType
    });
}

export function avatarPerfil(from, to, avatarBase64, updatedAt) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.AVATAR_PERFIL,
        from,
        to,
        avatarBase64,
        updatedAt: String(updatedAt)
    });
}

export function contactoQr(userId, displayName, publicKey) {
    return JSON.stringify({
        type: TIPOS_PAYLOAD.CONTACTO_QR,
        userId,
        displayName,
        publicKey
    });
}

export function parsearContactoQr(contenido) {
    const json = parsear(contenido);
    if (!json || json.type !== TIPOS_PAYLOAD.CONTACTO_QR) return null;

    const userId = String(json.userId || "").trim();
    if (!userId) return null;

    return {
        userId,
        displayName: String(json.displayName || "").trim() || userId,
        publicKey: String(json.publicKey || "")
    };
}

export function leerValor(payload, clave) {
    const json = parsear(payload);
    if (!json) return "";

    const valor = json[clave];
    return valor === undefined || valor === null ? "" : String(valor);
}

function parsear(payload) {
    try {
        const json = JSON.parse(payload);
        return typeof json === "object" && json !== null ? json : null;
    } catch {
        return null;
    }
}
