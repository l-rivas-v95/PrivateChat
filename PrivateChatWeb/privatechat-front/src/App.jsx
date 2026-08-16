import { useCallback, useEffect, useMemo, useState } from "react";
import { olvidarAdjunto } from "./components/chat/AdjuntoMensaje";
import VentanaChat from "./components/chat/VentanaChat";
import Icono from "./components/common/Icono";
import NuevoChatDialog from "./components/dialogs/NuevoChatDialog";
import PanelPerfil from "./components/profile/PanelPerfil";
import ListaChats from "./components/sidebar/ListaChats";
import { hayCryptoDisponible } from "./crypto/cryptoUtils";
import { usePrivateChat } from "./hooks/usePrivateChat";
import "./App.css";

const CLAVE_TEMA = "private_chat_tema";

function App() {
    const chat = usePrivateChat();

    const [contactoActivo, setContactoActivo] = useState(null);
    const [perfilAbierto, setPerfilAbierto] = useState(false);
    const [nuevoChatAbierto, setNuevoChatAbierto] = useState(false);
    const [tema, setTema] = useState(() => localStorage.getItem(CLAVE_TEMA) || "oscuro");

    useEffect(() => {
        document.documentElement.dataset.tema = tema;
        localStorage.setItem(CLAVE_TEMA, tema);
    }, [tema]);

    useEffect(() => {
        document.documentElement.style.setProperty("--acento", chat.color.acento);
        document.documentElement.style.setProperty("--acento-oscuro", chat.color.principal);
    }, [chat.color]);

    const eliminarMensaje = useCallback(
        (messageId) => {
            olvidarAdjunto(messageId);
            return chat.eliminarMensaje(messageId);
        },
        [chat]
    );

    const contacto = useMemo(
        () => chat.contactos.find((elemento) => elemento.username === contactoActivo) || null,
        [chat.contactos, contactoActivo]
    );

    const mensajesActivos = useMemo(
        () => (contactoActivo ? chat.mensajesPorContacto.get(contactoActivo) || [] : []),
        [chat.mensajesPorContacto, contactoActivo]
    );

    // Si se borra el contacto abierto, se vuelve a la lista.
    useEffect(() => {
        if (contactoActivo && !contacto) setContactoActivo(null);
    }, [contacto, contactoActivo]);

    // Los mensajes que llegan con el chat ya abierto también cuentan como leídos.
    const marcarChatComoLeido = chat.marcarChatComoLeido;
    useEffect(() => {
        if (!contactoActivo) return;
        if (!mensajesActivos.some((mensaje) => !mensaje.mine && !mensaje.isRead)) return;

        marcarChatComoLeido(contactoActivo);
    }, [contactoActivo, marcarChatComoLeido, mensajesActivos]);

    if (!hayCryptoDisponible()) {
        return (
            <div className="app-error">
                <Icono nombre="candado" tamano={44} />
                <h1>Contexto no seguro</h1>
                <p>
                    PrivateChat necesita WebCrypto para generar las claves ECDH y cifrar los
                    mensajes, y el navegador solo lo expone en <code>https://</code> o en{" "}
                    <code>http://localhost</code>.
                </p>
                <p>Abre la app desde una de esas direcciones y vuelve a intentarlo.</p>
            </div>
        );
    }

    return (
        <div className={`app ${contactoActivo ? "con-chat" : ""}`.trim()}>
            <div className="app-panel-izquierdo">
                <ListaChats
                    chats={chat.resumenChats}
                    solicitudes={chat.contactosPendientes}
                    contactoActivo={contactoActivo}
                    nombreLocal={chat.nombreLocal}
                    avatarLocal={chat.avatarLocal}
                    estadoConexion={chat.estadoConexion}
                    onSeleccionarChat={setContactoActivo}
                    onAbrirPerfil={() => setPerfilAbierto(true)}
                    onNuevoChat={() => setNuevoChatAbierto(true)}
                    onAceptarSolicitud={chat.aceptarContactoPendiente}
                    onRechazarSolicitud={chat.rechazarContactoPendiente}
                />

                <PanelPerfil
                    abierto={perfilAbierto}
                    userIdLocal={chat.userIdLocal}
                    clavePublicaLocal={chat.clavePublicaLocal}
                    nombreLocal={chat.nombreLocal}
                    avatarLocal={chat.avatarLocal}
                    colorId={chat.colorId}
                    tema={tema}
                    estadoConexion={chat.estadoConexion}
                    permisoNotificaciones={chat.permisoNotificaciones}
                    onCerrar={() => setPerfilAbierto(false)}
                    onCambiarNombre={chat.actualizarNombreLocal}
                    onCambiarColor={chat.actualizarColor}
                    onCambiarTema={setTema}
                    onCambiarAvatar={chat.actualizarAvatar}
                    onQuitarAvatar={chat.quitarAvatar}
                    onAlternarConexion={chat.alternarConexion}
                    onPedirPermisoNotificaciones={chat.pedirPermisoNotificaciones}
                />
            </div>

            <div className="app-panel-derecho">
                {contacto ? (
                    <VentanaChat
                        contacto={contacto}
                        mensajes={mensajesActivos}
                        onVolver={() => setContactoActivo(null)}
                        onEnviar={(texto) => chat.enviarMensaje(contacto.username, texto)}
                        onEnviarFichero={(fichero) => chat.enviarFichero(contacto.username, fichero)}
                        onEliminarMensaje={eliminarMensaje}
                        onRenombrar={(nombre) => chat.actualizarNombreContacto(contacto.username, nombre)}
                        onVaciar={() => chat.vaciarConversacion(contacto.username)}
                        onBorrar={() => {
                            chat.borrarConversacion(contacto.username);
                            setContactoActivo(null);
                        }}
                    />
                ) : (
                    <div className="app-bienvenida">
                        <Icono nombre="chat" tamano={72} />
                        <h1>PrivateChat</h1>
                        <p>
                            Elige una conversación de la lista o añade un contacto escaneando su
                            código QR.
                        </p>
                        <p className="app-bienvenida-cifrado">
                            <Icono nombre="candado" tamano={13} />
                            Los mensajes se cifran en tu dispositivo con ECDH P-256 y AES-GCM
                        </p>
                    </div>
                )}
            </div>

            <NuevoChatDialog
                abierto={nuevoChatAbierto}
                onCerrar={() => setNuevoChatAbierto(false)}
                onGuardar={async (userId, nombre, clavePublica) => {
                    await chat.guardarContactoManual(userId, nombre, clavePublica);
                    setNuevoChatAbierto(false);
                    setContactoActivo(userId);
                }}
            />

            {chat.errorCripto && (
                <p className="app-banner-error">
                    <Icono nombre="aviso" tamano={16} />
                    {chat.errorCripto}
                </p>
            )}
        </div>
    );
}

export default App;
