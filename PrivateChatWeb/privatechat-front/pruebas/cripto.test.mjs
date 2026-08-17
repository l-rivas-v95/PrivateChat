/**
 * Comprueba que el módulo de cripto del front (WebCrypto) es intercambiable
 * con el esquema de la app Android (JCE: KeyAgreement "ECDH" + SHA-256 + AES/GCM).
 *
 * El lado "Java" se simula con el módulo crypto de Node, que usa OpenSSL con
 * exactamente las mismas primitivas que usa la JCE en Android:
 *   crypto.diffieHellman()      == KeyAgreement.getInstance("ECDH")
 *   createHash("sha256")        == MessageDigest.getInstance("SHA-256")
 *   aes-256-gcm + authTag(16)   == Cipher.getInstance("AES/GCM/NoPadding"), tag 128 bits
 *
 * En Java doFinal() devuelve cipherText||tag, que es justo el formato de WebCrypto.
 */

import { webcrypto, createPrivateKey, createPublicKey, diffieHellman, createHash, createCipheriv, createDecipheriv } from "node:crypto";

// Shim mínimo del navegador para poder importar el módulo real sin tocarlo.
globalThis.window = {
    crypto: webcrypto,
    btoa: (texto) => Buffer.from(texto, "binary").toString("base64"),
    atob: (texto) => Buffer.from(texto, "base64").toString("binary")
};

const {
    generarParClaves,
    exportarClavePublica,
    exportarClavePrivada,
    importarClavePublica,
    importarClavePrivada,
    cifrarTexto,
    descifrarTexto,
    cifrarBytes,
    descifrarBytes
} = await import("./.generado/crypto-bundle.mjs");

let fallos = 0;

function comprobar(nombre, condicion, extra = "") {
    if (condicion) {
        console.log(`  ok   ${nombre}`);
    } else {
        console.log(`  FALLO ${nombre} ${extra}`);
        fallos += 1;
    }
}

// ------------------------------------------------------- lado "Android/Java"

function claveAesEstiloJava(pkcs8Base64, spkiBase64) {
    const privada = createPrivateKey({
        key: Buffer.from(pkcs8Base64, "base64"),
        format: "der",
        type: "pkcs8"
    });
    const publica = createPublicKey({
        key: Buffer.from(spkiBase64, "base64"),
        format: "der",
        type: "spki"
    });

    const secretoCompartido = diffieHellman({ privateKey: privada, publicKey: publica });
    return createHash("sha256").update(secretoCompartido).digest();
}

function cifrarEstiloJava(textoPlano, pkcs8Base64, spkiBase64) {
    const clave = claveAesEstiloJava(pkcs8Base64, spkiBase64);
    const iv = webcrypto.getRandomValues(new Uint8Array(12));

    const cipher = createCipheriv("aes-256-gcm", clave, iv, { authTagLength: 16 });
    const cuerpo = Buffer.concat([cipher.update(textoPlano, "utf8"), cipher.final()]);
    const tag = cipher.getAuthTag();

    return {
        cipherText: Buffer.concat([cuerpo, tag]).toString("base64"),
        iv: Buffer.from(iv).toString("base64")
    };
}

function descifrarEstiloJava(cipherTextBase64, ivBase64, pkcs8Base64, spkiBase64) {
    const clave = claveAesEstiloJava(pkcs8Base64, spkiBase64);
    const todo = Buffer.from(cipherTextBase64, "base64");
    const cuerpo = todo.subarray(0, todo.length - 16);
    const tag = todo.subarray(todo.length - 16);

    const decipher = createDecipheriv("aes-256-gcm", clave, Buffer.from(ivBase64, "base64"), {
        authTagLength: 16
    });
    decipher.setAuthTag(tag);

    return Buffer.concat([decipher.update(cuerpo), decipher.final()]).toString("utf8");
}

// ------------------------------------------------------------------ pruebas

console.log("\nInteroperabilidad cripto front React <-> app Android\n");

// Exportables solo para poder compararlas contra el lado Java en estas pruebas.
// La app real las genera con generarParClaves(false); eso se prueba al final.
const alice = await generarParClaves(true);
const bob = await generarParClaves(true);

const alicePub = await exportarClavePublica(alice.publicKey);
const alicePriv = await exportarClavePrivada(alice.privateKey);
const bobPub = await exportarClavePublica(bob.publicKey);
const bobPriv = await exportarClavePrivada(bob.privateKey);

comprobar(
    "la clave pública exportada es SPKI/X.509 base64 (lo que lee KeyFactory + X509EncodedKeySpec)",
    Buffer.from(alicePub, "base64").length === 91,
    `longitud=${Buffer.from(alicePub, "base64").length}`
);

comprobar(
    "la clave privada exportada es PKCS8 base64 (lo que lee PKCS8EncodedKeySpec)",
    (() => {
        try {
            createPrivateKey({ key: Buffer.from(alicePriv, "base64"), format: "der", type: "pkcs8" });
            return true;
        } catch {
            return false;
        }
    })()
);

comprobar(
    "base64 sin saltos de línea, igual que Base64.NO_WRAP",
    !alicePub.includes("\n") && !alicePriv.includes("\n")
);

comprobar(
    "las dos partes derivan la misma clave AES (ECDH simétrico)",
    claveAesEstiloJava(alicePriv, bobPub).equals(claveAesEstiloJava(bobPriv, alicePub))
);

// React cifra -> Android descifra
const textoUno = "Hola desde el front en React ✅ ñáéíóü";
const cifradoReact = await cifrarTexto(
    textoUno,
    alice.privateKey,
    await importarClavePublica(bobPub)
);
const descifradoJava = descifrarEstiloJava(cifradoReact.cipherText, cifradoReact.iv, bobPriv, alicePub);

comprobar("React cifra -> Android descifra", descifradoJava === textoUno, `-> "${descifradoJava}"`);

// Android cifra -> React descifra
const textoDos = "Mensaje enviado desde la app Kotlin 🔒";
const cifradoJava = cifrarEstiloJava(textoDos, bobPriv, alicePub);
const descifradoReact = await descifrarTexto(
    cifradoJava.cipherText,
    cifradoJava.iv,
    alice.privateKey,
    await importarClavePublica(bobPub)
);

comprobar("Android cifra -> React descifra", descifradoReact === textoDos, `-> "${descifradoReact}"`);

// Ida y vuelta dentro del propio front
const cifradoPropio = await cifrarTexto(textoUno, bob.privateKey, await importarClavePublica(alicePub));
const descifradoPropio = await descifrarTexto(
    cifradoPropio.cipherText,
    cifradoPropio.iv,
    alice.privateKey,
    await importarClavePublica(bobPub)
);

comprobar("ida y vuelta React <-> React", descifradoPropio === textoUno);

// Ficheros binarios (media_message)
const bytesOriginales = webcrypto.getRandomValues(new Uint8Array(4096));
const ficheroCifrado = await cifrarBytes(
    bytesOriginales,
    alice.privateKey,
    await importarClavePublica(bobPub)
);
const ficheroDescifrado = await descifrarBytes(
    ficheroCifrado.bytesCifrados,
    ficheroCifrado.iv,
    bob.privateKey,
    await importarClavePublica(alicePub)
);

comprobar(
    "ficheros binarios: cifrar y descifrar devuelve los mismos bytes",
    Buffer.from(bytesOriginales).equals(Buffer.from(ficheroDescifrado))
);

comprobar("IV de 12 bytes, como en la app Android", ficheroCifrado.iv.length === 12);

comprobar(
    "el tag GCM de 16 bytes va anexado al final, como en doFinal() de Java",
    ficheroCifrado.bytesCifrados.length === bytesOriginales.length + 16
);

// La clave importada desde base64 funciona igual que la original
const claveReimportada = await importarClavePrivada(alicePriv);
const cifradoReimportado = await cifrarTexto(
    "reimportada",
    claveReimportada,
    await importarClavePublica(bobPub)
);
comprobar(
    "una clave privada guardada y reimportada desde localStorage sigue sirviendo",
    descifrarEstiloJava(cifradoReimportado.cipherText, cifradoReimportado.iv, bobPriv, alicePub) ===
        "reimportada"
);

// ------------------------------------------------ claves NO exportables (app real)

console.log("\nClave privada no exportable (lo que usa la app)\n");

const seguro = await generarParClaves(false);
const seguroPub = await exportarClavePublica(seguro.publicKey);

comprobar(
    "la pública sigue exportándose aunque el par sea no exportable",
    Buffer.from(seguroPub, "base64").length === 91
);

comprobar(
    "la privada NO se puede exportar: no hay forma de sacarla del navegador",
    await (async () => {
        try {
            await exportarClavePrivada(seguro.privateKey);
            return false;
        } catch {
            return true;
        }
    })()
);

comprobar("el flag extractable está a false", seguro.privateKey.extractable === false);

const cifradoSeguro = await cifrarTexto(
    "mensaje con clave blindada",
    seguro.privateKey,
    await importarClavePublica(bobPub)
);

comprobar(
    "y aun así descifra y cifra igual que antes",
    descifrarEstiloJava(cifradoSeguro.cipherText, cifradoSeguro.iv, bobPriv, seguroPub) ===
        "mensaje con clave blindada"
);

const migrada = await importarClavePrivada(alicePriv, false);

comprobar(
    "una clave antigua de localStorage se puede reimportar como no exportable (migración)",
    migrada.extractable === false
);

comprobar(
    "y conserva la identidad: sigue derivando el mismo secreto que antes",
    await (async () => {
        const cifrado = await cifrarTexto("misma identidad", migrada, await importarClavePublica(bobPub));
        return descifrarEstiloJava(cifrado.cipherText, cifrado.iv, bobPriv, alicePub) === "misma identidad";
    })()
);

console.log(fallos === 0 ? "\nTodas las pruebas pasan.\n" : `\n${fallos} prueba(s) fallidas.\n`);
process.exit(fallos === 0 ? 0 : 1);
