/**
 * Base64 estándar sin saltos de línea, equivalente a Base64.NO_WRAP de Android.
 */

export function bytesABase64(bytes) {
    const vista = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
    let binario = "";

    for (let i = 0; i < vista.length; i += 0x8000) {
        binario += String.fromCharCode.apply(null, vista.subarray(i, i + 0x8000));
    }

    return window.btoa(binario);
}

export function base64ABytes(texto) {
    const limpio = String(texto || "").replace(/\s+/g, "");
    const binario = window.atob(limpio);
    const bytes = new Uint8Array(binario.length);

    for (let i = 0; i < binario.length; i += 1) {
        bytes[i] = binario.charCodeAt(i);
    }

    return bytes;
}

export function textoABytes(texto) {
    return new TextEncoder().encode(texto);
}

export function bytesATexto(bytes) {
    return new TextDecoder().decode(bytes);
}
