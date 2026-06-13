# PrivateChat

PrivateChat es una aplicación Android de mensajería privada desarrollada como proyecto de práctica. Permite conectar usuarios mediante un servidor WebSocket propio, añadir contactos por QR o manualmente, aceptar solicitudes, enviar mensajes cifrados y guardar conversaciones localmente en el dispositivo.

El proyecto está dividido en dos módulos principales:

```text
PrivateChat/        Aplicación Android nativa
PrivateChatServer/  Servidor Spring Boot con WebSocket
```

## Estado actual

El proyecto ya cuenta con una base funcional de chat entre dos dispositivos conectados al mismo servidor. La aplicación tiene interfaz tipo mensajería, almacenamiento local, contactos, avatares comprimidos y comunicación mediante WebSocket.

Funcionalidades implementadas:

- Aplicación Android nativa con Kotlin y Jetpack Compose.
- Servidor WebSocket propio con Spring Boot.
- Tres pantallas principales: chats, chat abierto y perfil/ajustes.
- Alta de contacto por QR.
- Alta manual de contacto mediante ID y clave pública.
- Sistema de solicitudes pendientes antes de aceptar contactos.
- Envío de invitación de contacto (`contact_invite`).
- Aceptación de contacto (`contact_accept`).
- Lista de conversaciones con último mensaje y hora.
- Pantalla de chat con cabecera fija, avatar, nombre editable y botón para vaciar conversación.
- Envío y recepción de mensajes por WebSocket.
- Cifrado de mensajes cuando el contacto tiene clave pública guardada.
- Persistencia local con Room para contactos, chats y mensajes.
- Mensajes pendientes en servidor si el receptor no está conectado.
- Confirmaciones de entrega mediante ACK.
- Borrado de mensajes individuales.
- Borrado local de conversaciones.
- Vaciado local de mensajes de una conversación.
- Perfil local con nombre, avatar, color de interfaz e identificador QR.
- Sincronización de avatar entre contactos aceptados.
- Compresión automática de avatar para evitar payloads grandes en WebSocket.
- Protección en servidor frente a mensajes demasiado grandes.
- Icono personalizado de la app.

## Tecnologías usadas

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- Coroutines
- OkHttp WebSocket
- AndroidX Lifecycle / ViewModel
- Cámara para lectura de QR
- BuildConfig para configurar la URL del servidor

La aplicación Android está dentro de:

```text
PrivateChat/
```

### Servidor

- Java
- Spring Boot
- Spring WebSocket
- `TextWebSocketHandler`
- Gestión en memoria de usuarios conectados
- Cola en memoria de mensajes pendientes
- Cola en memoria de ACK pendientes

El servidor está dentro de:

```text
PrivateChatServer/
```

## Arquitectura general

La comunicación no es móvil a móvil directamente. Ambos clientes se conectan al servidor WebSocket:

```text
Móvil A -> Servidor WebSocket -> Móvil B
```

Cada usuario tiene un identificador local generado por la app. Ese identificador se usa para abrir la conexión WebSocket:

```text
ws://IP_DEL_SERVIDOR:8080/chat?user=<userId>
```

El servidor mantiene un mapa de usuarios conectados. Cuando recibe un payload con campo `to`, intenta entregarlo al destinatario. Si el destinatario no está conectado, guarda el mensaje en memoria y lo entrega cuando vuelva a conectarse.

## Configuración del servidor en Android

La URL base del servidor se configura desde `local.properties` en el proyecto Android.

Ejemplo para emulador:

```properties
CHAT_SERVER_URL=ws://10.0.2.2:8080
```

Ejemplo para móvil físico en la misma red WiFi:

```properties
CHAT_SERVER_URL=ws://192.168.1.45:8080
```

No hay que añadir `/chat?user=...` en `local.properties`, porque la app lo añade automáticamente.

El archivo `local.properties` no debe subirse al repositorio, ya que depende del equipo y de la red local.

## Flujo de contactos

### Añadir por QR

1. Un usuario abre su perfil.
2. La app muestra un QR con su ID, nombre visible y clave pública.
3. Otro usuario escanea ese QR desde `+ Nuevo chat`.
4. Se guarda el contacto y se envía una invitación.
5. El receptor ve una solicitud pendiente.
6. Al aceptar, ambos usuarios pueden enviarse mensajes cifrados.

### Solicitudes pendientes

Cuando llega una invitación de contacto, la app no lo añade directamente como chat aceptado. Lo guarda como contacto pendiente.

En la pantalla de chats aparece una sección:

```text
Solicitudes pendientes
```

Desde ahí se puede aceptar o rechazar el contacto.

## Tipos de payload principales

### Invitación de contacto

```json
{
  "type": "contact_invite",
  "from": "usuarioA",
  "to": "usuarioB",
  "displayName": "Luis",
  "publicKey": "..."
}
```

### Aceptación de contacto

```json
{
  "type": "contact_accept",
  "from": "usuarioB",
  "to": "usuarioA",
  "displayName": "Pepe",
  "publicKey": "..."
}
```

### Mensaje cifrado

```json
{
  "type": "message",
  "id": "uuid",
  "from": "usuarioA",
  "to": "usuarioB",
  "cipherText": "...",
  "iv": "..."
}
```

### Confirmación de entrega

```json
{
  "type": "ack",
  "messageId": "uuid",
  "status": "DELIVERED"
}
```

### Avatar de perfil

```json
{
  "type": "profile_avatar",
  "from": "usuarioA",
  "to": "usuarioB",
  "avatarBase64": "...",
  "updatedAt": "timestamp"
}
```

Los avatares se comprimen antes de enviarse. La app reduce la imagen, baja la calidad y solo la manda si queda por debajo del límite definido para evitar errores WebSocket por payload demasiado grande.

## Persistencia local

La app guarda información en Room.

Entidades principales:

- `ContactEntity`: contacto, nombre visible, clave pública, avatar, estado y fechas.
- `ChatEntity`: conversación local asociada a un contacto.
- `MessageEntity`: mensaje guardado dentro de una conversación.

Estados de contacto:

```text
PENDING   Solicitud pendiente
ACCEPTED  Contacto aceptado
```

La base de datos local permite conservar conversaciones, contactos y mensajes aunque se cierre la app.

## Servidor WebSocket

El servidor mantiene en memoria:

- usuarios conectados;
- mensajes pendientes por usuario;
- ACK pendientes por usuario.

Cuando el destinatario está conectado, el payload se entrega directamente. Cuando no lo está, se guarda en memoria.

El servidor también descarta payloads demasiado grandes para evitar cierres de conexión con errores como:

```text
CloseStatus code=1009
The decoded text message was too big
```

Importante: los mensajes pendientes no se guardan en base de datos. Si el servidor se reinicia, se pierden los mensajes pendientes.

## Cómo ejecutar en local

### 1. Arrancar el servidor

Abre `PrivateChatServer` y ejecútalo desde el IDE o con Maven.

El servidor debe quedar escuchando en:

```text
http://localhost:8080
```

El endpoint WebSocket usado por la app es:

```text
/ws/chat?user=<userId>
```

o según la configuración actual del cliente:

```text
/chat?user=<userId>
```

### 2. Configurar la URL en Android

En `PrivateChat/local.properties`, añade la URL base del servidor:

```properties
CHAT_SERVER_URL=ws://IP_DEL_SERVIDOR:8080
```

Para emulador Android:

```properties
CHAT_SERVER_URL=ws://10.0.2.2:8080
```

Para móvil físico:

```properties
CHAT_SERVER_URL=ws://192.168.1.45:8080
```

### 3. Ejecutar la app

Abre `PrivateChat` en Android Studio y ejecuta la app en un emulador o dispositivo físico.

Para probar con dos móviles físicos:

1. Arranca el servidor en el PC.
2. Conecta ambos móviles y el PC a la misma red WiFi.
3. Configura `CHAT_SERVER_URL` con la IP local del PC.
4. Instala la app en ambos móviles.
5. Abre la app en ambos dispositivos.
6. Añade un contacto por QR.
7. Acepta la solicitud pendiente.
8. Envía mensajes.

## Estructura aproximada del cliente Android

```text
PrivateChat/app/src/main/java/com/lrv/privatechat/
├── crypto/          Gestión de claves y cifrado
├── data/            Room, entidades y DAOs
├── model/           Modelos de UI y constantes
├── network/         Cliente WebSocket y payloads
├── presentation/    ViewModel y estado de UI
├── ui/              Pantallas, componentes y tema
└── util/            Utilidades como compresión de imagen
```

## Limitaciones actuales

- El servidor no tiene autenticación real.
- Los identificadores de usuario se generan localmente.
- El servidor guarda mensajes pendientes solo en memoria.
- Si se reinicia el servidor, se pierden los mensajes pendientes.
- El sistema de cifrado es funcional para práctica, pero no debe considerarse auditado para producción.
- Los avatares se envían en Base64, por lo que deben ser pequeños.
- No hay notificaciones push en segundo plano todavía.
- No hay soporte completo de multimedia como mensajes de chat.
- No hay sincronización entre varios dispositivos del mismo usuario.

## Próximos pasos posibles

- Añadir notificaciones locales o servicio en primer plano.
- Añadir Firebase Cloud Messaging para notificaciones reales en segundo plano.
- Persistir mensajes pendientes en base de datos del servidor.
- Añadir autenticación o registro de usuarios.
- Mejorar el sistema de cifrado y verificación de claves.
- Añadir envío de imágenes como mensajes.
- Mejorar diseño visual y animaciones.
- Preparar despliegue del servidor en una URL pública con `wss://`.

## Nota

PrivateChat es un proyecto de aprendizaje para practicar Android, Jetpack Compose, Room, WebSocket, Spring Boot y cifrado entre clientes. No debe considerarse una aplicación lista para producción ni una alternativa real a aplicaciones de mensajería seguras.
