import { useEffect } from "react";
import "./Modal.css";

function Modal({ abierto, onCerrar, children, ancho = 440 }) {
    useEffect(() => {
        if (!abierto) return;

        const alPulsarTecla = (evento) => {
            if (evento.key === "Escape") onCerrar();
        };

        document.addEventListener("keydown", alPulsarTecla);
        return () => document.removeEventListener("keydown", alPulsarTecla);
    }, [abierto, onCerrar]);

    if (!abierto) return null;

    return (
        <div className="modal-fondo" onClick={onCerrar}>
            <div
                className="modal-caja"
                style={{ maxWidth: ancho }}
                onClick={(evento) => evento.stopPropagation()}
            >
                {children}
            </div>
        </div>
    );
}

export default Modal;
