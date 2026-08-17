# Subir el front al servidor

El nginx que hay delante **no es un contenedor**: es el nginx del sistema, y su
config está en `/etc/nginx/sites-available/lrivasvilla95`. El `nginx.conf` y el
`docker-compose.yml` que hay dentro de `PrivateChatServer/` no se usan.

La raíz del dominio ya la ocupa SwapCloset, así que PrivateChat va en
**`https://lrivasvilla95.duckdns.org/privatechat/`**.

El contenedor `privatechat` corre suelto (sin compose) publicando el 8082, y no
se toca.

## Preparación (una sola vez)

**1. En el VPS, crea la carpeta y guarda copia de la config:**

```bash
mkdir -p /var/www/privatechat
cp /etc/nginx/sites-available/lrivasvilla95 ~/lrivasvilla95.bak
```

**2. Edita la config:**

```bash
nano /etc/nginx/sites-available/lrivasvilla95
```

Pega el contenido de `nginx-privatechat.conf` dentro del bloque
`server { listen 443 ssl; ... }`, justo antes de la llave que lo cierra. No hay
que quitar nada de lo que ya está.

**3. Comprueba y recarga:**

```bash
nginx -t
systemctl reload nginx
```

## Cada vez que actualices

**En tu PC:**

```powershell
cd "C:\Users\luisr\Desktop\GitHub PrivateChat\PrivateChatWeb\privatechat-front"
npm run build
scp -r .\dist\* root@lrivasvilla95.duckdns.org:/var/www/privatechat/
ssh root@lrivasvilla95.duckdns.org "chmod -R a+rX /var/www/privatechat"
```

**La tercera línea no es opcional.** `scp` crea los ficheros con permisos que no
dejan leerlos a `www-data`, así que nginx devolvería 403 en el CSS y el JS y la
página saldría en blanco. Hay que ejecutarla en cada subida, no solo la primera.
La `X` mayúscula es importante: pone el bit de ejecución solo en directorios.

Nginx lee del disco en cada petición: no hay que recargar nada.

## Comprobar

```bash
curl -sI https://lrivasvilla95.duckdns.org/privatechat/ | head -3
curl -sI https://lrivasvilla95.duckdns.org/privatechat/apk | head -3
```

Los dos deben responder `200`. El segundo es la página del APK, que tiene que
seguir funcionando igual que antes.

## Por qué la app va en subruta

Como no vive en la raíz, hay tres cosas que tienen que apuntar al sitio correcto:

- `vite.config.js` tiene `base: '/privatechat/'`, y con eso Vite genera todas
  las rutas de los assets con ese prefijo.
- `manifest.webmanifest` usa rutas relativas (`"./"`, `"icon-192.png"`), que el
  navegador resuelve contra la propia URL del manifest.
- `sw.js` deduce su base de dónde está él mismo, y se registra con `scope`
  `/privatechat/`.

La API **no** lleva prefijo: `/chat`, `/upload` y `/file/` siguen en la raíz del
dominio, que es donde los tiene tu nginx.

En local esto también cambia: `npm run dev` ahora sirve en
`http://localhost:5173/privatechat/`. Vite lo imprime al arrancar.

## Cosas que conviene saber

**`scp` no borra ficheros viejos.** Los assets llevan hash, así que se acumulan
sin molestar. De vez en cuando:

```bash
rm -rf /var/www/privatechat/*
```

**Caché.** Los `/assets/` se cachean un año porque su nombre cambia con cada
build. `index.html`, `sw.js` y el manifest van con `no-cache` para que las
actualizaciones lleguen; si se cachearan, el usuario se quedaría clavado en la
versión antigua.

**Permisos.** Si nginx da 403, es que no puede leer la carpeta:

```bash
chmod -R a+rX /var/www/privatechat
```

**Volver atrás:**

```bash
cp ~/lrivasvilla95.bak /etc/nginx/sites-available/lrivasvilla95
nginx -t && systemctl reload nginx
```
