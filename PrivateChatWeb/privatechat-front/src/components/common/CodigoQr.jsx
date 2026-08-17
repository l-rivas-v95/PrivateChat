import { useEffect, useState } from "react";
import QRCode from "qrcode";
import Icono from "./Icono";
import "./CodigoQr.css";

/**
 * El contenido del QR (id + nombre + clave pública) ronda los 240 caracteres,
 * lo que da un QR de versión 11: 61x61 módulos. A esa densidad hay dos cosas
 * que no se pueden descuidar si se va a escanear desde la pantalla:
 *
 * - `margin: 4`. La norma exige una zona de silencio de 4 módulos alrededor.
 *   Con menos, muchos escáneres no llegan a detectar el código.
 * - `scale` en vez de `width`. Fijando los píxeles por módulo, la imagen sale
 *   con los módulos exactos y sin interpolar. Si se genera a un ancho fijo, el
 *   navegador reescala y los bordes se vuelven grises, que es justo lo que
 *   confunde a la cámara.
 */
const ESCALA_NORMAL = 4;
const ESCALA_AMPLIADA = 10;
const MARGEN_MODULOS = 4;

function CodigoQr({ contenido, ampliable = true }) {
    const [normal, setNormal] = useState(null);
    const [ampliada, setAmpliada] = useState(null);
    const [abierto, setAbierto] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!contenido) {
            setNormal(null);
            setAmpliada(null);
            return;
        }

        let vivo = true;
        const opciones = (escala) => ({
            scale: escala,
            margin: MARGEN_MODULOS,
            errorCorrectionLevel: "M",
            color: { dark: "#000000", light: "#ffffff" }
        });

        Promise.all([
            QRCode.toDataURL(contenido, opciones(ESCALA_NORMAL)),
            QRCode.toDataURL(contenido, opciones(ESCALA_AMPLIADA))
        ])
            .then(([chico, grande]) => {
                if (!vivo) return;
                setNormal(chico);
                setAmpliada(grande);
                setError(null);
            })
            .catch((causa) => {
                console.error("No se pudo generar el QR:", causa);
                if (vivo) setError("No se pudo generar el QR");
            });

        return () => {
            vivo = false;
        };
    }, [contenido]);

    useEffect(() => {
        if (!abierto) return;

        const alPulsar = (evento) => {
            if (evento.key === "Escape") setAbierto(false);
        };

        document.addEventListener("keydown", alPulsar);
        return () => document.removeEventListener("keydown", alPulsar);
    }, [abierto]);

    if (error) {
        return <p className="codigo-qr-error">{error}</p>;
    }

    if (!normal) {
        return <div className="codigo-qr codigo-qr-vacio" />;
    }

    return (
        <>
            <button
                type="button"
                className="codigo-qr"
                onClick={() => ampliable && setAbierto(true)}
                title={ampliable ? "Pulsa para ampliarlo" : undefined}
            >
                <img src={normal} alt="Código QR de contacto" />
                {ampliable && (
                    <span className="codigo-qr-pista">
                        <Icono nombre="buscar" tamano={13} />
                        Ampliar
                    </span>
                )}
            </button>

            {abierto && (
                <div className="codigo-qr-ampliado" onClick={() => setAbierto(false)}>
                    <img src={ampliada} alt="Código QR de contacto ampliado" />
                    <p>Escanéalo desde el otro dispositivo. Pulsa para cerrar.</p>
                </div>
            )}
        </>
    );
}

export default CodigoQr;
