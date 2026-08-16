import { useMemo, useState } from "react";
import {
    ESTADO_CONEXION_CONECTADO,
    ESTADO_CONEXION_CONECTANDO
} from "../../config/constantes";
import Avatar from "../common/Avatar";
import Icono from "../common/Icono";
import ElementoChat from "./ElementoChat";
import SolicitudesPendientes from "./SolicitudesPendientes";
import "./ListaChats.css";

function ListaChats({
    chats,
    solicitudes,
    contactoActivo,
    nombreLocal,
    avatarLocal,
    estadoConexion,
    onSeleccionarChat,
    onAbrirPerfil,
    onNuevoChat,
    onAceptarSolicitud,
    onRechazarSolicitud
}) {
    const [busqueda, setBusqueda] = useState("");

    const chatsFiltrados = useMemo(() => {
        const texto = busqueda.trim().toLowerCase();
        if (!texto) return chats;

        return chats.filter(
            (chat) =>
                chat.displayName.toLowerCase().includes(texto) ||
                chat.username.toLowerCase().includes(texto) ||
                chat.ultimoMensaje.toLowerCase().includes(texto)
        );
    }, [busqueda, chats]);

    const claseEstado = (() => {
        if (estadoConexion === ESTADO_CONEXION_CONECTADO) return "conectado";
        if (estadoConexion === ESTADO_CONEXION_CONECTANDO) return "conectando";
        return "desconectado";
    })();

    return (
        <aside className="lista-chats">
            <header className="lista-chats-cabecera">
                <Avatar
                    nombre={nombreLocal}
                    avatarBase64={avatarLocal}
                    tamano={40}
                    onClick={onAbrirPerfil}
                />

                <div className="lista-chats-titulo">
                    <span className="lista-chats-app">PrivateChat</span>
                    <span className={`lista-chats-estado ${claseEstado}`}>
                        <i className="lista-chats-punto" />
                        {estadoConexion}
                    </span>
                </div>

                <button
                    type="button"
                    className="lista-chats-accion"
                    onClick={onNuevoChat}
                    title="Nuevo chat"
                >
                    <Icono nombre="mas" tamano={22} />
                </button>

                <button
                    type="button"
                    className="lista-chats-accion"
                    onClick={onAbrirPerfil}
                    title="Perfil y ajustes"
                >
                    <Icono nombre="perfil" tamano={22} />
                </button>
            </header>

            <div className="lista-chats-buscador">
                <Icono nombre="buscar" tamano={18} />
                <input
                    value={busqueda}
                    onChange={(evento) => setBusqueda(evento.target.value)}
                    placeholder="Buscar un chat"
                    autoComplete="off"
                />
                {busqueda && (
                    <button type="button" onClick={() => setBusqueda("")} aria-label="Limpiar búsqueda">
                        <Icono nombre="cerrar" tamano={16} />
                    </button>
                )}
            </div>

            <div className="lista-chats-cuerpo">
                <SolicitudesPendientes
                    solicitudes={solicitudes}
                    onAceptar={onAceptarSolicitud}
                    onRechazar={onRechazarSolicitud}
                />

                {chatsFiltrados.length === 0 ? (
                    <div className="lista-chats-vacia">
                        <Icono nombre="chat" tamano={44} />
                        <p>{busqueda ? "Ningún chat coincide" : "Todavía no tienes chats"}</p>
                        {!busqueda && (
                            <button type="button" onClick={onNuevoChat}>
                                Añadir un contacto
                            </button>
                        )}
                    </div>
                ) : (
                    chatsFiltrados.map((chat) => (
                        <ElementoChat
                            key={chat.username}
                            chat={chat}
                            activo={chat.username === contactoActivo}
                            onSeleccionar={onSeleccionarChat}
                        />
                    ))
                )}
            </div>
        </aside>
    );
}

export default ListaChats;
