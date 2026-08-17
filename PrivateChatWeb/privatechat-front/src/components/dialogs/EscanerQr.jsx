import { useCallback, useEffect, useRef, useState } from "react";
import jsQR from "jsqr";
import Icono from "../common/Icono";
import "./EscanerQr.css";

/**
 * Lee el QR con la cámara del navegador.
 * Necesita contexto seguro (https o localhost), igual que WebCrypto.
 *
 * No se le imponen restricciones de resolución a propósito: pedirlas empeoraba
 * el enfoque de cerca, que es lo que hace falta para leer un QR de una pantalla.
 */
function EscanerQr({ onLeido, onCerrar }) {
    const videoRef = useRef(null);
    const lienzoRef = useRef(null);
    const streamRef = useRef(null);
    const animacionRef = useRef(null);
    const onLeidoRef = useRef(onLeido);
    const [error, setError] = useState(null);

    // Guardar el callback en una ref evita que la cámara se reinicie
    // cada vez que el componente padre vuelve a renderizar.
    useEffect(() => {
        onLeidoRef.current = onLeido;
    }, [onLeido]);

    const detener = useCallback(() => {
        if (animacionRef.current) {
            cancelAnimationFrame(animacionRef.current);
            animacionRef.current = null;
        }

        streamRef.current?.getTracks().forEach((pista) => pista.stop());
        streamRef.current = null;
    }, []);

    useEffect(() => {
        let cancelado = false;

        async function arrancar() {
            if (!navigator.mediaDevices?.getUserMedia) {
                setError("Este navegador no permite usar la cámara.");
                return;
            }

            try {
                const stream = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: "environment" }
                });

                if (cancelado) {
                    stream.getTracks().forEach((pista) => pista.stop());
                    return;
                }

                streamRef.current = stream;

                const video = videoRef.current;
                if (!video) return;

                video.srcObject = stream;
                video.setAttribute("playsinline", "true");
                await video.play();

                animacionRef.current = requestAnimationFrame(analizarFotograma);
            } catch (causa) {
                console.error("No se pudo abrir la cámara:", causa);
                setError(
                    "No se pudo abrir la cámara. Revisa los permisos y que la página se sirva por https o localhost."
                );
            }
        }

        function analizarFotograma() {
            const video = videoRef.current;
            const lienzo = lienzoRef.current;

            if (!video || !lienzo || video.readyState !== video.HAVE_ENOUGH_DATA) {
                animacionRef.current = requestAnimationFrame(analizarFotograma);
                return;
            }

            lienzo.width = video.videoWidth;
            lienzo.height = video.videoHeight;

            const contexto = lienzo.getContext("2d", { willReadFrequently: true });
            contexto.drawImage(video, 0, 0, lienzo.width, lienzo.height);

            const datos = contexto.getImageData(0, 0, lienzo.width, lienzo.height);
            const resultado = jsQR(datos.data, datos.width, datos.height, {
                inversionAttempts: "dontInvert"
            });

            if (resultado?.data) {
                detener();
                onLeidoRef.current(resultado.data);
                return;
            }

            animacionRef.current = requestAnimationFrame(analizarFotograma);
        }

        arrancar();

        return () => {
            cancelado = true;
            detener();
        };
    }, [detener]);

    return (
        <div className="escaner-qr">
            <div className="escaner-qr-cabecera">
                <h3>Escanear código QR</h3>
                <button type="button" className="escaner-qr-cerrar" onClick={onCerrar} aria-label="Cerrar">
                    <Icono nombre="cerrar" tamano={20} />
                </button>
            </div>

            {error ? (
                <p className="escaner-qr-error">{error}</p>
            ) : (
                <div className="escaner-qr-visor">
                    <video ref={videoRef} muted playsInline />
                    <div className="escaner-qr-marco" />
                </div>
            )}

            <p className="escaner-qr-pista">
                Apunta al QR que aparece en el perfil del otro dispositivo.
            </p>
            <canvas ref={lienzoRef} hidden />
        </div>
    );
}

export default EscanerQr;
