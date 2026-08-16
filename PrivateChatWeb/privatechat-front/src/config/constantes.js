export const NOMBRE_CONTACTO_DESCONOCIDO = "Usuario desconocido";

export const ESTADO_MENSAJE_ENVIADO = "SENT";
export const ESTADO_MENSAJE_ENTREGADO = "DELIVERED";
export const ESTADO_MENSAJE_RECIBIDO = "RECEIVED";

export const ESTADO_CONTACTO_PENDIENTE = "PENDING";
export const ESTADO_CONTACTO_ACEPTADO = "ACCEPTED";
export const ESTADO_CONTACTO_BLOQUEADO = "BLOCKED";

export const ESTADO_CONEXION_CONECTADO = "Conectado";
export const ESTADO_CONEXION_CONECTANDO = "Conectando...";
export const ESTADO_CONEXION_DESCONECTADO = "Desconectado";

export const TEXTO_SIN_MENSAJES = "Sin mensajes todavía";
export const TEXTO_ADJUNTO = "Archivo adjunto";
export const TEXTO_ERROR_DESCIFRADO = "[No se pudo descifrar]";

/** Mismo límite que MAX_PAYLOAD_CHARS del servidor, con margen. */
export const MAX_AVATAR_BASE64_CHARS = 8000;

/** Mismo límite que app.files.max-size-bytes del servidor. */
export const MAX_TAMANO_FICHERO_BYTES = 10 * 1024 * 1024;

export const COLORES_APP = [
    { id: "GREEN", etiqueta: "Verde", principal: "#075e54", acento: "#25d366", burbuja: "#d9fdd3" },
    { id: "BLUE", etiqueta: "Azul", principal: "#0b5cad", acento: "#2f8fef", burbuja: "#d8eafe" },
    { id: "PURPLE", etiqueta: "Morado", principal: "#6750a4", acento: "#a58bef", burbuja: "#eaddff" }
];

export const COLOR_APP_POR_DEFECTO = COLORES_APP[0];

export function buscarColorApp(id) {
    return COLORES_APP.find((color) => color.id === id) || COLOR_APP_POR_DEFECTO;
}
