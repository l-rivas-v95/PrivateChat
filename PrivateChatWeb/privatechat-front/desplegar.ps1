# Despliega el front en el VPS: compila, sube y arregla permisos.
#
#   npm run deploy
#
# El chmod del paso 3 no es opcional: scp crea los ficheros sin permiso de
# lectura para www-data (el umask de root en el servidor es restrictivo), y sin
# eso nginx devuelve 403 en el CSS y el JS, y la pagina sale en blanco.

$ErrorActionPreference = "Stop"

$servidor = "root@lrivasvilla95.duckdns.org"
$destino  = "/var/www/privatechat"
$url      = "https://lrivasvilla95.duckdns.org/privatechat/"

function Paso($numero, $texto) {
    Write-Host ""
    Write-Host "[$numero/4] $texto" -ForegroundColor Cyan
}

# ---------------------------------------------------------------- 1. compilar

Paso 1 "Compilando"
npm run build
if ($LASTEXITCODE -ne 0) { throw "El build ha fallado, no se sube nada." }

# ------------------------------------------------------------------- 2. subir

Paso 2 "Subiendo a $servidor`:$destino"
scp -r .\dist\* "${servidor}:${destino}/"
if ($LASTEXITCODE -ne 0) { throw "La subida ha fallado." }

# --------------------------------------------------------------- 3. permisos

Paso 3 "Ajustando permisos"
ssh $servidor "chmod -R a+rX $destino"
if ($LASTEXITCODE -ne 0) { throw "No se pudieron ajustar los permisos." }

# -------------------------------------------------------------- 4. verificar

Paso 4 "Verificando que todo se sirve"

try {
    $html = (Invoke-WebRequest $url -UseBasicParsing -Headers @{ "Cache-Control" = "no-cache" }).Content
} catch {
    throw "No se pudo cargar $url : $_"
}

# Coge todo lo que el index.html referencia y no sea externo, tanto si la ruta
# es absoluta (/privatechat/assets/...) como relativa (favicon.svg).
$rutas = [regex]::Matches($html, '(?:src|href)="([^"]+)"') |
         ForEach-Object { $_.Groups[1].Value } |
         Where-Object { $_ -notmatch '^(https?:|data:|mailto:|//|#)' } |
         Select-Object -Unique

if ($rutas.Count -eq 0) {
    Write-Host "  Aviso: el index.html no referencia ningun recurso local." -ForegroundColor Yellow
}

$fallos = 0
foreach ($ruta in $rutas) {
    $completa = [System.Uri]::new([System.Uri]$url, $ruta).AbsoluteUri
    try {
        $codigo = (Invoke-WebRequest $completa -UseBasicParsing -Method Head).StatusCode
        Write-Host "  $codigo  $ruta" -ForegroundColor DarkGray
    } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        Write-Host "  $codigo  $ruta" -ForegroundColor Red
        $fallos++
    }
}

# El service worker se pide aparte: no sale referenciado en el HTML.
foreach ($extra in @("sw.js", "manifest.webmanifest")) {
    $completa = [System.Uri]::new([System.Uri]$url, $extra).AbsoluteUri
    try {
        $codigo = (Invoke-WebRequest $completa -UseBasicParsing -Method Head).StatusCode
        Write-Host "  $codigo  $extra" -ForegroundColor DarkGray
    } catch {
        $codigo = $_.Exception.Response.StatusCode.value__
        Write-Host "  $codigo  $extra" -ForegroundColor Red
        $fallos++
    }
}

Write-Host ""
if ($fallos -gt 0) {
    throw "$fallos recurso(s) no se sirven. Si son 403, revisa los permisos de $destino."
}

Write-Host "Desplegado. $url" -ForegroundColor Green
Write-Host "Recuerda recargar con Ctrl+Shift+R para saltarte el service worker." -ForegroundColor DarkGray
