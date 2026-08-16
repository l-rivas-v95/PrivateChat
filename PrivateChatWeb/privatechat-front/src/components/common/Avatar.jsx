import { avatarADataUrl } from "../../utils/imageUtils";
import "./Avatar.css";

const COLORES_INICIAL = [
    "#f2617a", "#e8912d", "#2f8fef", "#7c6ff0",
    "#20b573", "#d4536a", "#00a8a8", "#a05fd6"
];

function Avatar({ nombre, avatarBase64, tamano = 48, onClick, className = "" }) {
    const imagen = avatarADataUrl(avatarBase64);
    const inicial = (nombre || "?").trim().charAt(0).toUpperCase();
    const colorFondo = COLORES_INICIAL[codigoDeNombre(nombre) % COLORES_INICIAL.length];

    const estilo = {
        width: tamano,
        height: tamano,
        fontSize: Math.round(tamano * 0.42),
        background: imagen ? "transparent" : colorFondo,
        cursor: onClick ? "pointer" : undefined
    };

    return (
        <div className={`avatar ${className}`.trim()} style={estilo} onClick={onClick}>
            {imagen ? <img src={imagen} alt={nombre || "Avatar"} /> : <span>{inicial}</span>}
        </div>
    );
}

function codigoDeNombre(nombre) {
    const texto = nombre || "?";
    let total = 0;

    for (let i = 0; i < texto.length; i += 1) {
        total += texto.charCodeAt(i);
    }

    return total;
}

export default Avatar;
