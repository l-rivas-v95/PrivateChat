import { useState } from "react";
import { parsearContactoQr } from "../../services/payloadService";
import Icono from "../common/Icono";
import EscanerQr from "./EscanerQr";
import Modal from "./Modal";
import "./NuevoChatDialog.css";

const PESTANA_QR = "qr";
const PESTANA_MANUAL = "manual";

function NuevoChatDialog({ abierto, onCerrar, onGuardar }) {
    const [pestana, setPestana] = useState(PESTANA_QR);
    const [escaneando, setEscaneando] = useState(false);
    const [userId, setUserId] = useState("");
    const [nombre, setNombre] = useState("");
    const [clavePublica, setClavePublica] = useState("");
    const [aviso, setAviso] = useState(null);

    function limpiar() {
        setPestana(PESTANA_QR);
        setEscaneando(false);
        setUserId("");
        setNombre("");
        setClavePublica("");
        setAviso(null);
    }

    function cerrar() {
        limpiar();
        onCerrar();
    }

    function alLeerQr(contenido) {
        setEscaneando(false);

        const contacto = parsearContactoQr(contenido);

        if (!contacto) {
            setAviso("Ese QR no es de PrivateChat.");
            return;
        }

        setUserId(contacto.userId);
        setNombre(contacto.displayName);
        setClavePublica(contacto.publicKey);
        setPestana(PESTANA_MANUAL);
        setAviso(null);
    }

    async function guardar(evento) {
        evento.preventDefault();

        const idLimpio = userId.trim();
        if (!idLimpio) {
            setAviso("Hace falta el identificador del contacto.");
            return;
        }

        await onGuardar(idLimpio, nombre.trim() || idLimpio, clavePublica.trim() || null);
        limpiar();
    }

    return (
        <Modal abierto={abierto} onCerrar={cerrar}>
            {escaneando ? (
                <EscanerQr onLeido={alLeerQr} onCerrar={() => setEscaneando(false)} />
            ) : (
                <div className="nuevo-chat">
                    <div className="nuevo-chat-cabecera">
                        <h3>Nuevo chat</h3>
                        <button type="button" className="nuevo-chat-cerrar" onClick={cerrar} aria-label="Cerrar">
                            <Icono nombre="cerrar" tamano={20} />
                        </button>
                    </div>

                    <div className="nuevo-chat-pestanas">
                        <button
                            type="button"
                            className={pestana === PESTANA_QR ? "activa" : ""}
                            onClick={() => setPestana(PESTANA_QR)}
                        >
                            Por QR
                        </button>
                        <button
                            type="button"
                            className={pestana === PESTANA_MANUAL ? "activa" : ""}
                            onClick={() => setPestana(PESTANA_MANUAL)}
                        >
                            Manual
                        </button>
                    </div>

                    {pestana === PESTANA_QR && (
                        <div className="nuevo-chat-qr">
                            <p>
                                Escanea el QR del perfil del otro dispositivo. Se rellenarán su
                                identificador, su nombre y su clave pública.
                            </p>
                            <button
                                type="button"
                                className="nuevo-chat-boton-principal"
                                onClick={() => setEscaneando(true)}
                            >
                                <Icono nombre="camara" tamano={20} />
                                Abrir cámara
                            </button>
                        </div>
                    )}

                    {pestana === PESTANA_MANUAL && (
                        <form className="nuevo-chat-formulario" onSubmit={guardar}>
                            <label>
                                Identificador
                                <input
                                    value={userId}
                                    onChange={(evento) => setUserId(evento.target.value)}
                                    placeholder="UUID del contacto"
                                    autoComplete="off"
                                />
                            </label>

                            <label>
                                Nombre visible
                                <input
                                    value={nombre}
                                    onChange={(evento) => setNombre(evento.target.value)}
                                    placeholder="Cómo quieres verlo en la lista"
                                    autoComplete="off"
                                />
                            </label>

                            <label>
                                Clave pública
                                <textarea
                                    value={clavePublica}
                                    onChange={(evento) => setClavePublica(evento.target.value)}
                                    placeholder="Base64 de la clave pública (opcional)"
                                    rows={3}
                                />
                            </label>

                            <p className="nuevo-chat-nota">
                                Sin clave pública los mensajes viajarían en claro. Si la dejas vacía,
                                se enviará una invitación y el cifrado se activará cuando el contacto
                                la acepte.
                            </p>

                            <button type="submit" className="nuevo-chat-boton-principal">
                                Guardar contacto
                            </button>
                        </form>
                    )}

                    {aviso && <p className="nuevo-chat-aviso">{aviso}</p>}
                </div>
            )}
        </Modal>
    );
}

export default NuevoChatDialog;
