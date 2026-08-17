import { useState } from "react";
import { importarClavePublica } from "../../crypto/cryptoUtils";
import { parsearContactoQr } from "../../services/payloadService";
import Icono from "../common/Icono";
import EscanerQr from "./EscanerQr";
import Modal from "./Modal";
import "./NuevoChatDialog.css";

const PESTANA_QR = "qr";
const PESTANA_MANUAL = "manual";

/**
 * Para dar de alta un contacto solo hace falta su identificador.
 *
 * La clave pública nunca se pide: o llega en el QR, o la manda el propio
 * contacto al aceptar la invitación (`contact_accept`). Pedirla a mano
 * significaba copiar dos cadenas larguísimas en vez de una.
 */
function NuevoChatDialog({ abierto, userIdLocal, onCerrar, onGuardar }) {
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

    async function alLeerQr(contenido) {
        setEscaneando(false);

        const contacto = parsearContactoQr(contenido);

        if (!contacto) {
            setAviso("Ese QR no es de PrivateChat.");
            return;
        }

        if (contacto.userId === userIdLocal) {
            setAviso("Ese es tu propio QR. Escanea el del otro dispositivo.");
            return;
        }

        // Si la clave leída no se puede importar, mejor descartarla y tirar por
        // la invitación que guardar una clave inservible: con una clave rota el
        // cifrado falla y los mensajes acabarían saliendo en claro.
        let clave = contacto.publicKey;
        let mensaje = null;

        if (clave) {
            try {
                await importarClavePublica(clave);
            } catch (error) {
                console.error("La clave pública del QR no es válida:", error);
                clave = "";
                mensaje =
                    "El QR se ha leído, pero su clave pública no es válida. " +
                    "Se enviará una invitación y el cifrado se activará al aceptarla.";
            }
        }

        setUserId(contacto.userId);
        setNombre(contacto.displayName);
        setClavePublica(clave);
        setPestana(PESTANA_MANUAL);
        setAviso(mensaje);
    }

    async function guardar(evento) {
        evento.preventDefault();

        const idLimpio = userId.trim();
        if (!idLimpio) {
            setAviso("Hace falta el identificador del contacto.");
            return;
        }

        if (idLimpio === userIdLocal) {
            setAviso("Ese es tu propio identificador.");
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
                                Escanea el QR del perfil del otro dispositivo. Se rellenará todo
                                solo, incluida su clave pública.
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

                            {clavePublica ? (
                                <p className="nuevo-chat-clave-ok">
                                    <Icono nombre="candado" tamano={14} />
                                    Clave pública leída del QR. El cifrado estará activo desde el
                                    primer mensaje.
                                </p>
                            ) : (
                                <p className="nuevo-chat-nota">
                                    Se le enviará una invitación. En cuanto la acepte, las claves se
                                    intercambian solas y el cifrado se activa.
                                </p>
                            )}

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
