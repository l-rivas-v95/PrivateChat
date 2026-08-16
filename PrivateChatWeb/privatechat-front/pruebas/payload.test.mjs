/**
 * Comprueba que los payloads generados por el front tienen exactamente los
 * campos que leen ChatSocketHandler.java y ChatPayloads.kt.
 */

globalThis.window = { crypto: { getRandomValues: (a) => a } };

const P = await import("./.generado/payload-bundle.mjs");

let fallos = 0;

function comprobar(nombre, condicion, extra = "") {
    if (condicion) {
        console.log(`  ok   ${nombre}`);
    } else {
        console.log(`  FALLO ${nombre} ${extra}`);
        fallos += 1;
    }
}

function campos(json) {
    return Object.keys(JSON.parse(json)).sort().join(",");
}

console.log("\nFormato de los payloads\n");

comprobar(
    "contact_invite",
    campos(P.invitacionContacto("a", "b", "Luis", "KEY")) === "displayName,from,publicKey,to,type"
);

comprobar(
    "contact_accept",
    campos(P.aceptacionContacto("a", "b", "Pepe", "KEY")) === "displayName,from,publicKey,to,type"
);

comprobar(
    "message cifrado",
    campos(P.mensajeCifrado("id", "a", "b", "CT", "IV")) === "cipherText,from,id,iv,to,type"
);

comprobar("message plano", campos(P.mensajePlano("id", "a", "b", "hola")) === "from,id,text,to,type");

comprobar(
    "media_message",
    campos(P.mensajeMultimedia("id", "a", "b", "fid", "IV", "image/jpeg")) ===
        "fileId,from,id,iv,mimeType,to,type"
);

comprobar(
    "profile_avatar",
    campos(P.avatarPerfil("a", "b", "AAA", 1700000000000)) ===
        "avatarBase64,from,to,type,updatedAt"
);

comprobar(
    "updatedAt viaja como texto, igual que en Kotlin",
    typeof JSON.parse(P.avatarPerfil("a", "b", "AAA", 1700000000000)).updatedAt === "string"
);

comprobar(
    "todos los tipos coinciden con ChatPayloadTypes.kt",
    P.TIPOS_PAYLOAD.MENSAJE === "message" &&
        P.TIPOS_PAYLOAD.MENSAJE_MULTIMEDIA === "media_message" &&
        P.TIPOS_PAYLOAD.ACK === "ack" &&
        P.TIPOS_PAYLOAD.INTERCAMBIO_CLAVES === "key_exchange" &&
        P.TIPOS_PAYLOAD.INVITACION_CONTACTO === "contact_invite" &&
        P.TIPOS_PAYLOAD.ACEPTACION_CONTACTO === "contact_accept" &&
        P.TIPOS_PAYLOAD.AVATAR_PERFIL === "profile_avatar" &&
        P.TIPOS_PAYLOAD.CONTACTO_QR === "privatechat_contact"
);

comprobar(
    "el servidor puede leer el campo 'to' de cualquier payload",
    JSON.parse(P.mensajeCifrado("id", "a", "b", "CT", "IV")).to === "b"
);

comprobar(
    "el servidor puede generar el ACK: hacen falta 'from' e 'id'",
    (() => {
        const json = JSON.parse(P.mensajeCifrado("mi-id", "a", "b", "CT", "IV"));
        return json.from === "a" && json.id === "mi-id";
    })()
);

// QR
const qr = P.contactoQr("uuid-1", "Luis", "KEY");
const leido = P.parsearContactoQr(qr);

comprobar(
    "el QR generado aquí lo entiende parseContactQr() de Kotlin",
    campos(qr) === "displayName,publicKey,type,userId"
);

comprobar(
    "y el front sabe leer su propio QR",
    leido?.userId === "uuid-1" && leido.displayName === "Luis" && leido.publicKey === "KEY"
);

comprobar("un QR de otra app se rechaza", P.parsearContactoQr('{"type":"otra_cosa"}') === null);
comprobar("texto no JSON se rechaza", P.parsearContactoQr("hola") === null);

comprobar(
    "leerValor devuelve cadena vacía si falta el campo (como optString en Kotlin)",
    P.leerValor('{"a":1}', "b") === ""
);

// Límite de tamaño del servidor
const avatarGrande = P.avatarPerfil("a", "b", "x".repeat(8000), Date.now());
comprobar(
    "un avatar en el límite (8000 chars) cabe en los 16.000 del servidor",
    avatarGrande.length < 16000,
    `longitud=${avatarGrande.length}`
);

console.log(fallos === 0 ? "\nTodas las pruebas pasan.\n" : `\n${fallos} prueba(s) fallidas.\n`);
process.exit(fallos === 0 ? 0 : 1);
