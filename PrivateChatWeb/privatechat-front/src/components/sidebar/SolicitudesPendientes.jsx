import Avatar from "../common/Avatar";

function SolicitudesPendientes({ solicitudes, onAceptar, onRechazar }) {
    if (solicitudes.length === 0) return null;

    return (
        <section className="solicitudes">
            <h2 className="solicitudes-titulo">Solicitudes pendientes</h2>

            {solicitudes.map((contacto) => (
                <article className="solicitud" key={contacto.username}>
                    <Avatar
                        nombre={contacto.displayName}
                        avatarBase64={contacto.avatarBase64}
                        tamano={42}
                    />

                    <div className="solicitud-cuerpo">
                        <span className="solicitud-nombre">{contacto.displayName}</span>
                        <span className="solicitud-id">{contacto.username}</span>
                    </div>

                    <div className="solicitud-acciones">
                        <button
                            type="button"
                            className="solicitud-aceptar"
                            onClick={() => onAceptar(contacto.username)}
                        >
                            Aceptar
                        </button>
                        <button
                            type="button"
                            className="solicitud-rechazar"
                            onClick={() => onRechazar(contacto.username)}
                        >
                            Rechazar
                        </button>
                    </div>
                </article>
            ))}
        </section>
    );
}

export default SolicitudesPendientes;
