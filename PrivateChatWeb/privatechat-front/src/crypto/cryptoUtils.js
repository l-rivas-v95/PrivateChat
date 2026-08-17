import { base64ABytes, bytesABase64, bytesATexto, textoABytes } from "../utils/base64";

/**
 * Portado de CryptoUtils.kt de la app Android.
 *
 * Esquema idéntico para que los dos clientes puedan hablar entre ellos:
 *   - Curva EC P-256 (secp256r1), la que genera KeyPairGenerator("EC").initialize(256)
 *   - ECDH -> secreto compartido (coordenada X, 32 bytes)
 *   - SHA-256 del secreto -> clave AES de 256 bits
 *   - AES/GCM/NoPadding con IV de 12 bytes y tag de 128 bits
 *
 * En Java doFinal() devuelve cipherText || tag, que es exactamente lo que
 * produce y espera WebCrypto, así que los mensajes son intercambiables.
 */

const CURVA = "P-256";
const TAM_IV_BYTES = 12;
const TAM_TAG_BITS = 128;

export function hayCryptoDisponible() {
    return typeof window !== "undefined" && !!window.crypto?.subtle;
}

function exigirCrypto() {
    if (!hayCryptoDisponible()) {
        throw new Error(
            "WebCrypto no está disponible. Abre la app en https:// o en http://localhost."
        );
    }
    return window.crypto.subtle;
}

/**
 * Genera el par ECDH.
 *
 * Con [exportable] a false la clave privada no se puede sacar del navegador:
 * ni `exportKey` ni ningún otro JavaScript del mismo origen pueden leerla, solo
 * usarla para derivar. La pública sigue siendo exportable pase lo que pase,
 * porque así lo define la especificación de WebCrypto para pares de claves, y
 * la necesitamos en Base64 para el QR y las invitaciones.
 */
export async function generarParClaves(exportable = false) {
    const subtle = exigirCrypto();

    return subtle.generateKey(
        { name: "ECDH", namedCurve: CURVA },
        exportable,
        ["deriveBits"]
    );
}

export async function exportarClavePublica(clavePublica) {
    const subtle = exigirCrypto();
    return bytesABase64(await subtle.exportKey("spki", clavePublica));
}

export async function exportarClavePrivada(clavePrivada) {
    const subtle = exigirCrypto();
    return bytesABase64(await subtle.exportKey("pkcs8", clavePrivada));
}

export async function importarClavePublica(clavePublicaBase64) {
    const subtle = exigirCrypto();

    return subtle.importKey(
        "spki",
        base64ABytes(clavePublicaBase64),
        { name: "ECDH", namedCurve: CURVA },
        true,
        []
    );
}

export async function importarClavePrivada(clavePrivadaBase64, exportable = false) {
    const subtle = exigirCrypto();

    return subtle.importKey(
        "pkcs8",
        base64ABytes(clavePrivadaBase64),
        { name: "ECDH", namedCurve: CURVA },
        exportable,
        ["deriveBits"]
    );
}

async function derivarClaveAes(clavePrivada, clavePublicaRemota) {
    const subtle = exigirCrypto();

    const secretoCompartido = await subtle.deriveBits(
        { name: "ECDH", public: clavePublicaRemota },
        clavePrivada,
        256
    );

    const bytesClave = await subtle.digest("SHA-256", secretoCompartido);

    return subtle.importKey("raw", bytesClave, { name: "AES-GCM" }, false, [
        "encrypt",
        "decrypt"
    ]);
}

function generarIv() {
    return window.crypto.getRandomValues(new Uint8Array(TAM_IV_BYTES));
}

export async function cifrarBytes(datos, clavePrivada, clavePublicaRemota) {
    const subtle = exigirCrypto();
    const claveAes = await derivarClaveAes(clavePrivada, clavePublicaRemota);
    const iv = generarIv();

    const cifrado = await subtle.encrypt(
        { name: "AES-GCM", iv, tagLength: TAM_TAG_BITS },
        claveAes,
        datos
    );

    return { bytesCifrados: new Uint8Array(cifrado), iv };
}

export async function descifrarBytes(bytesCifrados, iv, clavePrivada, clavePublicaRemota) {
    const subtle = exigirCrypto();
    const claveAes = await derivarClaveAes(clavePrivada, clavePublicaRemota);

    const descifrado = await subtle.decrypt(
        { name: "AES-GCM", iv, tagLength: TAM_TAG_BITS },
        claveAes,
        bytesCifrados
    );

    return new Uint8Array(descifrado);
}

export async function cifrarTexto(textoPlano, clavePrivada, clavePublicaRemota) {
    const { bytesCifrados, iv } = await cifrarBytes(
        textoABytes(textoPlano),
        clavePrivada,
        clavePublicaRemota
    );

    return {
        cipherText: bytesABase64(bytesCifrados),
        iv: bytesABase64(iv)
    };
}

export async function descifrarTexto(cipherTextBase64, ivBase64, clavePrivada, clavePublicaRemota) {
    const bytes = await descifrarBytes(
        base64ABytes(cipherTextBase64),
        base64ABytes(ivBase64),
        clavePrivada,
        clavePublicaRemota
    );

    return bytesATexto(bytes);
}
