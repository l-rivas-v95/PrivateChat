/**
 * Prepara los módulos del front para poder ejecutarlos en Node y lanza las pruebas.
 *
 *   npm test
 *
 * Usa el esbuild que ya viene con Vite, así que no hace falta instalar nada más.
 */

import { build } from "esbuild";
import { spawnSync } from "node:child_process";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, "..");
const generado = join(aqui, ".generado");

mkdirSync(generado, { recursive: true });

const modulos = [
    { entrada: "src/crypto/cryptoUtils.js", salida: "crypto-bundle.mjs" },
    { entrada: "src/services/payloadService.js", salida: "payload-bundle.mjs" }
];

for (const modulo of modulos) {
    await build({
        entryPoints: [join(raiz, modulo.entrada)],
        outfile: join(generado, modulo.salida),
        bundle: true,
        format: "esm",
        platform: "neutral",
        logLevel: "error"
    });
}

const pruebas = ["cripto.test.mjs", "payload.test.mjs"];
let fallos = 0;

for (const prueba of pruebas) {
    const resultado = spawnSync(process.execPath, [join(aqui, prueba)], { stdio: "inherit" });
    if (resultado.status !== 0) fallos += 1;
}

process.exit(fallos === 0 ? 0 : 1);
