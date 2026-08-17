import { useState } from "react";
import Icono from "../common/Icono";
import VisorMedia from "./VisorMedia";

/**
 * Los adjuntos se guardan como Blob en IndexedDB. Cada recarga del estado
 * devuelve un Blob nuevo, así que las URLs se cachean por id de mensaje:
 * si no, la imagen parpadearía cada vez que llega cualquier mensaje.
 */
const cacheUrls = new Map();

function urlDeAdjunto(mensaje) {
    if (!mensaje.mediaBlob) return null;

    const guardada = cacheUrls.get(mensaje.messageId);
    if (guardada) return guardada;

    const url = URL.createObjectURL(mensaje.mediaBlob);
    cacheUrls.set(mensaje.messageId, url);
    return url;
}

export function olvidarAdjunto(messageId) {
    const url = cacheUrls.get(messageId);
    if (!url) return;

    URL.revokeObjectURL(url);
    cacheUrls.delete(messageId);
}

function AdjuntoMensaje({ mensaje }) {
    const [visorAbierto, setVisorAbierto] = useState(false);

    const url = urlDeAdjunto(mensaje);
    if (!url) return null;

    const mime = mensaje.mimeType || "";
    const nombre = mensaje.fileName || "archivo";

    if (mime.startsWith("image/") || mime.startsWith("video/")) {
        const esVideo = mime.startsWith("video/");

        return (
            <>
                <button
                    type="button"
                    className={esVideo ? "adjunto-video" : "adjunto-imagen"}
                    onClick={() => setVisorAbierto(true)}
                    title="Pulsa para verlo"
                >
                    {esVideo ? (
                        <>
                            <video src={url} preload="metadata" muted />
                            <span className="adjunto-play">
                                <Icono nombre="enviar" tamano={20} />
                            </span>
                        </>
                    ) : (
                        <img src={url} alt={nombre} />
                    )}
                </button>

                {visorAbierto && (
                    <VisorMedia
                        url={url}
                        nombre={nombre}
                        mimeType={mime}
                        onCerrar={() => setVisorAbierto(false)}
                    />
                )}
            </>
        );
    }

    if (mime.startsWith("audio/")) {
        return <audio className="adjunto-audio" src={url} controls preload="metadata" />;
    }

    return (
        <a className="adjunto-fichero" href={url} download={nombre}>
            <Icono nombre="documento" tamano={26} />
            <span className="adjunto-fichero-nombre">{nombre}</span>
            <Icono nombre="descargar" tamano={18} />
        </a>
    );
}

export default AdjuntoMensaje;
