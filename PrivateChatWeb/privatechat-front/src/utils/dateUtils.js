const MS_POR_DIA = 24 * 60 * 60 * 1000;

export function formatearHora(timestamp) {
    return new Date(timestamp).toLocaleTimeString("es-ES", {
        hour: "2-digit",
        minute: "2-digit"
    });
}

/** Hora si es hoy, "Ayer" si es ayer, día de la semana esta semana, fecha corta si es más antiguo. */
export function formatearHoraLista(timestamp) {
    if (!timestamp) return "";

    const fecha = new Date(timestamp);
    const dias = diasDeDiferencia(fecha, new Date());

    if (dias === 0) return formatearHora(timestamp);
    if (dias === 1) return "Ayer";
    if (dias < 7) return fecha.toLocaleDateString("es-ES", { weekday: "long" });

    return fecha.toLocaleDateString("es-ES", { day: "2-digit", month: "2-digit", year: "2-digit" });
}

export function formatearSeparadorFecha(timestamp) {
    const fecha = new Date(timestamp);
    const dias = diasDeDiferencia(fecha, new Date());

    if (dias === 0) return "Hoy";
    if (dias === 1) return "Ayer";

    return fecha.toLocaleDateString("es-ES", { day: "numeric", month: "long", year: "numeric" });
}

export function esMismoDia(unTimestamp, otroTimestamp) {
    const uno = new Date(unTimestamp);
    const otro = new Date(otroTimestamp);

    return (
        uno.getFullYear() === otro.getFullYear() &&
        uno.getMonth() === otro.getMonth() &&
        uno.getDate() === otro.getDate()
    );
}

function diasDeDiferencia(fecha, referencia) {
    const inicioFecha = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
    const inicioReferencia = new Date(
        referencia.getFullYear(),
        referencia.getMonth(),
        referencia.getDate()
    );

    return Math.round((inicioReferencia - inicioFecha) / MS_POR_DIA);
}
