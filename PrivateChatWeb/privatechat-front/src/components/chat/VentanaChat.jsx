import { useEffect, useRef, useState } from "react";
import { esMismoDia, formatearSeparadorFecha } from "../../utils/dateUtils";
import Avatar from "../common/Avatar";
import Icono from "../common/Icono";
import BurbujaMensaje from "./BurbujaMensaje";
import RedactorMensaje from "./RedactorMensaje";
import "./VentanaChat.css";

function VentanaChat({
    contacto,
    mensajes,
    onVolver,
    onEnviar,
    onEnviarFichero,
    onEliminarMensaje,
    onRenombrar,
    onVaciar,
    onBorrar
}) {
    const finalRef = useRef(null);
    const [editandoNombre, setEditandoNombre] = useState(false);
    const [nombreBorrador, setNombreBorrador] = useState(contacto.displayName);
    const [menuAbierto, setMenuAbierto] = useState(false);
    const [aviso, setAviso] = useState(null);

    useEffect(() => {
        setNombreBorrador(contacto.displayName);
        setEditandoNombre(false);
        setMenuAbierto(false);
        setAviso(null);
    }, [contacto.username, contacto.displayName]);

    useEffect(() => {
        finalRef.current?.scrollIntoView({ block: "end" });
    }, [mensajes.length, contacto.username]);

    async function enviarFicheroConAviso(fichero) {
        const resultado = await onEnviarFichero(fichero);
        setAviso(resultado?.ok ? null : resultado?.error || "No se pudo enviar el archivo");
    }

    function confirmarNombre() {
        const limpio = nombreBorrador.trim();
        setEditandoNombre(false);
        if (limpio && limpio !== contacto.displayName) onRenombrar(limpio);
    }

    const sinClave = !contacto.publicKey;

    return (
        <section className="ventana-chat">
            <header className="ventana-chat-cabecera">
                <button type="button" className="ventana-chat-volver" onClick={onVolver} aria-label="Volver">
                    <Icono nombre="atras" tamano={22} />
                </button>

                <Avatar nombre={contacto.displayName} avatarBase64={contacto.avatarBase64} tamano={40} />

                <div className="ventana-chat-identidad">
                    {editandoNombre ? (
                        <input
                            className="ventana-chat-nombre-editable"
                            value={nombreBorrador}
                            autoFocus
                            onChange={(evento) => setNombreBorrador(evento.target.value)}
                            onBlur={confirmarNombre}
                            onKeyDown={(evento) => {
                                if (evento.key === "Enter") confirmarNombre();
                                if (evento.key === "Escape") {
                                    setNombreBorrador(contacto.displayName);
                                    setEditandoNombre(false);
                                }
                            }}
                        />
                    ) : (
                        <button
                            type="button"
                            className="ventana-chat-nombre"
                            onClick={() => setEditandoNombre(true)}
                            title="Editar nombre"
                        >
                            {contacto.displayName}
                            <Icono nombre="lapiz" tamano={13} />
                        </button>
                    )}

                    <span className={`ventana-chat-cifrado ${sinClave ? "sin-clave" : ""}`.trim()}>
                        <Icono nombre="candado" tamano={12} />
                        {sinClave ? "Sin clave pública todavía" : "Cifrado de extremo a extremo"}
                    </span>
                </div>

                <div className="ventana-chat-menu-contenedor">
                    <button
                        type="button"
                        className="ventana-chat-accion"
                        onClick={() => setMenuAbierto((abierto) => !abierto)}
                        aria-label="Opciones del chat"
                    >
                        <Icono nombre="opciones" tamano={20} />
                    </button>

                    {menuAbierto && (
                        <>
                            <div className="ventana-chat-menu-fondo" onClick={() => setMenuAbierto(false)} />
                            <div className="ventana-chat-menu">
                                <button
                                    type="button"
                                    onClick={() => {
                                        setMenuAbierto(false);
                                        onVaciar();
                                    }}
                                >
                                    <Icono nombre="escoba" tamano={17} />
                                    Vaciar conversación
                                </button>
                                <button
                                    type="button"
                                    className="peligro"
                                    onClick={() => {
                                        setMenuAbierto(false);
                                        onBorrar();
                                    }}
                                >
                                    <Icono nombre="papelera" tamano={17} />
                                    Eliminar chat y contacto
                                </button>
                            </div>
                        </>
                    )}
                </div>
            </header>

            <div className="ventana-chat-mensajes">
                {mensajes.length === 0 && (
                    <p className="ventana-chat-vacio">
                        Todavía no hay mensajes en esta conversación.
                    </p>
                )}

                {mensajes.map((mensaje, indice) => {
                    const anterior = mensajes[indice - 1];
                    const nuevoDia = !anterior || !esMismoDia(anterior.timestamp, mensaje.timestamp);

                    return (
                        <div key={mensaje.messageId}>
                            {nuevoDia && (
                                <div className="ventana-chat-separador">
                                    <span>{formatearSeparadorFecha(mensaje.timestamp)}</span>
                                </div>
                            )}
                            <BurbujaMensaje mensaje={mensaje} onEliminar={onEliminarMensaje} />
                        </div>
                    );
                })}

                <div ref={finalRef} />
            </div>

            {aviso && (
                <p className="ventana-chat-aviso">
                    <Icono nombre="aviso" tamano={15} />
                    {aviso}
                </p>
            )}

            <RedactorMensaje onEnviar={onEnviar} onEnviarFichero={enviarFicheroConAviso} />
        </section>
    );
}

export default VentanaChat;
