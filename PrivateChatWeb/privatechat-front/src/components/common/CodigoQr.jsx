import { useEffect, useState } from "react";
import QRCode from "qrcode";
import "./CodigoQr.css";

function CodigoQr({ contenido, tamano = 240 }) {
    const [imagen, setImagen] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!contenido) {
            setImagen(null);
            return;
        }

        let vivo = true;

        QRCode.toDataURL(contenido, {
            width: tamano,
            margin: 1,
            errorCorrectionLevel: "M",
            color: { dark: "#000000", light: "#ffffff" }
        })
            .then((url) => {
                if (vivo) {
                    setImagen(url);
                    setError(null);
                }
            })
            .catch((causa) => {
                console.error("No se pudo generar el QR:", causa);
                if (vivo) setError("No se pudo generar el QR");
            });

        return () => {
            vivo = false;
        };
    }, [contenido, tamano]);

    if (error) {
        return <p className="codigo-qr-error">{error}</p>;
    }

    return (
        <div className="codigo-qr" style={{ width: tamano, height: tamano }}>
            {imagen && <img src={imagen} alt="Código QR de contacto" width={tamano} height={tamano} />}
        </div>
    );
}

export default CodigoQr;
