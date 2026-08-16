import { MAX_AVATAR_BASE64_CHARS } from "../config/constantes";

/**
 * Portado de ImageBase64Encoder.kt.
 * El avatar viaja dentro de un payload WebSocket, y el servidor descarta
 * cualquier payload de más de 16.000 caracteres, así que hay que comprimir.
 */

const TAMANO_AVATAR_PX = 96;
const CALIDADES_JPEG = [0.55, 0.45, 0.35, 0.25];

export async function codificarAvatarABase64(fichero) {
    const bitmap = await leerImagen(fichero);

    const lienzo = document.createElement("canvas");
    lienzo.width = TAMANO_AVATAR_PX;
    lienzo.height = TAMANO_AVATAR_PX;

    const contexto = lienzo.getContext("2d");
    const { sx, sy, tam } = recorteCuadrado(bitmap.width, bitmap.height);
    contexto.drawImage(bitmap, sx, sy, tam, tam, 0, 0, TAMANO_AVATAR_PX, TAMANO_AVATAR_PX);

    for (const calidad of CALIDADES_JPEG) {
        const dataUrl = lienzo.toDataURL("image/jpeg", calidad);
        const base64 = dataUrl.split(",")[1] || "";

        if (base64.length <= MAX_AVATAR_BASE64_CHARS) {
            return base64;
        }
    }

    return null;
}

function recorteCuadrado(ancho, alto) {
    const tam = Math.min(ancho, alto);
    return {
        sx: Math.floor((ancho - tam) / 2),
        sy: Math.floor((alto - tam) / 2),
        tam
    };
}

function leerImagen(fichero) {
    if (window.createImageBitmap) {
        return createImageBitmap(fichero);
    }

    return new Promise((resolver, rechazar) => {
        const url = URL.createObjectURL(fichero);
        const imagen = new Image();

        imagen.onload = () => {
            URL.revokeObjectURL(url);
            resolver(imagen);
        };
        imagen.onerror = (error) => {
            URL.revokeObjectURL(url);
            rechazar(error);
        };

        imagen.src = url;
    });
}

export function avatarADataUrl(avatarBase64) {
    return avatarBase64 ? `data:image/jpeg;base64,${avatarBase64}` : null;
}
