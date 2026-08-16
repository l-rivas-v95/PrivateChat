import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    ESTADO_CONEXION_CONECTADO,
    ESTADO_CONEXION_DESCONECTADO,
    ESTADO_CONTACTO_ACEPTADO,
    ESTADO_CONTACTO_PENDIENTE,
    ESTADO_MENSAJE_ENTREGADO,
    ESTADO_MENSAJE_ENVIADO,
    ESTADO_MENSAJE_RECIBIDO,
    MAX_AVATAR_BASE64_CHARS,
    MAX_TAMANO_FICHERO_BYTES,
    NOMBRE_CONTACTO_DESCONOCIDO,
    TEXTO_ADJUNTO,
    TEXTO_ERROR_DESCIFRADO,
    TEXTO_SIN_MENSAJES,
    buscarColorApp
} from "../config/constantes";
import { construirPayloadSaliente, descifrarFichero, cifrarFichero, leerTextoDePayload } from "../crypto/chatCryptoService";
import { obtenerClavePublicaTexto } from "../crypto/keyPairManager";
import { crearChatSocket } from "../services/chatSocketService";
import { descargarFichero, subirFichero } from "../services/fileService";
import {
    TIPOS_PAYLOAD,
    aceptacionContacto,
    avatarPerfil,
    invitacionContacto,
    leerValor,
    mensajeMultimedia
} from "../services/payloadService";
import {
    actualizarResumenChat,
    asegurarChat,
    borrarChat,
    obtenerChats
} from "../storage/chatRepository";
import {
    borrarContacto,
    buscarContacto,
    guardarAvatarContacto,
    guardarContacto,
    obtenerContactos,
    renombrarContacto
} from "../storage/contactRepository";
import {
    actualizarEstadoEntrega,
    borrarMensaje,
    borrarMensajesDeContacto,
    guardarMensaje,
    marcarEntrantesComoLeidos,
    obtenerMensajes
} from "../storage/messageRepository";
import {
    crearUuid,
    guardarAvatarLocal,
    guardarColorLocal,
    guardarNombreLocal,
    obtenerAvatarLocal,
    obtenerColorLocal,
    obtenerNombreLocal,
    obtenerUserIdLocal
} from "../storage/perfilLocal";
import { base64ABytes, bytesABase64 } from "../utils/base64";
import { codificarAvatarABase64 } from "../utils/imageUtils";
import { useNotificaciones } from "./useNotificaciones";

/**
 * Equivalente a PrivateChatViewModel + ChatRealtimeManager de la app Android.
 *
 * Las funciones que reaccionan a payloads entrantes leen siempre de IndexedDB
 * en vez del estado de React, igual que el ViewModel leía de Room. Así no hay
 * closures obsoletas y el orden de llegada no importa.
 */
export function usePrivateChat() {
    const [userIdLocal] = useState(() => obtenerUserIdLocal());
    const [clavePublicaLocal, setClavePublicaLocal] = useState("");
    const [nombreLocal, setNombreLocal] = useState(() => obtenerNombreLocal());
    const [avatarLocal, setAvatarLocal] = useState(() => obtenerAvatarLocal());
    const [colorId, setColorId] = useState(() => obtenerColorLocal());

    const [estadoConexion, setEstadoConexion] = useState(ESTADO_CONEXION_DESCONECTADO);
    const [contactos, setContactos] = useState([]);
    const [chats, setChats] = useState([]);
    const [mensajes, setMensajes] = useState([]);
    const [cargando, setCargando] = useState(true);
    const [errorCripto, setErrorCripto] = useState(null);

    const socketRef = useRef(null);
    const perfilRef = useRef({ nombre: nombreLocal, clavePublica: "", avatar: avatarLocal });
    const { permiso: permisoNotificaciones, pedirPermiso, notificar } = useNotificaciones();

    useEffect(() => {
        perfilRef.current = {
            nombre: nombreLocal,
            clavePublica: clavePublicaLocal,
            avatar: avatarLocal
        };
    }, [nombreLocal, clavePublicaLocal, avatarLocal]);

    const recargarEstado = useCallback(async () => {
        const [contactosCargados, chatsCargados, mensajesCargados] = await Promise.all([
            obtenerContactos(),
            obtenerChats(),
            obtenerMensajes()
        ]);

        setContactos(contactosCargados);
        setChats(chatsCargados);
        setMensajes(mensajesCargados);
    }, []);

    const enviarPayload = useCallback((payload) => {
        return socketRef.current?.enviar(payload) || false;
    }, []);

    const enviarInvitacion = useCallback(
        (destino) => {
            const { nombre, clavePublica } = perfilRef.current;
            if (!clavePublica) return;
            enviarPayload(invitacionContacto(userIdLocal, destino, nombre, clavePublica));
        },
        [enviarPayload, userIdLocal]
    );

    const enviarAceptacion = useCallback(
        (destino) => {
            const { nombre, clavePublica } = perfilRef.current;
            if (!clavePublica) return;
            enviarPayload(aceptacionContacto(userIdLocal, destino, nombre, clavePublica));
        },
        [enviarPayload, userIdLocal]
    );

    const enviarAvatarAContacto = useCallback(
        (destino) => {
            const avatar = perfilRef.current.avatar;
            if (!avatar || avatar.length > MAX_AVATAR_BASE64_CHARS) return;
            enviarPayload(avatarPerfil(userIdLocal, destino, avatar, Date.now()));
        },
        [enviarPayload, userIdLocal]
    );

    const enviarAvatarAAceptados = useCallback(
        async (avatar) => {
            if (!avatar || avatar.length > MAX_AVATAR_BASE64_CHARS) return;

            const cargados = await obtenerContactos();
            cargados
                .filter((contacto) => contacto.username !== userIdLocal)
                .filter((contacto) => contacto.status === ESTADO_CONTACTO_ACEPTADO)
                .forEach((contacto) => {
                    enviarPayload(avatarPerfil(userIdLocal, contacto.username, avatar, Date.now()));
                });
        },
        [enviarPayload, userIdLocal]
    );

    const guardarMensajeLocal = useCallback(async (mensaje) => {
        await asegurarChat(mensaje.contactUsername);
        await guardarMensaje(mensaje);
        await actualizarResumenChat(mensaje.contactUsername, mensaje.text, mensaje.timestamp);
    }, []);

    // ---------------------------------------------------------------- entrantes

    const procesarAck = useCallback(
        async (payload) => {
            const messageId = leerValor(payload, "messageId");
            if (!messageId) return;

            await actualizarEstadoEntrega(messageId, ESTADO_MENSAJE_ENTREGADO);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const procesarInvitacion = useCallback(
        async (payload, from) => {
            const publicKey = leerValor(payload, "publicKey");
            const displayName = leerValor(payload, "displayName") || NOMBRE_CONTACTO_DESCONOCIDO;
            if (!from || !publicKey) return;

            await guardarContacto(from, displayName, publicKey, ESTADO_CONTACTO_PENDIENTE);
            await asegurarChat(from);
            await recargarEstado();
            notificar("Nueva solicitud de contacto", displayName);
        },
        [notificar, recargarEstado]
    );

    const procesarAceptacion = useCallback(
        async (payload, from) => {
            const publicKey = leerValor(payload, "publicKey");
            const displayName = leerValor(payload, "displayName") || NOMBRE_CONTACTO_DESCONOCIDO;
            if (!from || !publicKey) return;

            await guardarContacto(from, displayName, publicKey, ESTADO_CONTACTO_ACEPTADO);
            await asegurarChat(from);
            enviarAvatarAContacto(from);
            await recargarEstado();
        },
        [enviarAvatarAContacto, recargarEstado]
    );

    const procesarIntercambioClaves = useCallback(
        async (payload, from) => {
            const publicKey = leerValor(payload, "publicKey");
            if (!from || !publicKey) return;

            await guardarContacto(from, NOMBRE_CONTACTO_DESCONOCIDO, publicKey, ESTADO_CONTACTO_PENDIENTE);
            await asegurarChat(from);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const procesarAvatar = useCallback(
        async (payload, from) => {
            const avatarBase64 = leerValor(payload, "avatarBase64");
            const actualizadoEn = Number(leerValor(payload, "updatedAt")) || Date.now();
            if (!from || !avatarBase64 || avatarBase64.length > MAX_AVATAR_BASE64_CHARS) return;

            await guardarAvatarContacto(from, avatarBase64, actualizadoEn);
            await asegurarChat(from);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const procesarMensajeMultimedia = useCallback(
        async (payload, from) => {
            const to = leerValor(payload, "to");
            const messageId = leerValor(payload, "id") || crearUuid();
            const fileId = leerValor(payload, "fileId");
            const ivBase64 = leerValor(payload, "iv");
            const mimeType = leerValor(payload, "mimeType") || "application/octet-stream";
            if (!from || !fileId || !ivBase64) return;

            const contacto = await buscarContacto(from);
            if (!contacto?.publicKey) return;

            const bytesCifrados = await descargarFichero(fileId);
            if (!bytesCifrados) return;

            const bytesPlanos = await descifrarFichero(
                bytesCifrados,
                base64ABytes(ivBase64),
                contacto.publicKey
            );
            if (!bytesPlanos) return;

            await guardarMensajeLocal({
                messageId,
                contactUsername: from,
                from,
                to,
                text: TEXTO_ADJUNTO,
                timestamp: Date.now(),
                mine: false,
                status: ESTADO_MENSAJE_RECIBIDO,
                isRead: false,
                mediaBlob: new Blob([bytesPlanos], { type: mimeType }),
                mimeType,
                fileName: nombreFicheroPorDefecto(messageId, mimeType)
            });

            await recargarEstado();
            notificar(contacto.displayName, TEXTO_ADJUNTO);
        },
        [guardarMensajeLocal, notificar, recargarEstado]
    );

    const procesarMensaje = useCallback(
        async (payload, from) => {
            const to = leerValor(payload, "to");
            const messageId = leerValor(payload, "id") || crearUuid();
            if (!from) return;

            const cargados = await obtenerContactos();
            const texto = await leerTextoDePayload(payload, from, cargados);
            if (!texto || texto === TEXTO_ERROR_DESCIFRADO) {
                await recargarEstado();
                return;
            }

            const contacto = await guardarContacto(
                from,
                NOMBRE_CONTACTO_DESCONOCIDO,
                null,
                ESTADO_CONTACTO_PENDIENTE
            );

            await guardarMensajeLocal({
                messageId,
                contactUsername: from,
                from,
                to,
                text: texto,
                timestamp: Date.now(),
                mine: false,
                status: ESTADO_MENSAJE_RECIBIDO,
                isRead: false,
                mediaBlob: null,
                mimeType: null,
                fileName: null
            });

            await recargarEstado();
            notificar(contacto.displayName, texto);
        },
        [guardarMensajeLocal, notificar, recargarEstado]
    );

    const procesarPayloadEntrante = useCallback(
        (payload) => {
            const tipo = leerValor(payload, "type");
            const from = leerValor(payload, "from");

            const tarea = (() => {
                switch (tipo) {
                    case TIPOS_PAYLOAD.ACK:
                        return procesarAck(payload);
                    case TIPOS_PAYLOAD.INVITACION_CONTACTO:
                        return procesarInvitacion(payload, from);
                    case TIPOS_PAYLOAD.ACEPTACION_CONTACTO:
                        return procesarAceptacion(payload, from);
                    case TIPOS_PAYLOAD.INTERCAMBIO_CLAVES:
                        return procesarIntercambioClaves(payload, from);
                    case TIPOS_PAYLOAD.AVATAR_PERFIL:
                        return procesarAvatar(payload, from);
                    case TIPOS_PAYLOAD.MENSAJE_MULTIMEDIA:
                        return procesarMensajeMultimedia(payload, from);
                    default:
                        return procesarMensaje(payload, from);
                }
            })();

            Promise.resolve(tarea).catch((error) =>
                console.error("Error procesando payload entrante:", error)
            );
        },
        [
            procesarAceptacion,
            procesarAck,
            procesarAvatar,
            procesarIntercambioClaves,
            procesarInvitacion,
            procesarMensaje,
            procesarMensajeMultimedia
        ]
    );

    // -------------------------------------------------------------- ciclo de vida

    useEffect(() => {
        let vivo = true;

        obtenerClavePublicaTexto()
            .then((clave) => {
                if (vivo) setClavePublicaLocal(clave);
            })
            .catch((error) => {
                console.error("No se pudieron preparar las claves locales:", error);
                if (vivo) setErrorCripto(error.message);
            });

        recargarEstado()
            .catch((error) => console.error("No se pudo cargar la base local:", error))
            .finally(() => {
                if (vivo) setCargando(false);
            });

        return () => {
            vivo = false;
        };
    }, [recargarEstado]);

    useEffect(() => {
        const socket = crearChatSocket({
            onMensaje: procesarPayloadEntrante,
            onEstado: setEstadoConexion
        });

        socketRef.current = socket;
        socket.conectar(userIdLocal);

        const alVolver = () => {
            if (document.visibilityState === "visible") socket.reintentarAhora();
        };

        document.addEventListener("visibilitychange", alVolver);
        window.addEventListener("online", alVolver);

        return () => {
            document.removeEventListener("visibilitychange", alVolver);
            window.removeEventListener("online", alVolver);
            socket.desconectar();
            socketRef.current = null;
        };
        // El socket se crea una sola vez: procesarPayloadEntrante es estable
        // porque todas sus dependencias son callbacks memoizados.
    }, [procesarPayloadEntrante, userIdLocal]);

    // ------------------------------------------------------------------ acciones

    const conectar = useCallback(() => {
        socketRef.current?.conectar(userIdLocal);
    }, [userIdLocal]);

    const desconectar = useCallback(() => {
        socketRef.current?.desconectar();
    }, []);

    const alternarConexion = useCallback(() => {
        if (estadoConexion === ESTADO_CONEXION_CONECTADO) {
            desconectar();
        } else {
            conectar();
        }
    }, [conectar, desconectar, estadoConexion]);

    const guardarContactoManual = useCallback(
        async (contactId, contactName, publicKey) => {
            if (!contactId || contactId === userIdLocal) return;

            await guardarContacto(
                contactId,
                contactName || NOMBRE_CONTACTO_DESCONOCIDO,
                publicKey || null,
                ESTADO_CONTACTO_ACEPTADO
            );
            await asegurarChat(contactId);
            enviarInvitacion(contactId);
            enviarAvatarAContacto(contactId);
            await recargarEstado();
        },
        [enviarAvatarAContacto, enviarInvitacion, recargarEstado, userIdLocal]
    );

    const aceptarContactoPendiente = useCallback(
        async (username) => {
            const existente = await buscarContacto(username);
            if (!existente) return;

            await guardarContacto(
                existente.username,
                existente.displayName,
                existente.publicKey,
                ESTADO_CONTACTO_ACEPTADO
            );
            enviarAceptacion(username);
            enviarAvatarAContacto(username);
            await recargarEstado();
        },
        [enviarAceptacion, enviarAvatarAContacto, recargarEstado]
    );

    const rechazarContactoPendiente = useCallback(
        async (username) => {
            await borrarMensajesDeContacto(username);
            await borrarChat(username);
            await borrarContacto(username);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const enviarMensaje = useCallback(
        async (username, texto) => {
            const limpio = texto.trim();
            if (!limpio) return;

            const contacto = await buscarContacto(username);

            if (!contacto?.publicKey) {
                enviarInvitacion(username);
                await guardarMensajeLocal({
                    messageId: crearUuid(),
                    contactUsername: username,
                    from: userIdLocal,
                    to: username,
                    text: "Invitación enviada. Espera a que el contacto la acepte para poder enviar mensajes cifrados.",
                    timestamp: Date.now(),
                    mine: true,
                    status: ESTADO_MENSAJE_ENVIADO,
                    isRead: true,
                    mediaBlob: null,
                    mimeType: null,
                    fileName: null,
                    esDelSistema: true
                });
                await recargarEstado();
                return;
            }

            const messageId = crearUuid();
            const payload = await construirPayloadSaliente(
                messageId,
                userIdLocal,
                username,
                limpio,
                contacto
            );

            enviarPayload(payload);

            await guardarMensajeLocal({
                messageId,
                contactUsername: username,
                from: userIdLocal,
                to: username,
                text: limpio,
                timestamp: Date.now(),
                mine: true,
                status: ESTADO_MENSAJE_ENVIADO,
                isRead: true,
                mediaBlob: null,
                mimeType: null,
                fileName: null
            });

            await recargarEstado();
        },
        [enviarInvitacion, enviarPayload, guardarMensajeLocal, recargarEstado, userIdLocal]
    );

    const enviarFichero = useCallback(
        async (username, fichero) => {
            if (!fichero) return { ok: false, error: "No hay fichero" };
            if (fichero.size > MAX_TAMANO_FICHERO_BYTES) {
                return { ok: false, error: "El fichero supera los 10 MB que admite el servidor" };
            }

            const contacto = await buscarContacto(username);
            if (!contacto?.publicKey) {
                return { ok: false, error: "El contacto todavía no ha aceptado la invitación" };
            }

            const bytes = new Uint8Array(await fichero.arrayBuffer());
            const cifrado = await cifrarFichero(bytes, contacto.publicKey);
            if (!cifrado) return { ok: false, error: "No se pudo cifrar el fichero" };

            const fileId = await subirFichero(username, cifrado.bytesCifrados);
            if (!fileId) return { ok: false, error: "El servidor rechazó la subida" };

            const messageId = crearUuid();
            const mimeType = fichero.type || "application/octet-stream";

            enviarPayload(
                mensajeMultimedia(
                    messageId,
                    userIdLocal,
                    username,
                    fileId,
                    bytesABase64(cifrado.iv),
                    mimeType
                )
            );

            await guardarMensajeLocal({
                messageId,
                contactUsername: username,
                from: userIdLocal,
                to: username,
                text: TEXTO_ADJUNTO,
                timestamp: Date.now(),
                mine: true,
                status: ESTADO_MENSAJE_ENVIADO,
                isRead: true,
                mediaBlob: new Blob([bytes], { type: mimeType }),
                mimeType,
                fileName: fichero.name || nombreFicheroPorDefecto(messageId, mimeType)
            });

            await recargarEstado();
            return { ok: true };
        },
        [enviarPayload, guardarMensajeLocal, recargarEstado, userIdLocal]
    );

    const marcarChatComoLeido = useCallback(
        async (username) => {
            const cambiados = await marcarEntrantesComoLeidos(username);
            if (cambiados > 0) await recargarEstado();
        },
        [recargarEstado]
    );

    const vaciarConversacion = useCallback(
        async (username) => {
            await borrarMensajesDeContacto(username);
            await actualizarResumenChat(username, TEXTO_SIN_MENSAJES, Date.now());
            await recargarEstado();
        },
        [recargarEstado]
    );

    const borrarConversacion = useCallback(
        async (username) => {
            await borrarMensajesDeContacto(username);
            await borrarChat(username);
            await borrarContacto(username);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const eliminarMensaje = useCallback(
        async (messageId) => {
            await borrarMensaje(messageId);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const actualizarNombreContacto = useCallback(
        async (username, nuevoNombre) => {
            const limpio = nuevoNombre.trim();
            if (!limpio) return;

            await renombrarContacto(username, limpio);
            await recargarEstado();
        },
        [recargarEstado]
    );

    const actualizarNombreLocal = useCallback((nuevoNombre) => {
        const limpio = nuevoNombre.trim() || "User";
        guardarNombreLocal(limpio);
        setNombreLocal(limpio);
    }, []);

    const actualizarColor = useCallback((nuevoColorId) => {
        guardarColorLocal(nuevoColorId);
        setColorId(nuevoColorId);
    }, []);

    const actualizarAvatar = useCallback(
        async (fichero) => {
            const codificado = await codificarAvatarABase64(fichero);
            if (!codificado) return { ok: false, error: "No se pudo comprimir la imagen" };

            guardarAvatarLocal(codificado);
            setAvatarLocal(codificado);
            perfilRef.current = { ...perfilRef.current, avatar: codificado };
            await enviarAvatarAAceptados(codificado);
            return { ok: true };
        },
        [enviarAvatarAAceptados]
    );

    const quitarAvatar = useCallback(() => {
        guardarAvatarLocal(null);
        setAvatarLocal(null);
        perfilRef.current = { ...perfilRef.current, avatar: null };
    }, []);

    // ------------------------------------------------------------------ derivados

    const mensajesPorContacto = useMemo(() => {
        const mapa = new Map();

        mensajes.forEach((mensaje) => {
            const lista = mapa.get(mensaje.contactUsername) || [];
            lista.push(mensaje);
            mapa.set(mensaje.contactUsername, lista);
        });

        return mapa;
    }, [mensajes]);

    const noLeidosPorContacto = useMemo(() => {
        const mapa = {};

        mensajes.forEach((mensaje) => {
            if (mensaje.mine || mensaje.isRead) return;
            mapa[mensaje.contactUsername] = (mapa[mensaje.contactUsername] || 0) + 1;
        });

        return mapa;
    }, [mensajes]);

    const resumenChats = useMemo(() => {
        const chatsPorContacto = new Map(chats.map((chat) => [chat.contactUsername, chat]));

        return contactos
            .filter((contacto) => contacto.username !== userIdLocal)
            .filter((contacto) => contacto.status === ESTADO_CONTACTO_ACEPTADO)
            .map((contacto) => {
                const listaMensajes = mensajesPorContacto.get(contacto.username) || [];
                const ultimo = listaMensajes[listaMensajes.length - 1];
                const chat = chatsPorContacto.get(contacto.username);

                return {
                    username: contacto.username,
                    displayName: contacto.displayName,
                    avatarBase64: contacto.avatarBase64,
                    publicKey: contacto.publicKey,
                    ultimoMensaje: ultimo?.text || chat?.lastMessagePreview || TEXTO_SIN_MENSAJES,
                    ultimoEsMio: ultimo?.mine || false,
                    ultimoEstado: ultimo?.status || null,
                    timestamp: ultimo?.timestamp || chat?.updatedAt || contacto.lastSeenAt || contacto.createdAt,
                    noLeidos: noLeidosPorContacto[contacto.username] || 0
                };
            })
            .sort((a, b) => b.timestamp - a.timestamp);
    }, [chats, contactos, mensajesPorContacto, noLeidosPorContacto, userIdLocal]);

    const contactosPendientes = useMemo(
        () =>
            contactos
                .filter((contacto) => contacto.username !== userIdLocal)
                .filter((contacto) => contacto.status === ESTADO_CONTACTO_PENDIENTE)
                .sort((a, b) => (b.lastSeenAt || 0) - (a.lastSeenAt || 0)),
        [contactos, userIdLocal]
    );

    const color = useMemo(() => buscarColorApp(colorId), [colorId]);
    const conectado = estadoConexion === ESTADO_CONEXION_CONECTADO;

    return {
        userIdLocal,
        clavePublicaLocal,
        nombreLocal,
        avatarLocal,
        color,
        colorId,
        estadoConexion,
        conectado,
        cargando,
        errorCripto,
        contactos,
        contactosPendientes,
        resumenChats,
        mensajesPorContacto,
        permisoNotificaciones,
        pedirPermisoNotificaciones: pedirPermiso,
        conectar,
        desconectar,
        alternarConexion,
        guardarContactoManual,
        aceptarContactoPendiente,
        rechazarContactoPendiente,
        enviarMensaje,
        enviarFichero,
        marcarChatComoLeido,
        vaciarConversacion,
        borrarConversacion,
        eliminarMensaje,
        actualizarNombreContacto,
        actualizarNombreLocal,
        actualizarColor,
        actualizarAvatar,
        quitarAvatar
    };
}

function nombreFicheroPorDefecto(messageId, mimeType) {
    const extension = String(mimeType).split("/")[1]?.split(";")[0] || "bin";
    return `${messageId}.${extension}`;
}
