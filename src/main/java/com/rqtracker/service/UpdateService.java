package com.rqtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
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
    private static final String CMD_SCRIPT_NAME = "rq-update-runner.cmd";
    private static final String UPDATE_LOG_NAME = "rq-update-log.txt";
    private static final String UPDATE_MSI_LOG_NAME = "rq-update-msi.log";

    public record UpdateInfo(String version, String releaseNotes, String downloadUrl, String publishedAt) {}

    private UpdateService() {}

    // ── 版本檢查 ──────────────────────────────────────────────────────────────

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

    // ── 安裝啟動 ──────────────────────────────────────────────────────────────

    /**
     * 啟動 detached cmd 接力腳本：等主程式退出 → msiexec 提權安裝 → 重啟新版。
     * 呼叫端應在短暫倒數後 {@link #exitForUpdate()} 結束 JVM。
     */
    public static void launchInstaller(Path msi) throws IOException {
        if (msi == null) {
            throw new IOException("尚未下載安裝檔，請重新下載更新。");
        }
        Path msiAbs = msi.toAbsolutePath();
        if (!Files.isRegularFile(msiAbs) || Files.size(msiAbs) <= 0) {
            throw new IOException("找不到有效的 MSI 安裝檔：" + msiAbs);
        }

        String exePath = ProcessHandle.current().info().command().orElse("");
        boolean canRelaunch = !exePath.isBlank()
            && exePath.toLowerCase().endsWith(".exe")
            && !exePath.toLowerCase().contains("java");
        if (!canRelaunch) {
            throw new IOException("目前不是以已安裝的 RQTracker.exe 執行，無法自動更新。");
        }

        long pid = ProcessHandle.current().pid();
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path script = tmpDir.resolve(CMD_SCRIPT_NAME);
        Path msiLog = tmpDir.resolve(UPDATE_MSI_LOG_NAME);
        Path runnerLog = tmpDir.resolve(UPDATE_LOG_NAME);

        // 把 PID/MSI/EXE 直接寫死在腳本內，避免 cmd /c 多層引號傳遞造成的 strip-quote bug。
        String content = buildCmdScript(msiAbs, pid, exePath, msiLog, runnerLog);
        Files.writeString(script, content, Charset.defaultCharset());

        // 在 Java 端先寫一行起始訊息到 runnerLog；若之後 log 沒有 "runner started"，代表 cmd 根本沒被 spawn。
        Files.writeString(runnerLog,
            "[" + java.time.LocalDateTime.now() + "] java spawning runner: " + script
                + System.lineSeparator(),
            Charset.defaultCharset(),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        // cmd /c start "title" /min "script.cmd"
        // 只有一個帶引號參數，沒有 cmd /c 串接的 quote-strip 問題。
        new ProcessBuilder(
            "cmd.exe", "/c", "start", "RQ Tracker 更新", "/min",
            script.toString()
        ).start();
    }

    public static void exitForUpdate() {
        javafx.application.Platform.exit();
        System.exit(0);
    }

    /**
     * 產生 cmd 接力腳本內容。msiLog/runnerLog 路徑會直接內嵌；MSI、PID、EXE 透過 %~1 %~2 %~3 傳入。
     * 不在 Java 端拼接路徑就能避開引號 escape 的 corner case。
     */
    static String buildCmdScript(Path msi, long pid, String exePath, Path msiLog, Path runnerLog) {
        String exeName = Path.of(exePath).getFileName().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append("chcp 65001 > nul\r\n");
        sb.append("set \"MSI=").append(msi.toString()).append("\"\r\n");
        sb.append("set \"PID=").append(pid).append("\"\r\n");
        sb.append("set \"EXE=").append(exePath).append("\"\r\n");
        sb.append("set \"EXENAME=").append(exeName).append("\"\r\n");
        sb.append("set \"LOG=").append(msiLog.toString()).append("\"\r\n");
        sb.append("set \"RLOG=").append(runnerLog.toString()).append("\"\r\n");
        sb.append(">> \"%RLOG%\" echo [%DATE% %TIME%] runner started PID=%PID% EXE=%EXENAME% MSI=%MSI%\r\n");
        sb.append("\r\n");
        // 等候條件：PID + IMAGENAME 同時匹配（避開 PID 回收造成的誤判）；最多等 15 秒
        sb.append("set /a WAITS=0\r\n");
        sb.append(":wait_exit\r\n");
        sb.append("tasklist /FI \"PID eq %PID%\" /FI \"IMAGENAME eq %EXENAME%\" 2>nul | findstr /I /C:\"%EXENAME%\" > nul\r\n");
        sb.append("if errorlevel 1 goto exited\r\n");
        sb.append("set /a WAITS+=1\r\n");
        sb.append("if %WAITS% GEQ 15 goto exited\r\n");
        sb.append("timeout /t 1 /nobreak > nul\r\n");
        sb.append("goto wait_exit\r\n");
        sb.append(":exited\r\n");
        sb.append(">> \"%RLOG%\" echo [%DATE% %TIME%] main process exited (waited=%WAITS%s), launching msiexec via UAC\r\n");
        sb.append("\r\n");
        // PowerShell 一行命令：透過 ShellExecute -Verb RunAs 觸發 UAC，等候 msiexec 結束並回傳 exit code
        sb.append("powershell -NoProfile -ExecutionPolicy Bypass -Command \"$ErrorActionPreference='Stop'; try { $p = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', '%MSI%', '/qb!', '/norestart', '/L*v', '%LOG%') -Verb RunAs -Wait -PassThru; exit $p.ExitCode } catch { exit 1223 }\"\r\n");
        sb.append("set \"RC=%errorlevel%\"\r\n");
        sb.append(">> \"%RLOG%\" echo [%DATE% %TIME%] msiexec exit=%RC%\r\n");
        sb.append("\r\n");
        sb.append("if \"%RC%\"==\"0\" goto ok\r\n");
        sb.append("if \"%RC%\"==\"3010\" goto ok\r\n");
        sb.append("if \"%RC%\"==\"1602\" goto cancel\r\n");
        sb.append("if \"%RC%\"==\"1223\" goto cancel\r\n");
        sb.append("\r\n");
        sb.append(":fail\r\n");
        sb.append("mshta \"javascript:var sh=new ActiveXObject('WScript.Shell');sh.Popup('RQ Tracker 更新失敗 (代碼 %RC%)\\n\\n詳細記錄：%LOG%',0,'更新失敗',16);close()\"\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("exit /b %RC%\r\n");
        sb.append("\r\n");
        sb.append(":cancel\r\n");
        sb.append("mshta \"javascript:var sh=new ActiveXObject('WScript.Shell');sh.Popup('您取消了 Windows 授權，更新未進行。\\n舊版 RQ Tracker 將重新啟動。',0,'更新取消',48);close()\"\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("exit /b 0\r\n");
        sb.append("\r\n");
        sb.append(":ok\r\n");
        sb.append(">> \"%RLOG%\" echo [%DATE% %TIME%] install OK, relaunching %EXE%\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("exit /b 0\r\n");
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    // ── 解析 ──────────────────────────────────────────────────────────────────

    private static UpdateInfo parseGitHub(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String tag = root.path("tag_name").asText("").replaceFirst("^v", "");
            String notes = root.path("body").asText("");
            String pubRaw = root.path("published_at").asText("");
            String pub = pubRaw.substring(0, Math.min(10, pubRaw.length()));
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
