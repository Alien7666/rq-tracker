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
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path psOut = tmpDir.resolve("rq-update-ps.txt");
        Path vbsFile = tmpDir.resolve("rq-update-elevate.vbs");

        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append("chcp 65001 > nul\r\n");
        sb.append("set \"MSI=").append(msi.toString()).append("\"\r\n");
        sb.append("set \"PID=").append(pid).append("\"\r\n");
        sb.append("set \"EXE=").append(exePath).append("\"\r\n");
        sb.append("set \"LOG=").append(msiLog.toString()).append("\"\r\n");
        sb.append("set \"RLOG=").append(runnerLog.toString()).append("\"\r\n");
        sb.append("set \"PSOUT=").append(psOut.toString()).append("\"\r\n");
        sb.append("set \"VBS=").append(vbsFile.toString()).append("\"\r\n");
        sb.append("\r\n");
        sb.append("call :LOG step01 runner started\r\n");
        // 不再輪詢 tasklist：3 秒倒數已確保 Java 退出，再多等 8 秒做緩衝即可。
        sb.append("call :LOG step02 sleeping 8s buffer\r\n");
        sb.append("timeout /t 8 /nobreak > nul\r\n");
        sb.append("call :LOG step03 attempting elevation method 1: powershell Start-Process -Verb RunAs\r\n");
        sb.append("\r\n");
        // 第 1 種提權：PowerShell -Verb RunAs；輸出寫到 PSOUT 以便診斷
        sb.append("powershell -NoProfile -ExecutionPolicy Bypass -Command \"try { $p = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i','%MSI%','/qb!','/norestart','/L*v','%LOG%') -Verb RunAs -Wait -PassThru -ErrorAction Stop; Write-Output ('EXIT:' + $p.ExitCode); exit $p.ExitCode } catch { Write-Output ('ERR:' + $_.Exception.Message); exit 1223 }\" > \"%PSOUT%\" 2>&1\r\n");
        sb.append("set \"RC=%errorlevel%\"\r\n");
        sb.append("call :LOG step04 powershell exit=%RC% (see PSOUT)\r\n");
        sb.append("type \"%PSOUT%\" >> \"%RLOG%\" 2>nul\r\n");
        sb.append("\r\n");
        sb.append("if \"%RC%\"==\"0\" goto ok\r\n");
        sb.append("if \"%RC%\"==\"3010\" goto ok\r\n");
        // 1602/1223 = 使用者主動取消 UAC，不嘗試 fallback
        sb.append("if \"%RC%\"==\"1602\" goto cancel\r\n");
        sb.append("if \"%RC%\"==\"1223\" goto try_mshta\r\n");
        sb.append("goto try_mshta\r\n");
        sb.append("\r\n");
        // 第 2 種提權：mshta + VBScript ShellExecute "runas"（Windows 官方 ShellExecuteEx API，最相容）
        sb.append(":try_mshta\r\n");
        sb.append("call :LOG step05 attempting elevation method 2: mshta vbscript ShellExecute runas\r\n");
        sb.append("> \"%VBS%\" echo Set sh = CreateObject(\"Shell.Application\")\r\n");
        sb.append(">> \"%VBS%\" echo sh.ShellExecute \"msiexec.exe\", \"/i \"\"%MSI%\"\" /qb! /norestart /L*v \"\"%LOG%\"\"\", \"\", \"runas\", 1\r\n");
        sb.append("cscript //nologo //B \"%VBS%\"\r\n");
        sb.append("set \"RC2=%errorlevel%\"\r\n");
        sb.append("call :LOG step06 cscript exit=%RC2% (mshta/runas dispatched, msiexec running detached)\r\n");
        // ShellExecute 是非同步的，要靠 msiexec log 的內容判斷是否完成
        sb.append("call :LOG step07 polling msiexec log for completion (max 120s)\r\n");
        sb.append("set /a WAIT=0\r\n");
        sb.append(":wait_msi\r\n");
        sb.append("if not exist \"%LOG%\" goto wait_more\r\n");
        sb.append("findstr /C:\"=== Logging stopped\" \"%LOG%\" > nul 2>&1\r\n");
        sb.append("if not errorlevel 1 goto msi_done\r\n");
        sb.append(":wait_more\r\n");
        sb.append("set /a WAIT+=1\r\n");
        sb.append("if %WAIT% GEQ 120 goto msi_timeout\r\n");
        sb.append("timeout /t 1 /nobreak > nul\r\n");
        sb.append("goto wait_msi\r\n");
        sb.append("\r\n");
        sb.append(":msi_done\r\n");
        sb.append("call :LOG step08 msiexec log indicates completion (waited=%WAIT%s)\r\n");
        sb.append("goto ok\r\n");
        sb.append("\r\n");
        sb.append(":msi_timeout\r\n");
        sb.append("call :LOG step08 TIMEOUT waiting for msiexec log\r\n");
        sb.append("set \"RC=9999\"\r\n");
        sb.append("goto fail\r\n");
        sb.append("\r\n");
        sb.append(":ok\r\n");
        sb.append("call :LOG step09 SUCCESS, relaunching EXE\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("mshta \"javascript:var sh=new ActiveXObject('WScript.Shell');sh.Popup('RQ Tracker 更新完成，新版已啟動。',3,'更新成功',64);close()\"\r\n");
        sb.append("exit /b 0\r\n");
        sb.append("\r\n");
        sb.append(":cancel\r\n");
        sb.append("call :LOG step09 CANCELLED by user\r\n");
        sb.append("mshta \"javascript:var sh=new ActiveXObject('WScript.Shell');sh.Popup('您取消了 Windows 授權，更新未進行。\\n舊版 RQ Tracker 將重新啟動。',0,'更新取消',48);close()\"\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("exit /b 0\r\n");
        sb.append("\r\n");
        sb.append(":fail\r\n");
        sb.append("call :LOG step99 FAIL rc=%RC%\r\n");
        sb.append("mshta \"javascript:var sh=new ActiveXObject('WScript.Shell');sh.Popup('RQ Tracker 更新失敗 (代碼 %RC%)\\n\\n詳細記錄：%LOG%\\n執行記錄：%RLOG%',0,'更新失敗',16);close()\"\r\n");
        sb.append("start \"\" \"%EXE%\"\r\n");
        sb.append("exit /b %RC%\r\n");
        sb.append("\r\n");
        sb.append(":LOG\r\n");
        sb.append(">> \"%RLOG%\" echo [%DATE% %TIME%] %*\r\n");
        sb.append("goto :eof\r\n");
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
