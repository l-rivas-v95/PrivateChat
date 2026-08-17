/**
 * Por defecto el navegador considera que IndexedDB y localStorage son
 * "best-effort": puede desalojarlos si le falta espacio, y Safari los borra a
 * los 7 días sin visitas. Con almacenamiento persistente concedido eso no pasa.
 *
 * Chrome y Firefox lo conceden solo si la web tiene señales de uso real:
 * instalada como PWA, visitas repetidas o permiso de notificaciones concedido.
 * Por eso conviene pedirlo, pero no depender de que digan que sí.
 */

export async function asegurarAlmacenamientoPersistente() {
    if (!navigator.storage?.persist) return "no-soportado";

    try {
        if (await navigator.storage.persisted()) return "concedido";

        return (await navigator.storage.persist()) ? "concedido" : "denegado";
    } catch (error) {
        console.error("No se pudo pedir almacenamiento persistente:", error);
        return "no-soportado";
    }
}

export async function consultarEspacioUsado() {
    if (!navigator.storage?.estimate) return null;

    try {
        const { usage, quota } = await navigator.storage.estimate();
        return { usado: usage || 0, disponible: quota || 0 };
    } catch {
        return null;
    }
}

export function formatearBytes(bytes) {
    if (!bytes) return "0 B";

    const unidades = ["B", "KB", "MB", "GB"];
    const indice = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), unidades.length - 1);
    const valor = bytes / 1024 ** indice;

    return `${valor.toFixed(valor >= 10 || indice === 0 ? 0 : 1)} ${unidades[indice]}`;
}
