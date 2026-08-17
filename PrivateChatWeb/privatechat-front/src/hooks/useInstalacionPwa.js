import { useCallback, useEffect, useState } from "react";

/**
 * Gestiona la instalación de la app en el dispositivo.
 *
 * Chrome y Edge disparan `beforeinstallprompt` cuando la web cumple los
 * requisitos de PWA; lo guardamos para poder lanzar el diálogo desde un botón
 * nuestro en vez de esperar al del navegador.
 *
 * Safari e iOS no implementan ese evento: allí hay que instalarla a mano desde
 * Compartir → Añadir a pantalla de inicio, así que se devuelve `manual` para
 * poder explicárselo al usuario.
 */
export function useInstalacionPwa() {
    const [evento, setEvento] = useState(null);
    const [instalada, setInstalada] = useState(() => estaEnModoApp());

    useEffect(() => {
        const alPoderInstalar = (evt) => {
            evt.preventDefault();
            setEvento(evt);
        };

        const alInstalar = () => {
            setEvento(null);
            setInstalada(true);
        };

        window.addEventListener("beforeinstallprompt", alPoderInstalar);
        window.addEventListener("appinstalled", alInstalar);

        return () => {
            window.removeEventListener("beforeinstallprompt", alPoderInstalar);
            window.removeEventListener("appinstalled", alInstalar);
        };
    }, []);

    const instalar = useCallback(async () => {
        if (!evento) return false;

        evento.prompt();
        const { outcome } = await evento.userChoice;
        setEvento(null);

        return outcome === "accepted";
    }, [evento]);

    const estado = (() => {
        if (instalada) return "instalada";
        if (evento) return "disponible";
        if (esIos()) return "manual";
        return "no-disponible";
    })();

    return { estado, instalar };
}

function estaEnModoApp() {
    return (
        window.matchMedia?.("(display-mode: standalone)").matches ||
        window.navigator.standalone === true
    );
}

function esIos() {
    return /iphone|ipad|ipod/i.test(window.navigator.userAgent);
}
