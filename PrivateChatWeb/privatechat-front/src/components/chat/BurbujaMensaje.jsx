import { useState } from "react";
import { ESTADO_MENSAJE_ENTREGADO } from "../../config/constantes";
import { formatearHora } from "../../utils/dateUtils";
import Icono from "../common/Icono";
import AdjuntoMensaje from "./AdjuntoMensaje";

function BurbujaMensaje({ mensaje, onEliminar }) {
    const [menuAbierto, setMenuAbierto] = useState(false);
    const entregado = mensaje.status === ESTADO_MENSAJE_ENTREGADO;

    if (mensaje.esDelSistema) {
        return (
            <div className="burbuja-sistema">
                <Icono nombre="aviso" tamano={14} />
                <span>{mensaje.text}</span>
            </div>
        );
    }

    return (
        <div
            className={`burbuja-fila ${mensaje.mine ? "propia" : "ajena"}`}
            onMouseLeave={() => setMenuAbierto(false)}
        >
            <div className={`burbuja ${mensaje.mediaBlob ? "con-adjunto" : ""}`.trim()}>
                <button
                    type="button"
                    className="burbuja-menu-boton"
                    onClick={() => setMenuAbierto((abierto) => !abierto)}
                    aria-label="Opciones del mensaje"
                >
                    <Icono nombre="papelera" tamano={15} />
                </button>

                {menuAbierto && (
                    <div className="burbuja-menu">
                        <button
                            type="button"
                            onClick={() => {
                                setMenuAbierto(false);
                                onEliminar(mensaje.messageId);
                            }}
                        >
                            Eliminar para mí
                        </button>
                    </div>
                )}

                <AdjuntoMensaje mensaje={mensaje} />

                {(!mensaje.mediaBlob || mensaje.text) && (
                    <p className="burbuja-texto">{mensaje.text}</p>
                )}

                <span className="burbuja-meta">
                    {formatearHora(mensaje.timestamp)}
                    {mensaje.mine && (
                        <Icono
                            nombre={entregado ? "doblecheck" : "check"}
                            tamano={15}
                            className={`burbuja-check ${entregado ? "entregado" : ""}`.trim()}
                        />
                    )}
                </span>
            </div>
        </div>
    );
}

export default BurbujaMensaje;
