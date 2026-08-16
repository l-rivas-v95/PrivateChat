import { useCallback, useEffect, useState } from "react";

/**
 * Equivalente ligero de ChatNotificationHelper.
 * Solo avisa cuando la pestaña no está visible, para no duplicar la UI.
 */
export function useNotificaciones() {
    const [permiso, setPermiso] = useState(
        typeof Notification === "undefined" ? "unsupported" : Notification.permission
    );

    useEffect(() => {
        if (typeof Notification === "undefined") {
            setPermiso("unsupported");
        }
    }, []);

    const pedirPermiso = useCallback(async () => {
        if (typeof Notification === "undefined") return "unsupported";

        const resultado = await Notification.requestPermission();
        setPermiso(resultado);
        return resultado;
    }, []);

    const notificar = useCallback((titulo, cuerpo) => {
        if (typeof Notification === "undefined") return;
        if (Notification.permission !== "granted") return;
        if (document.visibilityState === "visible") return;

        try {
            const notificacion = new Notification(titulo, {
                body: cuerpo,
                icon: "/favicon.svg",
                tag: "privatechat"
            });

            notificacion.onclick = () => {
                window.focus();
                notificacion.close();
            };
        } catch (error) {
            console.error("No se pudo mostrar la notificación:", error);
        }
    }, []);

    return { permiso, pedirPermiso, notificar };
}
