import { ESTADO_MENSAJE_ENTREGADO } from "../../config/constantes";
import { formatearHoraLista } from "../../utils/dateUtils";
import Avatar from "../common/Avatar";
import Icono from "../common/Icono";

function ElementoChat({ chat, activo, onSeleccionar }) {
    return (
        <button
            type="button"
            className={`elemento-chat ${activo ? "activo" : ""}`.trim()}
            onClick={() => onSeleccionar(chat.username)}
        >
            <Avatar nombre={chat.displayName} avatarBase64={chat.avatarBase64} tamano={48} />

            <div className="elemento-chat-cuerpo">
                <div className="elemento-chat-linea">
                    <span className="elemento-chat-nombre">{chat.displayName}</span>
                    <span className={`elemento-chat-hora ${chat.noLeidos > 0 ? "resaltada" : ""}`.trim()}>
                        {formatearHoraLista(chat.timestamp)}
                    </span>
                </div>

                <div className="elemento-chat-linea">
                    <span className="elemento-chat-resumen">
                        {chat.ultimoEsMio && (
                            <Icono
                                nombre={chat.ultimoEstado === ESTADO_MENSAJE_ENTREGADO ? "doblecheck" : "check"}
                                tamano={15}
                                className={`elemento-chat-check ${chat.ultimoEstado === ESTADO_MENSAJE_ENTREGADO ? "entregado" : ""}`.trim()}
                            />
                        )}
                        {chat.ultimoMensaje}
                    </span>

                    {chat.noLeidos > 0 && <span className="elemento-chat-badge">{chat.noLeidos}</span>}
                </div>
            </div>
        </button>
    );
}

export default ElementoChat;
