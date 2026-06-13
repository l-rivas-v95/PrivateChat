# PrivateChat

PrivateChat es una aplicación Android de mensajería privada desarrollada como proyecto de práctica. La app permite conectar dos clientes mediante un servidor WebSocket propio, guardar conversaciones en local y enviar mensajes entre usuarios identificados por un ID único.

El proyecto está formado por dos partes:

```text
PrivateChat/        Aplicación Android
PrivateChatServer/  Servidor Spring Boot con WebSocket
```

## Estado actual

El proyecto está en desarrollo. Actualmente tiene una base funcional para chat entre usuarios, almacenamiento local, intercambio de claves y una interfaz dividida en varias pantallas.

Funcionalidades implementadas:

- Aplicación Android nativa con Kotlin y Jetpack Compose.
- Servidor WebSocket propio en Spring Boot.
- Conexión de usuarios mediante un identificador local.
- Lista de conversaciones.
- Pantalla de detalle de chat.
- Pantalla de perfil/ajustes.
- Envío y recepción de mensajes por WebSocket.
- Persistencia local de contactos, chats y mensajes con Room.
- Mensajes pendientes en servidor cuando el receptor no está conectado.
- Confirmaciones de entrega mediante mensajes `ack`.
- Intercambio de claves públicas entre usuarios.
- Cifrado de mensajes cuando existe clave pública del contacto.
- Guardado de contactos desconocidos al recibir mensajes.
- Edición/guardado de contactos desde un chat desconocido.
- Eliminación de conversaciones.
- Borrado de mensajes individuales.
- Limpieza de mensajes de una conversación.
- Hora en los mensajes y fecha/última actividad en la lista.
- Avatar local de perfil y sincronización básica de avatar entre contactos.
- Colores de personalización de la interfaz.

## Tecnologías usadas

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- Coroutines
- OkHttp WebSocket
- AndroidX Lifecycle

La configuración actual de la app usa `compileSdk 36`, `minSdk 24`, `targetSdk 36`, Java 11 y Compose habilitado.

### Servidor

- Java
- Spring Boot
- Spring WebSocket
- `TextWebSocketHandler`
- Gestión en memoria de sesiones conectadas
- Cola en memoria para mensajes pendientes y ACK pendientes

## Arquitectura actual

La app Android todavía está en proceso de refactor. La pantalla principal (`MainActivity`) contiene parte de la navegación, estado de UI, manejo del WebSocket y persistencia local. Se ha empezado a ordenar el flujo para evitar condiciones de carrera al guardar contactos y mensajes.

Actualmente el flujo de persistencia de mensajes se ha separado en funciones internas secuenciales para que, al recibir un mensaje, se haga en orden:

```text
1. guardar o actualizar contacto
2. asegurar conversación local
3. guardar mensaje
4. recargar contactos
5. recargar mensajes
```

Esto evita que un mensaje recibido de un usuario desconocido cree dos conversaciones al mismo tiempo.

## Flujo de comunicación

La comunicación no es móvil a móvil directamente. Ambos clientes se conectan al servidor:

```text
Móvil A -> Servidor WebSocket -> Móvil B
```

Cada cliente se conecta usando su identificador local:

```text
ws://10.0.2.2:8080/chat?user=<userId>
```

`10.0.2.2` sirve para emulador Android. En un móvil físico hay que cambiar esa URL por la IP del equipo donde se esté ejecutando el servidor, por ejemplo:

```text
ws://192.168.1.45:8080/chat?user=<userId>
```

Si se usa una URL pública con HTTPS/túnel, debe usarse `wss://`.

## Tipos de mensajes usados

### Intercambio de claves

```json
{
  "type": "key_exchange",
  "from": "usuarioA",
  "to": "usuarioB",
  "publicKey": "..."
}
```

Se usa para compartir la clave pública del usuario y poder cifrar mensajes posteriores.

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

Cuando el contacto tiene clave pública guardada, la app genera un mensaje cifrado.

### Mensaje sin cifrar de respaldo

```json
{
  "type": "message",
  "id": "uuid",
  "from": "usuarioA",
  "to": "usuarioB",
  "text": "mensaje"
}
```

Existe como respaldo cuando todavía no hay clave pública disponible.

### Confirmación de entrega

```json
{
  "type": "ack",
  "messageId": "uuid",
  "status": "DELIVERED"
}
```

El servidor envía ACK al remitente cuando entrega un mensaje con `id` al receptor.

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

Se usa para sincronizar una imagen de perfil pequeña entre contactos.

## Persistencia local

La app guarda datos en Room.

Entidades principales:

- `ContactEntity`: contacto local, nombre visible, clave pública, avatar y fechas.
- `ChatEntity`: conversación asociada a un contacto.
- `MessageEntity`: mensaje guardado en una conversación.

El identificador interno de Room es local. Para localizar contactos y conversaciones en la lógica de chat se usa el identificador remoto del contacto (`username` / `contactUsername`).

## Servidor WebSocket

El servidor mantiene en memoria:

- usuarios conectados;
- mensajes pendientes por usuario;
- ACK pendientes por usuario.

Cuando recibe un payload con campo `to`, intenta entregarlo al usuario destino. Si el usuario no está conectado, lo guarda en memoria hasta que vuelva a conectarse.

El servidor no persiste mensajes en base de datos. Si se reinicia, se pierden los mensajes pendientes almacenados en memoria.

## Cómo ejecutar en local

### 1. Arrancar el servidor

Arranca el proyecto `PrivateChatServer` desde el IDE o mediante el comando de build correspondiente del proyecto.

El servidor debe quedar escuchando en el puerto `8080`.

### 2. Ejecutar la app Android

Abre el proyecto Android `PrivateChat` en Android Studio y ejecuta la app en un emulador o dispositivo físico.

Para emulador Android, la URL actual del WebSocket apunta a:

```text
ws://10.0.2.2:8080/chat?user=<userId>
```

Para dos móviles físicos en la misma red WiFi, hay que cambiar la URL en `ChatWebSocketClient.kt` por la IP local del PC:

```text
ws://IP_DEL_PC:8080/chat?user=<userId>
```

## Pruebas con móviles físicos

Para probar con dos móviles:

1. Arrancar el servidor en el PC.
2. Asegurarse de que ambos móviles y el PC están en la misma red WiFi.
3. Cambiar la URL del WebSocket para usar la IP del PC.
4. Instalar la app en ambos móviles.
5. Conectar ambos usuarios.
6. Añadir el ID del otro usuario o recibir un mensaje como desconocido.
7. Probar intercambio de claves y envío de mensajes.

También se puede exponer el servidor local mediante un túnel, por ejemplo Cloudflare Tunnel. En ese caso, la app debe usar una URL `wss://`.

## Limitaciones actuales

- El servidor guarda sesiones y mensajes pendientes solo en memoria.
- No hay autenticación real de usuarios.
- Los identificadores de usuario se generan localmente.
- La URL del servidor está fija en código dentro del cliente WebSocket.
- El parser de JSON es manual y simple.
- No hay envío de multimedia como mensajes de chat todavía.
- El avatar se envía en Base64, por lo que solo es adecuado para imágenes pequeñas.
- La estructura Android necesita seguir refactorizándose para separar mejor UI, persistencia y red.

## Próximos pasos previstos

1. Refactorizar la app Android para reducir responsabilidades de `MainActivity`.
2. Extraer lógica de persistencia a un repositorio.
3. Extraer lógica de conexión WebSocket y eventos de chat.
4. Centralizar constantes de rutas, tipos de mensaje y URL del servidor.
5. Mejorar el intercambio de claves para evitar reenvíos innecesarios.
6. Añadir soporte de contenido multimedia empezando por imágenes comprimidas.
7. Preparar configuración para usar servidor local, IP LAN o URL pública sin modificar código manualmente.
8. Añadir autenticación o validación básica si el servidor se expone fuera de la red local.

## Nota

Este proyecto está pensado como práctica de desarrollo Android/backend y como base para experimentar con mensajería, WebSocket, persistencia local y cifrado entre clientes. No debe considerarse una aplicación lista para producción.