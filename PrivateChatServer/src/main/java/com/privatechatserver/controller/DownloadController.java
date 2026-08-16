package com.privatechatserver.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;

@Controller
public class DownloadController {

    private static final String APK_PATH = "/data/apk/privatechat.apk";
    private static final String APK_URL  = "https://lrivasvilla95.duckdns.org/privatechat/apk/download";

    @GetMapping("/privatechat/apk")
    @ResponseBody
    public ResponseEntity<String> downloadPage() {
        boolean apkExists = new File(APK_PATH).exists();
        String html = buildHtml(apkExists);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/privatechat/apk/download")
    public ResponseEntity<Resource> downloadApk() {
        File apkFile = new File(APK_PATH);
        if (!apkFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(apkFile);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"privatechat.apk\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(apkFile.length()))
                .body(resource);
    }

    private String buildHtml(boolean apkExists) {
        String statusBlock = apkExists
                ? "<p class=\"available\">✅ APK disponible</p>"
                : "<p class=\"unavailable\">⚠️ APK aún no subida al servidor</p>";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Descargar PrivateChat</title>
                  <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
                  <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      background: #0f1117;
                      color: #e0e0e0;
                      min-height: 100vh;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                    }
                    .card {
                      background: #1a1d27;
                      border-radius: 20px;
                      padding: 40px 36px;
                      max-width: 400px;
                      width: 90%;
                      text-align: center;
                      box-shadow: 0 8px 40px rgba(0,0,0,0.5);
                    }
                    .logo { width: 96px; height: 96px; border-radius: 22px; margin-bottom: 12px; }
                    h1 { font-size: 24px; font-weight: 700; margin-bottom: 4px; }
                    .subtitle { color: #888; font-size: 14px; margin-bottom: 28px; }
                    #qrcode {
                      display: inline-block;
                      padding: 16px;
                      background: white;
                      border-radius: 14px;
                      margin-bottom: 20px;
                    }
                    .scan-hint { color: #aaa; font-size: 13px; margin-bottom: 24px; }
                    .download-btn {
                      display: inline-block;
                      background: #4caf50;
                      color: white;
                      text-decoration: none;
                      padding: 14px 32px;
                      border-radius: 30px;
                      font-size: 16px;
                      font-weight: 600;
                      transition: background 0.2s;
                    }
                    .download-btn:hover { background: #43a047; }
                    .available   { color: #4caf50; font-size: 13px; margin-top: 20px; }
                    .unavailable { color: #f5a623; font-size: 13px; margin-top: 20px; }
                    .note { color: #666; font-size: 12px; margin-top: 16px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <img class="logo" src="/privatechat/apk/icon.webp" alt="PrivateChat">
                    <h1>PrivateChat</h1>
                    <p class="subtitle">Mensajería cifrada de extremo a extremo</p>

                    <div id="qrcode"></div>
                    <p class="scan-hint">Escanea el QR con tu móvil Android para descargar</p>

                    <a class="download-btn" href="/privatechat/apk/download">⬇ Descargar APK</a>
                    """ + statusBlock + """
                    <p class="note">Solo compatible con Android · Permite instalar apps de fuentes desconocidas</p>
                  </div>

                  <script>
                    new QRCode(document.getElementById("qrcode"), {
                      text: \"""" + APK_URL + """
",
                      width: 200,
                      height: 200,
                      colorDark: "#000000",
                      colorLight: "#ffffff",
                      correctLevel: QRCode.CorrectLevel.M
                    });
                  </script>
                </body>
                </html>
                """;
    }
}
