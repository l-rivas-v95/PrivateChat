import Icono from "../common/Icono";

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
    const url = urlDeAdjunto(mensaje);
    if (!url) return null;

    const mime = mensaje.mimeType || "";

    if (mime.startsWith("image/")) {
        return (
            <a
                className="adjunto-imagen"
                href={url}
                download={mensaje.fileName}
                target="_blank"
                rel="noreferrer"
            >
                <img src={url} alt={mensaje.fileName || "Imagen"} />
            </a>
        );
    }

    if (mime.startsWith("video/")) {
        return <video className="adjunto-video" src={url} controls preload="metadata" />;
    }

    if (mime.startsWith("audio/")) {
        return <audio className="adjunto-audio" src={url} controls preload="metadata" />;
    }

    return (
        <a className="adjunto-fichero" href={url} download={mensaje.fileName}>
            <Icono nombre="documento" tamano={26} />
            <span className="adjunto-fichero-nombre">{mensaje.fileName || "Archivo"}</span>
            <Icono nombre="descargar" tamano={18} />
        </a>
    );
}

export default AdjuntoMensaje;
