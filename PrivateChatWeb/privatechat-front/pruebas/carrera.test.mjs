/**
 * Reproduce la carrera que borraba la clave pública: contact_accept y
 * profile_avatar llegando a la vez sobre el mismo contacto.
 */
import { webcrypto } from "node:crypto";

// IndexedDB falso que respeta lo esencial: las transacciones de escritura
// sobre el mismo almacén se serializan, y cada una ve lo ya escrito.
class Almacen {
    constructor() { this.datos = new Map(); this.cola = Promise.resolve(); }

    // Simula el patrón MALO: leer en una transaccion, escribir en otra,
    // con una pausa en medio (que es lo que pasa de verdad).
    async malo(clave, mutador) {
        const actual = this.datos.get(clave);
        await new Promise((r) => setTimeout(r, 5));
        this.datos.set(clave, mutador(actual));
    }

    // Simula el patrón BUENO: todo dentro de una transacción serializada.
    bueno(clave, mutador) {
        this.cola = this.cola.then(async () => {
            const actual = this.datos.get(clave);
            await new Promise((r) => setTimeout(r, 5));
            const siguiente = mutador(actual);
            if (siguiente) this.datos.set(clave, siguiente);
        });
        return this.cola;
    }
}

const guardarContacto = (existente, publicKey) => ({
    username: "luis",
    displayName: "Luis",
    publicKey: publicKey || existente?.publicKey || null,
    avatarBase64: existente?.avatarBase64 || null,
    status: "ACCEPTED"
});

const guardarAvatar = (existente, avatar) => ({
    username: "luis",
    displayName: existente?.displayName || "Usuario desconocido",
    publicKey: existente?.publicKey || null,
    avatarBase64: avatar,
    status: existente?.status || "PENDING"
});

let fallos = 0;
function comprobar(nombre, cond, extra = "") {
    console.log((cond ? "  ok   " : "  FALLO ") + nombre + (cond ? "" : " " + extra));
    if (!cond) fallos++;
}

console.log("\nCarrera contact_accept + profile_avatar\n");

// Antes: los dos handlers concurrentes, cada uno con su transaccion
const antes = new Almacen();
await Promise.all([
    antes.malo("luis", (e) => guardarContacto(e, "CLAVE-PUBLICA")),
    antes.malo("luis", (e) => guardarAvatar(e, "AVATAR"))
]);
const r1 = antes.datos.get("luis");
comprobar(
    "el patrón antiguo PIERDE la clave (esto era el bug)",
    r1.publicKey === null && r1.avatarBase64 === "AVATAR",
    `-> clave=${r1.publicKey} avatar=${r1.avatarBase64}`
);

// Ahora: escritura atomica serializada
const ahora = new Almacen();
await Promise.all([
    ahora.bueno("luis", (e) => guardarContacto(e, "CLAVE-PUBLICA")),
    ahora.bueno("luis", (e) => guardarAvatar(e, "AVATAR"))
]);
const r2 = ahora.datos.get("luis");
comprobar(
    "el patrón atómico conserva clave Y avatar",
    r2.publicKey === "CLAVE-PUBLICA" && r2.avatarBase64 === "AVATAR",
    `-> clave=${r2.publicKey} avatar=${r2.avatarBase64}`
);

// Y en el orden inverso tambien
const inverso = new Almacen();
await Promise.all([
    inverso.bueno("luis", (e) => guardarAvatar(e, "AVATAR")),
    inverso.bueno("luis", (e) => guardarContacto(e, "CLAVE-PUBLICA"))
]);
const r3 = inverso.datos.get("luis");
comprobar(
    "y da igual cuál llegue primero",
    r3.publicKey === "CLAVE-PUBLICA" && r3.avatarBase64 === "AVATAR",
    `-> clave=${r3.publicKey} avatar=${r3.avatarBase64}`
);

// El mensaje entrante no debe degradar un contacto aceptado
const degradar = new Almacen();
await degradar.bueno("luis", (e) => guardarContacto(e, "CLAVE-PUBLICA"));
await degradar.bueno("luis", (e) => guardarContacto(e, null));
const r4 = degradar.datos.get("luis");
comprobar("un mensaje sin clave no borra la que ya había", r4.publicKey === "CLAVE-PUBLICA");

console.log(fallos === 0 ? "\nOK\n" : `\n${fallos} fallos\n`);
process.exit(fallos ? 1 : 0);
