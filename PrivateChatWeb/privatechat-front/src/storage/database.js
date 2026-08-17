/**
 * Equivalente a PrivateChatDatabase (Room), pero sobre IndexedDB.
 *
 * Almacenes:
 *   contacts  -> clave: username
 *   chats     -> clave: contactUsername
 *   messages  -> clave: messageId, índice por contactUsername
 */

const NOMBRE_BASE_DATOS = "private_chat";
const VERSION_BASE_DATOS = 2;

export const ALMACEN_CONTACTOS = "contacts";
export const ALMACEN_CHATS = "chats";
export const ALMACEN_MENSAJES = "messages";
export const ALMACEN_CLAVES = "claves";

let promesaBaseDatos = null;

export function abrirBaseDatos() {
    if (promesaBaseDatos) return promesaBaseDatos;

    promesaBaseDatos = new Promise((resolver, rechazar) => {
        const peticion = indexedDB.open(NOMBRE_BASE_DATOS, VERSION_BASE_DATOS);

        // Si otra pestaña tiene abierta una versión anterior, el navegador no
        // puede aplicar la subida de versión y este open se queda colgado para
        // siempre sin lanzar error. Sin esto, la app se queda muda: no carga
        // contactos ni claves y no avisa de nada.
        peticion.onblocked = () => {
            rechazar(
                new Error(
                    "Hay otra pestaña de PrivateChat abierta con una versión anterior. " +
                        "Ciérralas todas y vuelve a entrar."
                )
            );
        };

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

            // v2: aquí viven los objetos CryptoKey. Se guardan tal cual, sin
            // serializar a Base64, para que la privada pueda ser no exportable.
            if (!db.objectStoreNames.contains(ALMACEN_CLAVES)) {
                db.createObjectStore(ALMACEN_CLAVES);
            }
        };

        peticion.onsuccess = () => {
            const db = peticion.result;

            // Y al contrario: si es esta pestaña la que tiene la versión vieja,
            // hay que soltar la conexión para no bloquear a la nueva.
            db.onversionchange = () => {
                console.warn("Otra pestaña actualiza la base de datos. Cerrando esta conexión.");
                db.close();
                promesaBaseDatos = null;
            };

            resolver(db);
        };

        peticion.onerror = () => rechazar(peticion.error);
    });

    // Si falla, no dejar la promesa rota en caché: hay que poder reintentar.
    promesaBaseDatos.catch(() => {
        promesaBaseDatos = null;
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

/**
 * [clave] solo hace falta en almacenes sin keyPath, como el de las claves.
 */
export function guardar(nombreAlmacen, registro, clave) {
    return conAlmacen(nombreAlmacen, "readwrite", (almacen) =>
        envolverPeticion(clave === undefined ? almacen.put(registro) : almacen.put(registro, clave))
    );
}

export function guardarVarios(nombreAlmacen, registros) {
    return conAlmacen(nombreAlmacen, "readwrite", (almacen) =>
        Promise.all(registros.map((registro) => envolverPeticion(almacen.put(registro))))
    );
}

/**
 * Lee, transforma y escribe un registro DENTRO DE UNA SOLA TRANSACCIÓN.
 *
 * Imprescindible cuando llegan dos payloads casi a la vez que tocan el mismo
 * contacto (por ejemplo `contact_accept` y `profile_avatar`, que se envían
 * seguidos). Si el leer y el escribir van en transacciones distintas, el
 * segundo trabaja sobre una copia vieja y pisa lo que acababa de guardar el
 * primero: así se perdía la clave pública. IndexedDB serializa las
 * transacciones de escritura que se solapan sobre el mismo almacén, así que
 * metiéndolo todo en una el problema desaparece.
 *
 * [mutador] recibe el registro actual (o undefined) y devuelve el nuevo, o
 * null si no hay que escribir nada.
 */
export function actualizarEnAlmacen(nombreAlmacen, clave, mutador) {
    return conAlmacen(nombreAlmacen, "readwrite", async (almacen) => {
        const actual = await envolverPeticion(almacen.get(clave));
        const siguiente = mutador(actual);

        if (!siguiente) return actual;

        await envolverPeticion(
            almacen.keyPath ? almacen.put(siguiente) : almacen.put(siguiente, clave)
        );
        return siguiente;
    });
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
