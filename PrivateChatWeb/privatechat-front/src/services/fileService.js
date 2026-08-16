import { buildHttpUrl } from "../config/api";

/**
 * Portado de ChatFileClient.kt.
 * El servidor borra el fichero en la primera descarga, así que el receptor
 * tiene que guardarse los bytes descifrados en local.
 */

export async function subirFichero(destinatario, bytesCifrados) {
    try {
        const formulario = new FormData();
        formulario.append("recipient", destinatario);
        formulario.append(
            "file",
            new Blob([bytesCifrados], { type: "application/octet-stream" }),
            "attachment"
        );

        const respuesta = await fetch(buildHttpUrl("/upload"), {
            method: "POST",
            body: formulario
        });

        if (!respuesta.ok) return null;

        const json = await respuesta.json();
        return json.fileId || null;
    } catch (error) {
        console.error("Error subiendo fichero:", error);
        return null;
    }
}

export async function descargarFichero(fileId) {
    try {
        const respuesta = await fetch(buildHttpUrl(`/file/${encodeURIComponent(fileId)}`));
        if (!respuesta.ok) return null;

        return new Uint8Array(await respuesta.arrayBuffer());
    } catch (error) {
        console.error("Error descargando fichero:", error);
        return null;
    }
}
