import { useEffect, useRef, useState } from "react";
import { formatearDuracion, useGrabadoraAudio } from "../../hooks/useGrabadoraAudio";
import Icono from "../common/Icono";

const OPCIONES_ADJUNTO = [
    { id: "galeria", etiqueta: "Galería", icono: "galeria", accept: "image/*,video/*" },
    { id: "camara", etiqueta: "Cámara", icono: "camara", accept: "image/*", capture: "environment" },
    { id: "documento", etiqueta: "Documento", icono: "documento", accept: undefined }
];

function extensionDeAudio(mimeType) {
    if (mimeType.includes("mp4")) return "m4a";
    if (mimeType.includes("ogg")) return "ogg";
    return "webm";
}

function RedactorMensaje({ onEnviar, onEnviarFichero }) {
    const [texto, setTexto] = useState("");
    const [enviandoFichero, setEnviandoFichero] = useState(false);
    const [menuAbierto, setMenuAbierto] = useState(false);

    const areaRef = useRef(null);
    const entradasRef = useRef({});
    const grabadora = useGrabadoraAudio();

    // Al empezar a grabar se cierra el menú, para que no queden los dos abiertos.
    useEffect(() => {
        if (grabadora.grabando) setMenuAbierto(false);
    }, [grabadora.grabando]);

    // Escape descarta la grabación, que es lo que espera cualquiera.
    const cancelarGrabacion = grabadora.cancelar;
    useEffect(() => {
        if (!grabadora.grabando) return;

        const alPulsar = (evento) => {
            if (evento.key === "Escape") cancelarGrabacion();
        };

        document.addEventListener("keydown", alPulsar);
        return () => document.removeEventListener("keydown", alPulsar);
    }, [cancelarGrabacion, grabadora.grabando]);

    function ajustarAltura() {
        const area = areaRef.current;
        if (!area) return;

        area.style.height = "auto";
        area.style.height = `${Math.min(area.scrollHeight, 120)}px`;
    }

    async function enviarTexto() {
        const limpio = texto.trim();
        if (!limpio) return;

        setTexto("");
        if (areaRef.current) areaRef.current.style.height = "auto";
        await onEnviar(limpio);
    }

    function alPulsarTecla(evento) {
        if (evento.key === "Enter" && !evento.shiftKey) {
            evento.preventDefault();
            enviarTexto();
        }
    }

    async function alElegirFichero(evento) {
        const fichero = evento.target.files?.[0];
        evento.target.value = "";
        setMenuAbierto(false);
        if (!fichero) return;

        setEnviandoFichero(true);
        await onEnviarFichero(fichero);
        setEnviandoFichero(false);
    }

    async function enviarAudio() {
        const audio = await grabadora.detener();
        if (!audio) return;

        const nombre = `audio-${Date.now()}.${extensionDeAudio(audio.type)}`;
        const fichero = new File([audio], nombre, { type: audio.type });

        setEnviandoFichero(true);
        await onEnviarFichero(fichero);
        setEnviandoFichero(false);
    }

    if (grabadora.grabando) {
        return (
            <footer className="redactor redactor-grabando">
                <button
                    type="button"
                    className="redactor-cancelar"
                    onClick={() => cancelarGrabacion()}
                    title="Descartar la grabación"
                >
                    <Icono nombre="papelera" tamano={20} />
                    Cancelar
                </button>

                <div className="redactor-grabacion">
                    <span className="redactor-punto" />
                    <span className="redactor-tiempo">{formatearDuracion(grabadora.segundos)}</span>
                </div>

                <button
                    type="button"
                    className="redactor-enviar"
                    onClick={enviarAudio}
                    title="Enviar nota de voz"
                >
                    <Icono nombre="enviar" tamano={20} />
                </button>
            </footer>
        );
    }

    const hayTexto = texto.trim().length > 0;

    return (
        <footer className="redactor">
            {OPCIONES_ADJUNTO.map((opcion) => (
                <input
                    key={opcion.id}
                    ref={(elemento) => {
                        entradasRef.current[opcion.id] = elemento;
                    }}
                    type="file"
                    hidden
                    accept={opcion.accept}
                    capture={opcion.capture}
                    onChange={alElegirFichero}
                />
            ))}

            <div className="redactor-adjuntar">
                {menuAbierto && (
                    <>
                        <div className="redactor-menu-fondo" onClick={() => setMenuAbierto(false)} />
                        <div className="redactor-menu">
                            {OPCIONES_ADJUNTO.map((opcion) => (
                                <button
                                    key={opcion.id}
                                    type="button"
                                    onClick={() => entradasRef.current[opcion.id]?.click()}
                                >
                                    <Icono nombre={opcion.icono} tamano={19} />
                                    {opcion.etiqueta}
                                </button>
                            ))}
                        </div>
                    </>
                )}

                <button
                    type="button"
                    className={`redactor-accion ${menuAbierto ? "activa" : ""}`.trim()}
                    onClick={() => setMenuAbierto((abierto) => !abierto)}
                    disabled={enviandoFichero}
                    title="Adjuntar"
                >
                    <Icono nombre="adjuntar" tamano={22} />
                </button>
            </div>

            <textarea
                ref={areaRef}
                className="redactor-area"
                value={texto}
                rows={1}
                placeholder={enviandoFichero ? "Cifrando y subiendo…" : "Escribe un mensaje"}
                disabled={enviandoFichero}
                onChange={(evento) => {
                    setTexto(evento.target.value);
                    ajustarAltura();
                }}
                onKeyDown={alPulsarTecla}
            />

            {hayTexto ? (
                <button type="button" className="redactor-enviar" onClick={enviarTexto} title="Enviar">
                    <Icono nombre="enviar" tamano={20} />
                </button>
            ) : (
                <button
                    type="button"
                    className="redactor-enviar redactor-microfono"
                    onClick={grabadora.iniciar}
                    disabled={enviandoFichero}
                    title="Grabar nota de voz"
                >
                    <Icono nombre="microfono" tamano={21} />
                </button>
            )}

            {grabadora.error && (
                <p className="redactor-error" onClick={grabadora.descartarError}>
                    {grabadora.error}
                </p>
            )}
        </footer>
    );
}

export default RedactorMensaje;
