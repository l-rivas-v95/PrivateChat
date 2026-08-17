import { useRef, useState } from "react";
import { COLORES_APP, ESTADO_CONEXION_CONECTADO } from "../../config/constantes";
import { contactoQr } from "../../services/payloadService";
import { formatearBytes } from "../../utils/almacenamiento";
import Avatar from "../common/Avatar";
import CodigoQr from "../common/CodigoQr";
import Icono from "../common/Icono";
import "./PanelPerfil.css";

function PanelPerfil({
    abierto,
    userIdLocal,
    clavePublicaLocal,
    nombreLocal,
    avatarLocal,
    colorId,
    tema,
    estadoConexion,
    permisoNotificaciones,
    almacenamiento,
    instalacion,
    onCerrar,
    onCambiarNombre,
    onCambiarColor,
    onCambiarTema,
    onCambiarAvatar,
    onQuitarAvatar,
    onAlternarConexion,
    onPedirPermisoNotificaciones
}) {
    const ficheroRef = useRef(null);
    const [nombreBorrador, setNombreBorrador] = useState(nombreLocal);
    const [copiado, setCopiado] = useState(null);
    const [aviso, setAviso] = useState(null);

    if (!abierto) return null;

    const contenidoQr = clavePublicaLocal
        ? contactoQr(userIdLocal, nombreLocal, clavePublicaLocal)
        : null;

    async function copiar(texto, etiqueta) {
        try {
            await navigator.clipboard.writeText(texto);
            setCopiado(etiqueta);
            setTimeout(() => setCopiado(null), 1800);
        } catch (error) {
            console.error("No se pudo copiar:", error);
        }
    }

    async function alElegirAvatar(evento) {
        const fichero = evento.target.files?.[0];
        evento.target.value = "";
        if (!fichero) return;

        const resultado = await onCambiarAvatar(fichero);
        setAviso(resultado?.ok ? null : resultado?.error || "No se pudo actualizar el avatar");
    }

    return (
        <div className="panel-perfil">
            <header className="panel-perfil-cabecera">
                <button type="button" onClick={onCerrar} aria-label="Cerrar perfil">
                    <Icono nombre="atras" tamano={22} />
                </button>
                <h2>Perfil y ajustes</h2>
            </header>

            <div className="panel-perfil-cuerpo">
                <section className="panel-perfil-avatar">
                    <input ref={ficheroRef} type="file" accept="image/*" hidden onChange={alElegirAvatar} />

                    <Avatar
                        nombre={nombreLocal}
                        avatarBase64={avatarLocal}
                        tamano={132}
                        onClick={() => ficheroRef.current?.click()}
                    />

                    <div className="panel-perfil-avatar-acciones">
                        <button type="button" onClick={() => ficheroRef.current?.click()}>
                            <Icono nombre="camara" tamano={17} />
                            Cambiar foto
                        </button>
                        {avatarLocal && (
                            <button type="button" className="tenue" onClick={onQuitarAvatar}>
                                Quitar
                            </button>
                        )}
                    </div>

                    <p className="panel-perfil-nota">
                        La imagen se reduce a 96 px y se comprime antes de enviarse, porque el
                        servidor descarta los payloads grandes.
                    </p>
                </section>

                {aviso && <p className="panel-perfil-aviso">{aviso}</p>}

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Tu nombre</label>
                    <div className="panel-perfil-campo">
                        <input
                            value={nombreBorrador}
                            onChange={(evento) => setNombreBorrador(evento.target.value)}
                            onBlur={() => onCambiarNombre(nombreBorrador)}
                            onKeyDown={(evento) => {
                                if (evento.key === "Enter") evento.currentTarget.blur();
                            }}
                            maxLength={40}
                        />
                        <Icono nombre="lapiz" tamano={16} />
                    </div>
                    <p className="panel-perfil-nota">
                        Es el nombre que verán tus contactos al aceptarte.
                    </p>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Tu código QR</label>
                    <div className="panel-perfil-qr">
                        {contenidoQr ? (
                            <CodigoQr contenido={contenidoQr} />
                        ) : (
                            <p className="panel-perfil-nota">Generando claves...</p>
                        )}
                    </div>
                    <p className="panel-perfil-nota">
                        Contiene tu identificador, tu nombre y tu clave pública. Que lo escaneen
                        desde <strong>Nuevo chat</strong>. Si cuesta leerlo, púlsalo para verlo a
                        pantalla completa.
                    </p>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Identificador</label>
                    <button
                        type="button"
                        className="panel-perfil-copiable"
                        onClick={() => copiar(userIdLocal, "id")}
                    >
                        <span>{userIdLocal}</span>
                        <small>{copiado === "id" ? "¡Copiado!" : "Copiar"}</small>
                    </button>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Clave pública</label>
                    <button
                        type="button"
                        className="panel-perfil-copiable clave"
                        onClick={() => copiar(clavePublicaLocal, "clave")}
                        disabled={!clavePublicaLocal}
                    >
                        <span>{clavePublicaLocal || "Generando..."}</span>
                        <small>{copiado === "clave" ? "¡Copiado!" : "Copiar"}</small>
                    </button>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Color de acento</label>
                    <div className="panel-perfil-colores">
                        {COLORES_APP.map((color) => (
                            <button
                                type="button"
                                key={color.id}
                                className={`panel-perfil-color ${color.id === colorId ? "activo" : ""}`.trim()}
                                style={{ background: color.acento }}
                                onClick={() => onCambiarColor(color.id)}
                                title={color.etiqueta}
                            >
                                {color.id === colorId && <Icono nombre="check" tamano={18} />}
                            </button>
                        ))}
                    </div>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Apariencia</label>
                    <div className="panel-perfil-interruptores">
                        <button
                            type="button"
                            className={tema === "claro" ? "activo" : ""}
                            onClick={() => onCambiarTema("claro")}
                        >
                            <Icono nombre="sol" tamano={17} />
                            Claro
                        </button>
                        <button
                            type="button"
                            className={tema === "oscuro" ? "activo" : ""}
                            onClick={() => onCambiarTema("oscuro")}
                        >
                            <Icono nombre="luna" tamano={17} />
                            Oscuro
                        </button>
                    </div>
                </section>

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Conexión</label>
                    <button
                        type="button"
                        className={`panel-perfil-conexion ${estadoConexion === ESTADO_CONEXION_CONECTADO ? "conectado" : ""}`.trim()}
                        onClick={onAlternarConexion}
                    >
                        <Icono nombre="enchufe" tamano={18} />
                        {estadoConexion === ESTADO_CONEXION_CONECTADO
                            ? "Conectado · pulsa para desconectar"
                            : `${estadoConexion} · pulsa para conectar`}
                    </button>
                </section>

                {permisoNotificaciones === "default" && (
                    <section className="panel-perfil-bloque">
                        <label className="panel-perfil-etiqueta">Notificaciones</label>
                        <button
                            type="button"
                            className="panel-perfil-secundario"
                            onClick={onPedirPermisoNotificaciones}
                        >
                            Activar avisos del navegador
                        </button>
                        <p className="panel-perfil-nota">
                            Solo se muestran cuando la pestaña no está en primer plano. Concederlo
                            también ayuda a que el navegador conserve tus datos.
                        </p>
                    </section>
                )}

                <section className="panel-perfil-bloque">
                    <label className="panel-perfil-etiqueta">Aplicación</label>

                    {instalacion.estado === "disponible" && (
                        <button
                            type="button"
                            className="panel-perfil-secundario destacado"
                            onClick={instalacion.instalar}
                        >
                            <Icono nombre="descargar" tamano={18} />
                            Instalar en este dispositivo
                        </button>
                    )}

                    {instalacion.estado === "instalada" && (
                        <p className="panel-perfil-estado ok">
                            <Icono nombre="check" tamano={15} />
                            Instalada como aplicación
                        </p>
                    )}

                    {instalacion.estado === "manual" && (
                        <p className="panel-perfil-nota">
                            Para instalarla en iOS: botón Compartir → <strong>Añadir a pantalla de
                            inicio</strong>. Además de tener icono propio, evita que Safari borre
                            tus conversaciones a los 7 días sin abrirla.
                        </p>
                    )}

                    {instalacion.estado === "no-disponible" && (
                        <p className="panel-perfil-nota">
                            Este navegador no ofrece instalarla, o ya la tienes instalada.
                        </p>
                    )}

                    <p
                        className={`panel-perfil-estado ${almacenamiento.estado === "concedido" ? "ok" : "aviso"}`}
                    >
                        <Icono
                            nombre={almacenamiento.estado === "concedido" ? "candado" : "aviso"}
                            tamano={15}
                        />
                        {textoAlmacenamiento(almacenamiento)}
                    </p>

                    <p className="panel-perfil-nota">
                        Tu clave privada se guarda como clave no exportable del navegador: se puede
                        usar para descifrar, pero no hay forma de leerla ni de copiarla. Si borras
                        los datos del sitio, se pierde junto con las conversaciones.
                    </p>
                </section>
            </div>
        </div>
    );
}

function textoAlmacenamiento(almacenamiento) {
    const espacio =
        almacenamiento.usado !== undefined ? ` · ${formatearBytes(almacenamiento.usado)} usados` : "";

    switch (almacenamiento.estado) {
        case "concedido":
            return `Almacenamiento persistente activo${espacio}`;
        case "denegado":
            return `El navegador podría borrar los datos si le falta espacio${espacio}`;
        case "comprobando":
            return "Comprobando el almacenamiento...";
        default:
            return `Este navegador no permite fijar el almacenamiento${espacio}`;
    }
}

export default PanelPerfil;
