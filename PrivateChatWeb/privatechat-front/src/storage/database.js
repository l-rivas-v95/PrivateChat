/**
 * Equivalente a PrivateChatDatabase (Room), pero sobre IndexedDB.
 *
 * Almacenes:
 *   contacts  -> clave: username
 *   chats     -> clave: contactUsername
 *   messages  -> clave: messageId, índice por contactUsername
 */

const NOMBRE_BASE_DATOS = "private_chat";
const VERSION_BASE_DATOS = 1;

export const ALMACEN_CONTACTOS = "contacts";
export const ALMACEN_CHATS = "chats";
export const ALMACEN_MENSAJES = "messages";

let promesaBaseDatos = null;

export function abrirBaseDatos() {
    if (promesaBaseDatos) return promesaBaseDatos;

    promesaBaseDatos = new Promise((resolver, rechazar) => {
        const peticion = indexedDB.open(NOMBRE_BASE_DATOS, VERSION_BASE_DATOS);

        peticion.onupgradeneeded = () => {
            const db = peticion.result;

            if (!db.objectStoreNames.contains(ALMACEN_CONTACTOS)) {
                db.createObjectStore(ALMACEN_CONTACTOS, { keyPath: "username" });
            }

            if (!db.objectStoreNames.contains(ALMACEN_CHATS)) {
                db.createObjectStore(ALMACEN_CHATS, { keyPath: "contactUsername" });
            }

            if (!db.objectStoreNames.contains(ALMACEN_MENSAJES)) {
                const almacen = db.createObjectStore(ALMACEN_MENSAJES, { keyPath: "messageId" });
                almacen.createIndex("porContacto", "contactUsername", { unique: false });
                almacen.createIndex("porFecha", "timestamp", { unique: false });
            }
        };

        peticion.onsuccess = () => resolver(peticion.result);
        peticion.onerror = () => rechazar(peticion.error);
    });

    return promesaBaseDatos;
}

function envolverPeticion(peticion) {
    return new Promise((resolver, rechazar) => {
        peticion.onsuccess = () => resolver(peticion.result);
        peticion.onerror = () => rechazar(peticion.error);
    });
}

async function conAlmacen(nombreAlmacen, modo, accion) {
    const db = await abrirBaseDatos();

    return new Promise((resolver, rechazar) => {
        const transaccion = db.transaction(nombreAlmacen, modo);
        const almacen = transaccion.objectStore(nombreAlmacen);
        let resultado;

        Promise.resolve(accion(almacen))
            .then((valor) => {
                resultado = valor;
            })
            .catch(rechazar);

        transaccion.oncomplete = () => resolver(resultado);
        transaccion.onerror = () => rechazar(transaccion.error);
        transaccion.onabort = () => rechazar(transaccion.error);
    });
}

export function obtenerTodos(nombreAlmacen) {
    return conAlmacen(nombreAlmacen, "readonly", (almacen) => envolverPeticion(almacen.getAll()));
}

export function obtenerPorClave(nombreAlmacen, clave) {
    return conAlmacen(nombreAlmacen, "readonly", (almacen) =>
        envolverPeticion(almacen.get(clave))
    );
}

export function obtenerPorIndice(nombreAlmacen, nombreIndice, valor) {
    return conAlmacen(nombreAlmacen, "readonly", (almacen) =>
        envolverPeticion(almacen.index(nombreIndice).getAll(valor))
    );
}

export function guardar(nombreAlmacen, registro) {
    return conAlmacen(nombreAlmacen, "readwrite", (almacen) =>
        envolverPeticion(almacen.put(registro))
    );
}

export function guardarVarios(nombreAlmacen, registros) {
    return conAlmacen(nombreAlmacen, "readwrite", (almacen) =>
        Promise.all(registros.map((registro) => envolverPeticion(almacen.put(registro))))
    );
}

export function borrarPorClave(nombreAlmacen, clave) {
    return conAlmacen(nombreAlmacen, "readwrite", (almacen) =>
        envolverPeticion(almacen.delete(clave))
    );
}

export function borrarPorIndice(nombreAlmacen, nombreIndice, valor) {
    return conAlmacen(nombreAlmacen, "readwrite", async (almacen) => {
        const claves = await envolverPeticion(almacen.index(nombreIndice).getAllKeys(valor));
        await Promise.all(claves.map((clave) => envolverPeticion(almacen.delete(clave))));
        return claves.length;
    });
}
