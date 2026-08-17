# PrivateChat Web

Cliente web de PrivateChat en React. Es una reescritura del front que hoy está en
Kotlin + Jetpack Compose (`PrivateChat/`), **sin ningún cambio en el servidor**:
habla el mismo protocolo WebSocket, usa el mismo esquema de cifrado y los mismos
endpoints de ficheros, así que la app Android y esta web pueden chatear entre sí.

```text
PrivateChat/        App Android (Kotlin + Compose)   ← sin tocar
PrivateChatServer/  Servidor Spring Boot             ← sin tocar
PrivateChatWeb/     Este proyecto (React + Vite)
```

## Arrancar en local

```bash
cd PrivateChatWeb/privatechat-front
npm install
npm run dev
```

Abre `http://localhost:5173` con el servidor Spring arrancado en el 8080. No
hace falta configurar nada más. Si tu servidor está en otra máquina o en otro
puerto, copia `.env.example` a `.env` y ajusta las dos variables.

### Cómo llega cada cosa al servidor

Las dos vías se tratan distinto, y es a propósito:

- **El WebSocket va directo** a `ws://localhost:8080/chat`. `WebSocketConfig.java`
  registra el handler con `setAllowedOrigins("*")`, así que el servidor acepta
  el handshake aunque el front se sirva desde otro puerto. Meterlo por un proxy
  solo añadía una pieza que se podía romper.
- **`/upload` y `/file` van por el proxy de Vite**, porque esos controladores no
  mandan cabeceras CORS y el navegador bloquearía la llamada desde `:5173`.
  Saliendo por el mismo origen el problema desaparece sin tocar el backend.

En producción hay dos opciones que tampoco tocan el back:

1. Servir el `dist/` desde el mismo dominio que el servidor (por ejemplo detrás
   del mismo nginx de `lrivasvilla95.duckdns.org`). Con `VITE_CHAT_SERVER_URL`
   vacío, el front usa siempre el origen desde el que se ha cargado.
2. Copiar el `dist/` dentro de `src/main/resources/static` del servidor Spring.

## HTTPS o localhost, obligatorio

El navegador solo expone WebCrypto y la cámara en contextos seguros. En
`http://192.168.1.45:5173` no se pueden generar las claves ECDH ni leer el QR.
Usa `localhost` en desarrollo y `https://` en producción. La app avisa por
pantalla si detecta que no está en un contexto seguro.

## Qué se ha portado

| App Android | Web |
| --- | --- |
| `KeyPairManager` (SharedPreferences) | `crypto/keyPairManager.js` (CryptoKey no exportable en IndexedDB) |
| `CryptoUtils` (JCE) | `crypto/cryptoUtils.js` (WebCrypto) |
| `ChatCryptoService` | `crypto/chatCryptoService.js` |
| `ChatPayloads` | `services/payloadService.js` |
| `ChatWebSocketClient` (OkHttp) | `services/chatSocketService.js` (WebSocket nativo) |
| `ChatFileClient` | `services/fileService.js` |
| Room (`contacts`, `chats`, `chat_messages`) | IndexedDB (`storage/`) |
| `PrivateChatViewModel` + `ChatRealtimeManager` | `hooks/usePrivateChat.js` |
| `ChatNotificationHelper` | `hooks/useNotificaciones.js` (Notification API) |
| `ImageBase64Encoder` (Bitmap) | `utils/imageUtils.js` (canvas) |
| Pantallas Compose | `components/` (estilo WhatsApp Web) |

Funciona: alta por QR y manual, solicitudes pendientes, invitación y aceptación,
mensajes cifrados, ACK de entrega, adjuntos cifrados (imagen, vídeo, audio y
ficheros), avatares sincronizados, no leídos, borrar mensaje, vaciar y eliminar
conversación, color de acento, tema claro/oscuro y reconexión automática.

## Compatibilidad con la app Android

El cifrado es el mismo esquema, campo por campo:

- Curva EC **P-256**, la que genera `KeyPairGenerator("EC").initialize(256)`.
- Clave pública en **SPKI/X.509** y privada en **PKCS8**, ambas en Base64 sin
  saltos de línea (`Base64.NO_WRAP`).
- **ECDH** para el secreto compartido, **SHA-256** para derivar la clave AES.
- **AES/GCM/NoPadding**, IV de 12 bytes, tag de 128 bits anexado al final,
  que es justo lo que devuelve y espera `doFinal()` en Java.

Eso está comprobado con pruebas automáticas:

```bash
npm test
```

Las pruebas cifran con el módulo real del front y descifran con una
implementación equivalente a la de la JCE (y al revés), además de verificar que
todos los payloads JSON llevan exactamente los campos que leen
`ChatSocketHandler.java` y `ChatPayloads.kt`.

## Dónde se guarda la identidad

El `userId` es un UUID que se genera la primera vez que se abre la app y vive en
`localStorage`, igual que en las `SharedPreferences` de Android.

Las **claves ECDH no**. Se generan con `extractable: false` y se guardan como
objetos `CryptoKey` dentro de IndexedDB. El navegador las conserva entre
recargas y las deja usar para derivar el secreto compartido, pero no existe
ninguna forma de convertirlas de nuevo en bytes: ni `exportKey`, ni este código,
ni ningún script inyectado en la página. En Android la protección la daba el
sandbox de la app; aquí la da el propio navegador.

Si vienes de una versión anterior que las guardaba en Base64 en `localStorage`,
se migran solas al arrancar (reimportadas ya como no exportables) y se borra la
copia en claro. No se pierde la identidad ni los contactos.

La contrapartida: **no se puede hacer copia de seguridad de la identidad**. Si se
borran los datos del sitio, se pierde, y con ella los mensajes antiguos, porque
el secreto compartido ya no se puede derivar.

## PWA

La app es instalable. En Chrome y Edge aparece un botón *Instalar en este
dispositivo* dentro de Perfil y ajustes; en iOS hay que hacerlo desde
Compartir → Añadir a pantalla de inicio.

Instalarla da tres cosas: ventana propia sin barra del navegador, arranque sin
red gracias a la caché del `service worker`, y sobre todo **almacenamiento
persistente**, que impide que el navegador desaloje los datos y esquiva el
borrado a los 7 días que aplica Safari a las webs que no se visitan.

El service worker (`public/sw.js`) solo cachea el cascarón: HTML, JS, CSS e
iconos. `/chat`, `/upload` y `/file` quedan explícitamente fuera. Solo se
registra en la build de producción, así que para probarlo en local hay que hacer
`npm run build` y luego `npm run preview`.

**Lo que la PWA no arregla**: seguir recibiendo mensajes con la app cerrada. Eso
necesita Web Push, y Web Push necesita que el servidor guarde suscripciones y
mande las notificaciones con claves VAPID. Es lo único que hoy sigue haciendo
mejor el APK.

## Otras diferencias con la app Android

- **Sin servicio en segundo plano.** El navegador cierra el WebSocket al dormir
  la pestaña. El cliente reconecta solo (con backoff) y al volver a primer plano,
  y el servidor entrega los mensajes pendientes al reconectar.
- **Notificaciones del navegador** en lugar de notificaciones nativas, y solo
  cuando la pestaña no está visible.
- **Los adjuntos se guardan como `Blob` en IndexedDB**, no como ficheros en
  disco. Recuerda que el servidor borra el fichero en la primera descarga.
- Cada navegador es una identidad distinta, igual que cada instalación de la app
  genera su propio `userId`.

## Estructura

```text
src/
├── components/    Pantallas y componentes (JSX + CSS por componente)
│   ├── chat/      Ventana de conversación, burbujas, redactor, adjuntos
│   ├── common/    Avatar, iconos, QR
│   ├── dialogs/   Modal, nuevo chat, escáner QR
│   ├── profile/   Perfil y ajustes
│   └── sidebar/   Lista de chats y solicitudes pendientes
├── config/        URLs del servidor y constantes compartidas
├── crypto/        Claves ECDH y cifrado AES-GCM
├── hooks/         usePrivateChat (el equivalente al ViewModel)
├── services/      WebSocket, payloads y subida/descarga de ficheros
├── storage/       IndexedDB (contactos, chats, mensajes) y perfil local
└── utils/         Base64, fechas y compresión de avatar
```

## Limitaciones heredadas del servidor

Las mismas que ya tenía el proyecto: no hay autenticación real, los
identificadores se generan en el cliente y el cifrado es funcional para
practicar, pero no está auditado.
