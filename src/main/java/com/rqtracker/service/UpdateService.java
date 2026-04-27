package com.rqtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class UpdateService {

    private static final Logger LOG = Logger.getLogger(UpdateService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record UpdateInfo(String version, String releaseNotes, String downloadUrl, String publishedAt) {}

    private UpdateService() {}

    // ── 版本檢查 ──────────────────────────────────────────────────────────────

    /**
     * 向 url 發出 GET 請求，若伺服器版本比 currentVersion 新則回傳 UpdateInfo。
     * 支援 GitHub Releases API 與自訂 JSON 兩種格式。
     * 網路或解析錯誤時拋出例外，由呼叫端決定如何呈現。
     */
    public static Optional<UpdateInfo> checkForUpdate(String url, String currentVersion)
            throws IOException, InterruptedException {
        if (url == null || url.isBlank()) return Optional.empty();

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RQTracker/" + currentVersion)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IOException("伺服器回傳 HTTP " + resp.statusCode());

        UpdateInfo info = url.contains("api.github.com")
            ? parseGitHub(resp.body())
            : parseCustomJson(resp.body());

        if (info == null)
            throw new IOException("無法解析版本資訊（回應格式不符）");

        return isNewer(info.version(), currentVersion) ? Optional.of(info) : Optional.empty();
    }

    // ── 下載 MSI ──────────────────────────────────────────────────────────────

    /**
     * 下載 MSI 到 %TEMP%\rq-tracker-update.msi。
     * onProgress 收到 0.0–1.0 的進度值；cancelled 設為 true 可中止。
     * @return 下載完成的 MSI 路徑，中止或失敗時回傳 null
     */
    public static Path download(UpdateInfo info, Consumer<Double> onProgress,
                                AtomicBoolean cancelled) throws IOException, InterruptedException {
        Path dest = Path.of(System.getProperty("java.io.tmpdir"), "rq-tracker-update.msi");

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(info.downloadUrl()))
            .header("User-Agent", "RQTracker")
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();

        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());

        long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        try (InputStream in = resp.body()) {
            byte[] buf = new byte[65536];
            long downloaded = 0;
            var tmp = Files.createTempFile("rq-update-", ".msi");
            try (var out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelled != null && cancelled.get()) {
                        Files.deleteIfExists(tmp);
                        return null;
                    }
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (total > 0 && onProgress != null) {
                        double pct = (double) downloaded / total;
                        onProgress.accept(pct);
                    }
                }
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        if (onProgress != null) onProgress.accept(1.0);
        return dest;
    }

    /**
     * 靜默安裝並在完成後自動重啟。
     *
     * 架構說明（兩個暫存檔）：
     *   rq-update-worker.ps1  — 以管理員身份執行，做實際 msiexec 安裝 + 重啟
     *   rq-update-launcher.vbs — 由 wscript.exe /B 執行，完全無視窗，
     *                            透過 Shell.Application.ShellExecute "runas" 觸發 UAC
     *
     * 關鍵原理：
     *   PowerShell -Verb RunAs 在 Hidden/NonInteractive 程序中可能無法觸發 UAC。
     *   Shell.Application.ShellExecute("powershell","...","","runas",0) 是 Windows
     *   官方建議的背景程序 UAC 觸發方式，無論父程序視窗狀態如何都能正確顯示 UAC。
     */
    public static void launchInstaller(Path msi) throws IOException {
        String msiPath = msi.toAbsolutePath().toString();
        String tmpDir  = System.getProperty("java.io.tmpdir");

        // 取得目前執行檔路徑（jpackage 安裝後為 RQTracker.exe）
        String exePath = ProcessHandle.current().info().command().orElse("");
        boolean canRelaunch = !exePath.isBlank()
            && exePath.toLowerCase().endsWith(".exe")
            && !exePath.toLowerCase().contains("java");

        // ── Step 1：寫 PowerShell worker（以提權後的管理員身份執行） ─────────────
        StringBuilder psWorker = new StringBuilder();
        // 寫安裝日誌到 %TEMP%\rq-update-log.txt 方便診斷
        psWorker.append("$log = \"$env:TEMP\\rq-update-log.txt\"\r\n");
        psWorker.append("\"$(Get-Date -f 'HH:mm:ss') [START] worker running as admin\" | Out-File $log -Encoding UTF8\r\n");
        psWorker.append("$msi = '").append(msiPath.replace("'", "''")).append("'\r\n");
        psWorker.append("\"$(Get-Date -f 'HH:mm:ss') [INFO] msi=$msi\" | Add-Content $log\r\n");
        psWorker.append("\"$(Get-Date -f 'HH:mm:ss') [INFO] exists=$(Test-Path $msi)\" | Add-Content $log\r\n");
        // 以管理員身份直接呼叫 msiexec（不需 -Verb RunAs，已是提權程序）
        psWorker.append("$proc = Start-Process msiexec -ArgumentList @('/i', $msi, '/quiet', '/norestart') -Wait -PassThru\r\n");
        psWorker.append("\"$(Get-Date -f 'HH:mm:ss') [INFO] exitCode=$($proc.ExitCode)\" | Add-Content $log\r\n");
        if (canRelaunch) {
            psWorker.append("if ($proc -ne $null -and $proc.ExitCode -eq 0) {\r\n");
            psWorker.append("    Start-Sleep -Seconds 3\r\n");
            psWorker.append("    Start-Process '").append(exePath.replace("'", "''")).append("'\r\n");
            psWorker.append("    \"$(Get-Date -f 'HH:mm:ss') [INFO] relaunched\" | Add-Content $log\r\n");
            psWorker.append("}\r\n");
        }
        psWorker.append("\"$(Get-Date -f 'HH:mm:ss') [END] done\" | Add-Content $log\r\n");

        Path psFile = java.nio.file.Path.of(tmpDir, "rq-update-worker.ps1");
        java.nio.file.Files.writeString(psFile, psWorker.toString(),
            java.nio.charset.StandardCharsets.UTF_8);

        // ── Step 2：寫 VBScript launcher（Shell.Application.ShellExecute = Windows 官方 UAC 觸發） ──
        // VBScript 字串內 "" 代表一個 "，故路徑要包在 ""PATH"" 之中
        // sh.ShellExecute "powershell.exe", "-... -File ""PATH""", "", "runas", 0
        String psAbsPath = psFile.toAbsolutePath().toString();
        String vbsContent =
            "WScript.Sleep 4000\r\n" +
            "Set sh = CreateObject(\"Shell.Application\")\r\n" +
            "sh.ShellExecute \"powershell.exe\", " +
            "\"-ExecutionPolicy Bypass -WindowStyle Hidden -File \"\"" +
            psAbsPath +
            "\"\"\", \"\", \"runas\", 0\r\n";

        Path vbsFile = java.nio.file.Path.of(tmpDir, "rq-update-launcher.vbs");
        java.nio.file.Files.writeString(vbsFile, vbsContent,
            java.nio.charset.StandardCharsets.UTF_8);

        // ── Step 3：用 wscript.exe /B 靜默啟動 VBS（/B 隱藏所有錯誤對話框） ─────
        new ProcessBuilder("wscript.exe", "/B",
            vbsFile.toAbsolutePath().toString()).start();

        javafx.application.Platform.exit();
        System.exit(0);
    }

    // ── 解析 ──────────────────────────────────────────────────────────────────

    private static UpdateInfo parseGitHub(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String tag = root.path("tag_name").asText("").replaceFirst("^v", "");
            String notes = root.path("body").asText("");
            String pub = root.path("published_at").asText("").substring(0, Math.min(10, root.path("published_at").asText("").length()));
            JsonNode assets = root.path("assets");
            String dlUrl = "";
            if (assets.isArray()) {
                for (JsonNode a : assets) {
                    String name = a.path("name").asText("");
                    if (name.endsWith(".msi") || name.endsWith(".exe")) {
                        dlUrl = a.path("browser_download_url").asText("");
                        break;
                    }
                }
            }
            if (tag.isBlank()) return null;
            return new UpdateInfo(tag, notes, dlUrl, pub);
        } catch (Exception e) {
            LOG.warning("GitHub API 解析失敗：" + e.getMessage());
            return null;
        }
    }

    private static UpdateInfo parseCustomJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String ver  = root.path("version").asText("");
            String notes = root.path("releaseNotes").asText("");
            String dl   = root.path("downloadUrl").asText("");
            String pub  = root.path("publishedAt").asText("");
            if (ver.isBlank()) return null;
            return new UpdateInfo(ver, notes, dl, pub);
        } catch (Exception e) {
            LOG.warning("自訂 JSON 解析失敗：" + e.getMessage());
            return null;
        }
    }

    // ── 版本比較 ──────────────────────────────────────────────────────────────

    static boolean isNewer(String remote, String local) {
        try {
            int[] r = parse(remote);
            int[] l = parse(local);
            for (int i = 0; i < Math.max(r.length, l.length); i++) {
                int rv = i < r.length ? r[i] : 0;
                int lv = i < l.length ? l[i] : 0;
                if (rv != lv) return rv > lv;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] parse(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) nums[i] = Integer.parseInt(parts[i].trim());
        return nums;
    }
}
