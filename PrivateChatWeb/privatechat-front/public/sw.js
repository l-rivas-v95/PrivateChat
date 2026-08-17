/* eslint-env serviceworker */

/**
 * Service worker de PrivateChat.
 *
 * Solo se ocupa del "cascarón": HTML, JS, CSS e iconos. Los mensajes NO pasan
 * por aquí, van por WebSocket, y los adjuntos tampoco: `/upload` y `/file`
 * quedan fuera de la caché a propósito, porque el servidor borra el fichero en
 * la primera descarga y cachearlo sería guardar contenido cifrado que ya no se
 * puede volver a pedir.
 *
 * La base se deduce de dónde esté este propio fichero, así que funciona igual
 * servido en la raíz que en /privatechat/ sin tocar nada.
 *
 * Sube VERSION al cambiar la estrategia para que se limpien las cachés viejas.
 */

const VERSION = "v1";
const BASE = self.location.pathname.replace(/sw\.js$/, "");
const CACHE = `privatechat-${VERSION}-${BASE}`;

const ESENCIALES = [
    BASE,
    `${BASE}index.html`,
    `${BASE}manifest.webmanifest`,
    `${BASE}favicon.svg`,
    `${BASE}icon-192.png`,
    `${BASE}icon-512.png`
];

// Estas viven en la raíz del dominio, no bajo BASE.
const RUTAS_NUNCA_CACHEADAS = ["/chat", "/upload", "/file"];

self.addEventListener("install", (evento) => {
    evento.waitUntil(
        caches
            .open(CACHE)
            .then((cache) => cache.addAll(ESENCIALES))
            .then(() => self.skipWaiting())
            .catch((error) => console.error("[sw] fallo al precachear:", error))
    );
});

self.addEventListener("activate", (evento) => {
    evento.waitUntil(
        caches
            .keys()
            .then((nombres) =>
                Promise.all(
                    nombres
                        .filter((nombre) => nombre.startsWith("privatechat-") && nombre !== CACHE)
                        .map((nombre) => caches.delete(nombre))
                )
            )
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", (evento) => {
    const peticion = evento.request;

    if (peticion.method !== "GET") return;

    const url = new URL(peticion.url);
    if (url.origin !== self.location.origin) return;
    if (RUTAS_NUNCA_CACHEADAS.some((ruta) => url.pathname.startsWith(ruta))) return;
    if (!url.pathname.startsWith(BASE)) return;

    // Navegación: primero la red, y si no hay conexión se abre la app cacheada.
    // Así el usuario puede leer sus conversaciones aunque esté sin cobertura.
    if (peticion.mode === "navigate") {
        evento.respondWith(
            fetch(peticion)
                .then((respuesta) => {
                    guardarEnCache(peticion, respuesta.clone());
                    return respuesta;
                })
                .catch(() =>
                    caches
                        .match(`${BASE}index.html`)
                        .then((cacheada) => cacheada || caches.match(BASE))
                )
        );
        return;
    }

    // Recursos: se sirve lo cacheado al instante y se refresca por detrás.
    evento.respondWith(
        caches.match(peticion).then((cacheada) => {
            const desdeRed = fetch(peticion)
                .then((respuesta) => {
                    guardarEnCache(peticion, respuesta.clone());
                    return respuesta;
                })
                .catch(() => cacheada);

            return cacheada || desdeRed;
        })
    );
});

function guardarEnCache(peticion, respuesta) {
    if (!respuesta || respuesta.status !== 200 || respuesta.type !== "basic") return;

    caches
        .open(CACHE)
        .then((cache) => cache.put(peticion, respuesta))
        .catch((error) => console.error("[sw] no se pudo cachear:", error));
}
