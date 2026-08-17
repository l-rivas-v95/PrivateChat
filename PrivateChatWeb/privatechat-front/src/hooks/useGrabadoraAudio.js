import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Grabación de notas de voz con MediaRecorder.
 *
 * El formato lo decide el navegador entre los que soporta. En Chrome y en
 * Android sale webm con Opus; en Safari, mp4. Se manda tal cual en el
 * `mimeType` del payload, así que el receptor sabe qué es.
 */

const FORMATOS = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/mp4",
    "audio/ogg;codecs=opus"
];

function elegirFormato() {
    if (typeof MediaRecorder === "undefined") return null;
    return FORMATOS.find((formato) => MediaRecorder.isTypeSupported?.(formato)) ?? "";
}

export function useGrabadoraAudio() {
    const [grabando, setGrabando] = useState(false);
    const [segundos, setSegundos] = useState(0);
    const [error, setError] = useState(null);

    const grabadoraRef = useRef(null);
    const trozosRef = useRef([]);
    const streamRef = useRef(null);
    const intervaloRef = useRef(null);
    const canceladoRef = useRef(false);

    const soltarTodo = useCallback(() => {
        clearInterval(intervaloRef.current);
        intervaloRef.current = null;

        streamRef.current?.getTracks().forEach((pista) => pista.stop());
        streamRef.current = null;
        grabadoraRef.current = null;
        trozosRef.current = [];
    }, []);

    // Si el componente se desmonta a media grabación hay que soltar el micro:
    // si no, el navegador se queda con el indicador de grabación encendido.
    useEffect(() => soltarTodo, [soltarTodo]);

    const iniciar = useCallback(async () => {
        if (grabando) return false;

        if (typeof MediaRecorder === "undefined" || !navigator.mediaDevices?.getUserMedia) {
            setError("Este navegador no permite grabar audio.");
            return false;
        }

        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const formato = elegirFormato();
            const grabadora = new MediaRecorder(stream, formato ? { mimeType: formato } : undefined);

            trozosRef.current = [];
            canceladoRef.current = false;

            grabadora.ondataavailable = (evento) => {
                if (evento.data?.size > 0) trozosRef.current.push(evento.data);
            };

            streamRef.current = stream;
            grabadoraRef.current = grabadora;

            grabadora.start();
            setGrabando(true);
            setSegundos(0);
            setError(null);

            intervaloRef.current = setInterval(() => setSegundos((valor) => valor + 1), 1000);
            return true;
        } catch (causa) {
            console.error("No se pudo grabar audio:", causa);
            setError("No se pudo acceder al micrófono. Revisa los permisos.");
            soltarTodo();
            return false;
        }
    }, [grabando, soltarTodo]);

    /** Para la grabación y devuelve el audio, o null si se canceló o salió vacío. */
    const detener = useCallback(() => {
        const grabadora = grabadoraRef.current;

        if (!grabadora || grabadora.state === "inactive") {
            soltarTodo();
            setGrabando(false);
            return Promise.resolve(null);
        }

        return new Promise((resolver) => {
            let resuelto = false;

            const terminar = (resultado) => {
                if (resuelto) return;
                resuelto = true;

                clearTimeout(reserva);
                soltarTodo();
                setGrabando(false);
                setSegundos(0);
                resolver(resultado);
            };

            // Red de seguridad: si por lo que sea `onstop` no llegara, la
            // interfaz no puede quedarse colgada en modo grabación.
            const reserva = setTimeout(() => {
                console.warn("La grabadora no respondió al parar. Se descarta.");
                terminar(null);
            }, 3000);

            grabadora.onstop = () => {
                const trozos = trozosRef.current;
                const tipo = grabadora.mimeType || "audio/webm";

                if (canceladoRef.current || trozos.length === 0) {
                    terminar(null);
                    return;
                }

                terminar(new Blob(trozos, { type: tipo }));
            };

            try {
                grabadora.stop();
            } catch (error) {
                console.error("No se pudo parar la grabadora:", error);
                terminar(null);
            }
        });
    }, [soltarTodo]);

    const cancelar = useCallback(() => {
        canceladoRef.current = true;
        return detener();
    }, [detener]);

    return { grabando, segundos, error, iniciar, detener, cancelar, descartarError: () => setError(null) };
}

export function formatearDuracion(segundos) {
    const minutos = Math.floor(segundos / 60);
    const resto = segundos % 60;
    return `${minutos}:${String(resto).padStart(2, "0")}`;
}
