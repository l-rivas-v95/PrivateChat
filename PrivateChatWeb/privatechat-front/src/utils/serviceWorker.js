/**
 * Registra el service worker solo en la build de producción.
 *
 * En desarrollo estorbaría: se quedaría con versiones cacheadas de los módulos
 * y pelearía con el recargado en caliente de Vite. Para probar la PWA en local
 * hay que hacer `npm run build` y luego `npm run preview`.
 *
 * La ruta sale de import.meta.env.BASE_URL, que Vite rellena con el `base` del
 * vite.config. Así el scope del service worker coincide siempre con el sitio
 * donde vive la app, esté en la raíz o en /privatechat/.
 */
export function registrarServiceWorker() {
    if (!import.meta.env.PROD) return;
    if (!("serviceWorker" in navigator)) return;

    const base = import.meta.env.BASE_URL;

    window.addEventListener("load", () => {
        navigator.serviceWorker
            .register(`${base}sw.js`, { scope: base })
            .catch((error) => console.error("No se pudo registrar el service worker:", error));
    });
}
