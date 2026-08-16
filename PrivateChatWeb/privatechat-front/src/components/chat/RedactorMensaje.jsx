import { useRef, useState } from "react";
import Icono from "../common/Icono";

function RedactorMensaje({ onEnviar, onEnviarFichero }) {
    const [texto, setTexto] = useState("");
    const [enviandoFichero, setEnviandoFichero] = useState(false);
    const areaRef = useRef(null);
    const ficheroRef = useRef(null);

    function ajustarAltura() {
        const area = areaRef.current;
        if (!area) return;

        area.style.height = "auto";
        area.style.height = `${Math.min(area.scrollHeight, 120)}px`;
    }

    async function enviar() {
        const limpio = texto.trim();
        if (!limpio) return;

        setTexto("");
        if (areaRef.current) areaRef.current.style.height = "auto";
        await onEnviar(limpio);
    }

    function alPulsarTecla(evento) {
        if (evento.key === "Enter" && !evento.shiftKey) {
            evento.preventDefault();
            enviar();
        }
    }

    async function alElegirFichero(evento) {
        const fichero = evento.target.files?.[0];
        evento.target.value = "";
        if (!fichero) return;

        setEnviandoFichero(true);
        await onEnviarFichero(fichero);
        setEnviandoFichero(false);
    }

    return (
        <footer className="redactor">
            <input
                ref={ficheroRef}
                type="file"
                hidden
                onChange={alElegirFichero}
            />

            <button
                type="button"
                className="redactor-accion"
                onClick={() => ficheroRef.current?.click()}
                disabled={enviandoFichero}
                title="Adjuntar archivo"
            >
                <Icono nombre="adjuntar" tamano={22} />
            </button>

            <textarea
                ref={areaRef}
                className="redactor-area"
                value={texto}
                rows={1}
                placeholder={enviandoFichero ? "Cifrando y subiendo archivo..." : "Escribe un mensaje"}
                onChange={(evento) => {
                    setTexto(evento.target.value);
                    ajustarAltura();
                }}
                onKeyDown={alPulsarTecla}
            />

            <button
                type="button"
                className="redactor-enviar"
                onClick={enviar}
                disabled={!texto.trim()}
                title="Enviar"
            >
                <Icono nombre="enviar" tamano={20} />
            </button>
        </footer>
    );
}

export default RedactorMensaje;
