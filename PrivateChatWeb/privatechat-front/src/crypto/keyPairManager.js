import {
    exportarClavePrivada,
    exportarClavePublica,
    generarParClaves,
    importarClavePrivada,
    importarClavePublica
} from "./cryptoUtils";

/**
 * Portado de KeyPairManager.kt.
 * Donde Android usa SharedPreferences, aquí se usa localStorage.
 */

const CLAVE_PRIVADA_STORAGE = "private_chat_local_private_key";
const CLAVE_PUBLICA_STORAGE = "private_chat_local_public_key";

let parClavesEnMemoria = null;
let promesaCarga = null;
const cachePublicasRemotas = new Map();

async function cargarOCrearParClaves() {
    const privadaGuardada = localStorage.getItem(CLAVE_PRIVADA_STORAGE);
    const publicaGuardada = localStorage.getItem(CLAVE_PUBLICA_STORAGE);

    if (privadaGuardada && publicaGuardada) {
        try {
            return {
                privateKey: await importarClavePrivada(privadaGuardada),
                publicKey: await importarClavePublica(publicaGuardada),
                publicKeyBase64: publicaGuardada
            };
        } catch (error) {
            console.error("Claves locales corruptas, se regeneran:", error);
        }
    }

    const par = await generarParClaves();
    const publicaBase64 = await exportarClavePublica(par.publicKey);
    const privadaBase64 = await exportarClavePrivada(par.privateKey);

    localStorage.setItem(CLAVE_PUBLICA_STORAGE, publicaBase64);
    localStorage.setItem(CLAVE_PRIVADA_STORAGE, privadaBase64);

    return {
        privateKey: par.privateKey,
        publicKey: par.publicKey,
        publicKeyBase64: publicaBase64
    };
}

export function obtenerParClaves() {
    if (parClavesEnMemoria) {
        return Promise.resolve(parClavesEnMemoria);
    }

    if (!promesaCarga) {
        promesaCarga = cargarOCrearParClaves().then((par) => {
            parClavesEnMemoria = par;
            promesaCarga = null;
            return par;
        });
    }

    return promesaCarga;
}

export async function obtenerClavePublicaTexto() {
    const par = await obtenerParClaves();
    return par.publicKeyBase64;
}

export async function decodificarClavePublica(clavePublicaBase64) {
    if (cachePublicasRemotas.has(clavePublicaBase64)) {
        return cachePublicasRemotas.get(clavePublicaBase64);
    }

    const clave = await importarClavePublica(clavePublicaBase64);
    cachePublicasRemotas.set(clavePublicaBase64, clave);
    return clave;
}
