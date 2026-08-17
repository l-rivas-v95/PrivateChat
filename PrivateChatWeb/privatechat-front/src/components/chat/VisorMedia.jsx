import { useEffect } from "react";
import { createPortal } from "react-dom";
import Icono from "../common/Icono";
import "./VisorMedia.css";

/**
 * Visor a pantalla completa para imágenes y vídeos.
 *
 * Va montado en un portal sobre `document.body` a propósito. Si se renderiza
 * donde está la burbuja, queda dentro del contexto de apilamiento que crea
 * `.ventana-chat-mensajes > *`, y los mensajes que vienen después en el DOM se
 * pintan por encima: se ve la imagen pero los clics se los llevan las burbujas
 * y no hay forma de cerrar.
 *
 * Antes, pulsar una imagen disparaba la descarga directamente, así que cada
 * toque guardaba otra copia. Ahora pulsar solo abre esto, y descargar es una
 * acción aparte dentro del visor.
 */
function VisorMedia({ url, nombre, mimeType, onCerrar }) {
    useEffect(() => {
        const alPulsar = (evento) => {
            if (evento.key === "Escape") onCerrar();
        };

        document.addEventListener("keydown", alPulsar);

        const desbordeAnterior = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        return () => {
            document.removeEventListener("keydown", alPulsar);
            document.body.style.overflow = desbordeAnterior;
        };
    }, [onCerrar]);

    const esVideo = (mimeType || "").startsWith("video/");

    // Cierra al pulsar en cualquier sitio menos en la propia imagen o vídeo,
    // que es lo que se espera de un visor.
    const noPropagar = (evento) => evento.stopPropagation();

    return createPortal(
        <div className="visor-media" onClick={onCerrar}>
            <header className="visor-media-cabecera">
                <button type="button" onClick={onCerrar} aria-label="Cerrar">
                    <Icono nombre="cerrar" tamano={22} />
                </button>

                <span className="visor-media-nombre">{nombre || "Archivo"}</span>

                <a
                    href={url}
                    download={nombre || "archivo"}
                    onClick={noPropagar}
                    title="Guardar en el dispositivo"
                    aria-label="Guardar en el dispositivo"
                >
                    <Icono nombre="descargar" tamano={21} />
                </a>
            </header>

            <div className="visor-media-cuerpo">
                {esVideo ? (
                    <video src={url} controls autoPlay playsInline onClick={noPropagar} />
                ) : (
                    <img src={url} alt={nombre || "Imagen"} onClick={noPropagar} />
                )}
            </div>

            <p className="visor-media-pie">Pulsa fuera de la imagen para cerrar</p>
        </div>,
        document.body
    );
}

export default VisorMedia;
