import { ALMACEN_CLAVES, borrarPorClave, guardar, obtenerPorClave } from "../storage/database";
import {
    exportarClavePublica,
    generarParClaves,
    importarClavePrivada,
    importarClavePublica
} from "./cryptoUtils";

/**
 * Equivalente a KeyPairManager.kt, pero endurecido.
 *
 * En Android las claves viven en SharedPreferences, dentro del sandbox de la
 * app. En el navegador no hay sandbox equivalente: cualquier JavaScript del
 * mismo origen puede leer localStorage. Por eso aquí la clave privada se genera
 * como **no exportable** y se guarda como objeto `CryptoKey` en IndexedDB.
 *
 * El navegador la conserva entre recargas y la deja usar para derivar el
 * secreto ECDH, pero no hay forma de volver a convertirla en bytes. Ni siquiera
 * este código puede. La contrapartida es que no se puede hacer copia de
 * seguridad de la identidad: si se borran los datos del sitio, se pierde.
 */

const CLAVE_REGISTRO = "local";

// Formato antiguo, en Base64 dentro de localStorage. Se migra y se borra.
const LEGACY_PRIVADA = "private_chat_local_private_key";
const LEGACY_PUBLICA = "private_chat_local_public_key";

let parClavesEnMemoria = null;
let promesaCarga = null;
const cachePublicasRemotas = new Map();

async function migrarDesdeLocalStorage() {
    const privadaBase64 = localStorage.getItem(LEGACY_PRIVADA);
    const publicaBase64 = localStorage.getItem(LEGACY_PUBLICA);

    if (!privadaBase64 || !publicaBase64) return null;

    try {
        const registro = {
            privateKey: await importarClavePrivada(privadaBase64, false),
            publicKey: await importarClavePublica(publicaBase64),
            publicKeyBase64: publicaBase64
        };

        await guardar(ALMACEN_CLAVES, registro, CLAVE_REGISTRO);

        // Solo se borra el original cuando la copia ya está a salvo, para no
        // quedarse sin identidad si algo falla a mitad.
        localStorage.removeItem(LEGACY_PRIVADA);
        localStorage.removeItem(LEGACY_PUBLICA);

        console.info("Claves migradas de localStorage a IndexedDB. La privada ya no es exportable.");
        return registro;
    } catch (error) {
        console.error("No se pudieron migrar las claves antiguas:", error);
        return null;
    }
}

async function cargarOCrearParClaves() {
    const guardadas = await obtenerPorClave(ALMACEN_CLAVES, CLAVE_REGISTRO);

    if (guardadas?.privateKey && guardadas?.publicKey && guardadas?.publicKeyBase64) {
        return guardadas;
    }

    const migradas = await migrarDesdeLocalStorage();
    if (migradas) return migradas;

    const par = await generarParClaves(false);
    const registro = {
        privateKey: par.privateKey,
        publicKey: par.publicKey,
        publicKeyBase64: await exportarClavePublica(par.publicKey)
    };

    await guardar(ALMACEN_CLAVES, registro, CLAVE_REGISTRO);
    return registro;
}

export function obtenerParClaves() {
    if (parClavesEnMemoria) {
        return Promise.resolve(parClavesEnMemoria);
    }

    if (!promesaCarga) {
        promesaCarga = cargarOCrearParClaves()
            .then((par) => {
                parClavesEnMemoria = par;
                return par;
            })
            .finally(() => {
                promesaCarga = null;
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

/** Solo para pruebas o para empezar de cero a propósito. */
export async function borrarParClaves() {
    parClavesEnMemoria = null;
    await borrarPorClave(ALMACEN_CLAVES, CLAVE_REGISTRO);
}
