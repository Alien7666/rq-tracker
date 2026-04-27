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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class UpdateService {

    private static final Logger LOG = Logger.getLogger(UpdateService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UPDATE_LOG_NAME = "rq-update-log.txt";
    private static final String UPDATE_STARTED_FLAG_NAME = "rq-update-started.flag";

    public record UpdateInfo(String version, String releaseNotes, String downloadUrl, String publishedAt) {}

    private UpdateService() {}

    public static Path getUpdateStartedFlagPath() {
        return Path.of(System.getProperty("java.io.tmpdir"), UPDATE_STARTED_FLAG_NAME);
    }

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
     * 啟動提權 worker 進行靜默安裝。
     * 呼叫端應等待 getUpdateStartedFlagPath() 出現後再關閉目前 App。
     */
    public static void launchInstaller(Path msi) throws IOException {
        if (msi == null) {
            throw new IOException("尚未下載安裝檔，請重新下載更新。");
        }
        Path msiAbs = msi.toAbsolutePath();
        if (!Files.isRegularFile(msiAbs) || Files.size(msiAbs) <= 0) {
            throw new IOException("找不到有效的 MSI 安裝檔：" + msiAbs);
        }

        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path logFile = tmpDir.resolve(UPDATE_LOG_NAME);
        Path flagFile = getUpdateStartedFlagPath();
        Files.deleteIfExists(flagFile);

        long appPid = ProcessHandle.current().pid();
        String exePath = ProcessHandle.current().info().command().orElse("");
        boolean canRelaunch = !exePath.isBlank()
            && exePath.toLowerCase().endsWith(".exe")
            && !exePath.toLowerCase().contains("java");
        if (!canRelaunch) {
            writePrepareLog(logFile, msiAbs, appPid, exePath, "INVALID_EXE");
            throw new IOException("目前不是以已安裝的 RQTracker.exe 執行，無法自動更新。");
        }

        writePrepareLog(logFile, msiAbs, appPid, exePath, "PREPARE");

        Path psFile = tmpDir.resolve("rq-update-worker.ps1");
        Files.writeString(psFile, buildWorkerScript(msiAbs, flagFile, logFile, exePath, appPid),
            StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            "Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','"
                + psEscape(psFile.toAbsolutePath().toString()) + "') -Verb RunAs"
        ).start();

        try {
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                int exit = process.exitValue();
                appendLog(logFile, "LAUNCHER exitCode=" + exit);
                if (exit != 0) {
                    throw new IOException("提權安裝啟動器失敗，exitCode=" + exit);
                }
            } else {
                appendLog(logFile, "LAUNCHER still waiting for UAC response");
                process.destroyForcibly();
                throw new IOException("未在 10 秒內完成 Windows 授權確認，請重新點選立即安裝。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("提權安裝啟動器被中斷", e);
        }
    }

    public static void exitForUpdate() {
        javafx.application.Platform.exit();
        System.exit(0);
    }

    private static String buildWorkerScript(Path msi, Path flagFile, Path logFile,
                                            String exePath, long appPid) {
        StringBuilder ps = new StringBuilder();
        ps.append("$ErrorActionPreference = 'Stop'\r\n");
        ps.append("$log = '").append(psEscape(logFile.toString())).append("'\r\n");
        ps.append("function Log($m) { \"$(Get-Date -f 'HH:mm:ss') $m\" | Add-Content $log -Encoding UTF8 }\r\n");
        ps.append("try {\r\n");
        ps.append("    Log '[STARTED] worker running as admin'\r\n");
        ps.append("    Set-Content -Path '").append(psEscape(flagFile.toString())).append("' -Value \"started $(Get-Date -Format o)\" -Encoding UTF8\r\n");
        ps.append("    $msi = '").append(psEscape(msi.toString())).append("'\r\n");
        ps.append("    Log \"[INFO] msi=$msi\"\r\n");
        ps.append("    Log \"[INFO] exists=$(Test-Path $msi)\"\r\n");
        ps.append("    Log '[INFO] waiting briefly for app shutdown'\r\n");
        ps.append("    Start-Sleep -Seconds 4\r\n");
        ps.append("    $exe = '").append(psEscape(exePath)).append("'\r\n");
        ps.append("    Get-Process RQTracker -ErrorAction SilentlyContinue | ForEach-Object {\r\n");
        ps.append("        try {\r\n");
        ps.append("            if ($_.Path -eq $exe) {\r\n");
        ps.append("                Log \"[INFO] stopping stale RQTracker pid=$($_.Id)\"\r\n");
        ps.append("                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue\r\n");
        ps.append("            }\r\n");
        ps.append("        } catch { Log \"[WARN] stop stale app failed: $($_.Exception.Message)\" }\r\n");
        ps.append("    }\r\n");
        ps.append("    Start-Sleep -Seconds 2\r\n");
        ps.append("    $msiLog = Join-Path $env:TEMP 'rq-update-msi.log'\r\n");
        ps.append("    Log \"[INFO] starting msiexec log=$msiLog\"\r\n");
        ps.append("    $proc = Start-Process \"$env:SystemRoot\\System32\\msiexec.exe\" -ArgumentList @('/i', $msi, '/qn', '/norestart', '/L*v', $msiLog) -Wait -PassThru\r\n");
        ps.append("    Log \"[INFO] msiexec exitCode=$($proc.ExitCode)\"\r\n");
        ps.append("    if ($proc -ne $null -and $proc.ExitCode -eq 0) {\r\n");
        ps.append("        Start-Sleep -Seconds 3\r\n");
        ps.append("        Start-Process '").append(psEscape(exePath)).append("'\r\n");
        ps.append("        Log '[INFO] relaunched'\r\n");
        ps.append("    }\r\n");
        ps.append("    Log '[END] done'\r\n");
        ps.append("} catch {\r\n");
        ps.append("    Log \"[ERROR] $($_.Exception.Message)\"\r\n");
        ps.append("    exit 1\r\n");
        ps.append("}\r\n");
        return ps.toString();
    }

    private static void writePrepareLog(Path logFile, Path msi, long appPid,
                                        String exePath, String status) throws IOException {
        String content = ""
            + java.time.LocalDateTime.now() + " [" + status + "] launcher preparing\r\n"
            + "msi=" + msi + "\r\n"
            + "msiExists=" + Files.exists(msi) + "\r\n"
            + "msiSize=" + (Files.exists(msi) ? Files.size(msi) : -1) + "\r\n"
            + "appPid=" + appPid + "\r\n"
            + "exePath=" + exePath + "\r\n";
        Files.writeString(logFile, content, StandardCharsets.UTF_8);
    }

    private static void appendLog(Path logFile, String message) throws IOException {
        String line = java.time.LocalDateTime.now() + " " + message + "\r\n";
        Files.writeString(logFile, line, StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
    }

    private static String psEscape(String value) {
        return value.replace("'", "''");
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
