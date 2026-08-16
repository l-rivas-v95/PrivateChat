import { buildChatSocketUrl } from "../config/api";
import {
    ESTADO_CONEXION_CONECTADO,
    ESTADO_CONEXION_CONECTANDO,
    ESTADO_CONEXION_DESCONECTADO
} from "../config/constantes";

/**
 * Portado de ChatWebSocketClient.kt, con reconexión automática.
 * El navegador cierra el socket al dormir la pestaña, así que reconectar
 * es imprescindible aquí (en Android eso lo cubría el foreground service).
 */

const RETARDO_RECONEXION_MS = [1000, 2000, 4000, 8000, 15000];

export function crearChatSocket({ onMensaje, onEstado }) {
    let socket = null;
    let usuarioActual = null;
    let intentosReconexion = 0;
    let temporizadorReconexion = null;
    let cerradoPorElUsuario = false;

    function cambiarEstado(estado) {
        onEstado?.(estado);
    }

    function limpiarTemporizador() {
        if (temporizadorReconexion) {
            clearTimeout(temporizadorReconexion);
            temporizadorReconexion = null;
        }
    }

    function programarReconexion() {
        if (cerradoPorElUsuario || !usuarioActual) return;

        const retardo = RETARDO_RECONEXION_MS[
            Math.min(intentosReconexion, RETARDO_RECONEXION_MS.length - 1)
        ];
        intentosReconexion += 1;

        limpiarTemporizador();
        temporizadorReconexion = setTimeout(() => abrirSocket(), retardo);
    }

    function abrirSocket() {
        if (!usuarioActual) return;

        const anterior = socket;
        socket = null;
        if (anterior) {
            anterior.onopen = null;
            anterior.onmessage = null;
            anterior.onerror = null;
            anterior.onclose = null;
            if (anterior.readyState === WebSocket.OPEN || anterior.readyState === WebSocket.CONNECTING) {
                anterior.close(1000, "Cierre normal");
            }
        }

        cambiarEstado(ESTADO_CONEXION_CONECTANDO);

        const siguiente = new WebSocket(buildChatSocketUrl(usuarioActual));

        siguiente.onopen = () => {
            if (socket !== siguiente) return;
            intentosReconexion = 0;
            cambiarEstado(ESTADO_CONEXION_CONECTADO);
        };

        siguiente.onmessage = (evento) => {
            if (socket !== siguiente) return;
            try {
                onMensaje?.(evento.data);
            } catch (error) {
                console.error("Error procesando mensaje entrante:", error);
            }
        };

        siguiente.onerror = () => {
            if (socket !== siguiente) return;
            cambiarEstado(ESTADO_CONEXION_DESCONECTADO);
        };

        siguiente.onclose = () => {
            if (socket !== siguiente) return;
            socket = null;
            cambiarEstado(ESTADO_CONEXION_DESCONECTADO);
            programarReconexion();
        };

        socket = siguiente;
    }

    return {
        conectar(userId) {
            if (!userId) return;
            cerradoPorElUsuario = false;

            if (usuarioActual === userId && socket && socket.readyState === WebSocket.OPEN) {
                return;
            }

            usuarioActual = userId;
            intentosReconexion = 0;
            abrirSocket();
        },

        desconectar() {
            cerradoPorElUsuario = true;
            limpiarTemporizador();

            const actual = socket;
            socket = null;

            if (actual) {
                actual.onclose = null;
                actual.close(1000, "Cierre normal");
            }

            cambiarEstado(ESTADO_CONEXION_DESCONECTADO);
        },

        enviar(payload) {
            if (!socket || socket.readyState !== WebSocket.OPEN) return false;
            socket.send(payload);
            return true;
        },

        estaConectado() {
            return !!socket && socket.readyState === WebSocket.OPEN;
        },

        /** Fuerza un intento inmediato, por ejemplo al volver del segundo plano. */
        reintentarAhora() {
            if (cerradoPorElUsuario || !usuarioActual) return;
            if (socket && socket.readyState === WebSocket.OPEN) return;
            limpiarTemporizador();
            intentosReconexion = 0;
            abrirSocket();
        }
    };
}
