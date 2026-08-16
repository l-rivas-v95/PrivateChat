import { TEXTO_ERROR_DESCIFRADO } from "../config/constantes";
import { leerValor, mensajeCifrado, mensajePlano } from "../services/payloadService";
import { cifrarBytes, cifrarTexto, descifrarBytes, descifrarTexto } from "./cryptoUtils";
import { decodificarClavePublica, obtenerParClaves } from "./keyPairManager";

/**
 * Portado de ChatCryptoService.kt.
 * Si el contacto no tiene clave pública guardada se envía el mensaje en claro,
 * exactamente igual que hace la app Android.
 */

export async function construirPayloadSaliente(messageId, from, to, textoPlano, contacto) {
    const clavePublicaContacto = contacto?.publicKey;

    if (!clavePublicaContacto) {
        return mensajePlano(messageId, from, to, textoPlano);
    }

    try {
        const { privateKey } = await obtenerParClaves();
        const clavePublicaRemota = await decodificarClavePublica(clavePublicaContacto);
        const cifrado = await cifrarTexto(textoPlano, privateKey, clavePublicaRemota);

        return mensajeCifrado(messageId, from, to, cifrado.cipherText, cifrado.iv);
    } catch (error) {
        console.error("No se pudo cifrar el mensaje, se envía en claro:", error);
        return mensajePlano(messageId, from, to, textoPlano);
    }
}

export async function leerTextoDePayload(payload, from, contactos) {
    const texto = leerValor(payload, "text");
    if (texto) return texto;

    const cipherText = leerValor(payload, "cipherText");
    const iv = leerValor(payload, "iv");
    const clavePublicaContacto = contactos.find((contacto) => contacto.username === from)?.publicKey;

    if (!cipherText || !iv || !clavePublicaContacto) {
        return TEXTO_ERROR_DESCIFRADO;
    }

    try {
        const { privateKey } = await obtenerParClaves();
        const clavePublicaRemota = await decodificarClavePublica(clavePublicaContacto);
        return await descifrarTexto(cipherText, iv, privateKey, clavePublicaRemota);
    } catch (error) {
        console.error("No se pudo descifrar el mensaje:", error);
        return TEXTO_ERROR_DESCIFRADO;
    }
}

export async function cifrarFichero(bytes, clavePublicaContacto) {
    try {
        const { privateKey } = await obtenerParClaves();
        const clavePublicaRemota = await decodificarClavePublica(clavePublicaContacto);
        return await cifrarBytes(bytes, privateKey, clavePublicaRemota);
    } catch (error) {
        console.error("No se pudo cifrar el fichero:", error);
        return null;
    }
}

export async function descifrarFichero(bytesCifrados, iv, clavePublicaRemitente) {
    try {
        const { privateKey } = await obtenerParClaves();
        const clavePublicaRemota = await decodificarClavePublica(clavePublicaRemitente);
        return await descifrarBytes(bytesCifrados, iv, privateKey, clavePublicaRemota);
    } catch (error) {
        console.error("No se pudo descifrar el fichero:", error);
        return null;
    }
}
