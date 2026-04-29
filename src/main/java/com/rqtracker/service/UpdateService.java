package com.rqtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private static final String VBS_SCRIPT_NAME = "rq-update-runner.vbs";
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
     * 啟動 VBScript runner 完成更新流程。
     *
     * 關鍵：用 schtasks (Windows 工作排程器) 跑 wscript，不直接 spawn。
     * 原因：jpackage 產生的 RQTracker.exe launcher 把 JVM 放進 Windows Job Object；
     * Java 直接 spawn 的任何子程序（cmd / wscript / powershell）都會被同一個 Job 連坐殺掉。
     * Task Scheduler 在自己的 service context 執行新工作，完全脫離我們的 Job → wscript 安全存活。
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
        String exeName = Path.of(exePath).getFileName().toString();

        long pid = ProcessHandle.current().pid();
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path script = tmpDir.resolve(VBS_SCRIPT_NAME);
        Path msiLog = tmpDir.resolve(UPDATE_MSI_LOG_NAME);
        Path runLog = tmpDir.resolve(UPDATE_LOG_NAME);

        String taskName = "RQTrackerUpdater_" + System.currentTimeMillis();
        String content = buildVbsScript(msiAbs, pid, exePath, exeName, msiLog, runLog, taskName);

        // VBScript 必須以 UTF-16 LE + BOM 寫出，wscript 才會正確解析中文
        byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(body.length + 2);
        baos.write(0xFF);
        baos.write(0xFE);
        baos.write(body, 0, body.length);
        Files.write(script, baos.toByteArray());

        // Java 端起始記錄
        Files.writeString(runLog,
            "[" + java.time.LocalDateTime.now() + "] java scheduling task " + taskName
                + " to run wscript on: " + script + System.lineSeparator(),
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        // /TR 必須是單一字串：wscript.exe //B //Nologo "C:\path\script.vbs"
        String trCmd = "wscript.exe //B //Nologo \"" + script.toString() + "\"";

        // 排程時間隨便填一個未來時間（5 分鐘後），實際靠下一步 /Run 立即觸發
        String startTime = java.time.LocalTime.now()
            .plusMinutes(5)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        try {
            // 1) 建立一次性工作（current user，無需 admin）
            Process create = new ProcessBuilder(
                "schtasks.exe", "/Create",
                "/TN", taskName,
                "/TR", trCmd,
                "/SC", "ONCE",
                "/ST", startTime,
                "/F"
            ).redirectErrorStream(true).start();
            create.waitFor();

            // 2) 立即觸發
            new ProcessBuilder("schtasks.exe", "/Run", "/TN", taskName).start();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("排程更新工作時被中斷", ie);
        }
    }

    public static void exitForUpdate() {
        javafx.application.Platform.exit();
        System.exit(0);
    }

    /**
     * 產生 VBScript runner 內容；所有路徑與 PID 直接內嵌，不靠命令列引數。
     */
    static String buildVbsScript(Path msi, long pid, String exePath, String exeName,
                                 Path msiLog, Path runLog, String taskName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Option Explicit\r\n");
        sb.append("Const MSI = \"").append(vbsEscape(msi.toString())).append("\"\r\n");
        sb.append("Const PID = ").append(pid).append("\r\n");
        sb.append("Const EXE = \"").append(vbsEscape(exePath)).append("\"\r\n");
        sb.append("Const EXENAME = \"").append(vbsEscape(exeName)).append("\"\r\n");
        sb.append("Const MSILOG = \"").append(vbsEscape(msiLog.toString())).append("\"\r\n");
        sb.append("Const RUNLOG = \"").append(vbsEscape(runLog.toString())).append("\"\r\n");
        sb.append("Const TASKNAME = \"").append(vbsEscape(taskName)).append("\"\r\n");
        sb.append("\r\n");
        sb.append("Dim sh, fso, wmi, procs, t, started\r\n");
        sb.append("Set sh  = CreateObject(\"WScript.Shell\")\r\n");
        sb.append("Set fso = CreateObject(\"Scripting.FileSystemObject\")\r\n");
        sb.append("\r\n");
        sb.append("Sub LogLine(msg)\r\n");
        sb.append("    On Error Resume Next\r\n");
        sb.append("    Dim f\r\n");
        sb.append("    Set f = fso.OpenTextFile(RUNLOG, 8, True)\r\n");
        sb.append("    f.WriteLine \"[\" & Now & \"] \" & msg\r\n");
        sb.append("    f.Close\r\n");
        sb.append("    On Error Goto 0\r\n");
        sb.append("End Sub\r\n");
        sb.append("\r\n");
        sb.append("LogLine \"step01 vbs runner started, pid=\" & PID & \" exe=\" & EXENAME & \" task=\" & TASKNAME\r\n");
        sb.append("\r\n");
        // WMI 連線（包進 On Error，失敗也能有 fallback）
        sb.append("On Error Resume Next\r\n");
        sb.append("Set wmi = GetObject(\"winmgmts:\\\\.\\root\\cimv2\")\r\n");
        sb.append("If Err.Number <> 0 Then\r\n");
        sb.append("    LogLine \"step01b WMI unavailable: \" & Err.Number & \" \" & Err.Description & \" — using fixed sleep instead\"\r\n");
        sb.append("    Err.Clear\r\n");
        sb.append("    Set wmi = Nothing\r\n");
        sb.append("End If\r\n");
        sb.append("On Error Goto 0\r\n");
        sb.append("\r\n");
        // 等主程式真正退出
        sb.append("LogLine \"step01c entering wait loop for main pid=\" & PID\r\n");
        sb.append("If wmi Is Nothing Then\r\n");
        sb.append("    WScript.Sleep 5000\r\n");
        sb.append("    t = 0\r\n");
        sb.append("Else\r\n");
        sb.append("    For t = 1 To 30\r\n");
        sb.append("        On Error Resume Next\r\n");
        sb.append("        Set procs = wmi.ExecQuery(\"SELECT * FROM Win32_Process WHERE ProcessId=\" & PID & \" AND Name='\" & EXENAME & \"'\")\r\n");
        sb.append("        Dim cnt\r\n");
        sb.append("        cnt = -1\r\n");
        sb.append("        If Err.Number = 0 Then cnt = procs.Count\r\n");
        sb.append("        On Error Goto 0\r\n");
        sb.append("        If cnt = 0 Then Exit For\r\n");
        sb.append("        WScript.Sleep 1000\r\n");
        sb.append("    Next\r\n");
        sb.append("End If\r\n");
        sb.append("LogLine \"step02 main app gone (after \" & t & \"s wait)\"\r\n");
        sb.append("WScript.Sleep 2000\r\n");
        sb.append("\r\n");
        // 觸發 UAC + msiexec
        sb.append("LogLine \"step03 invoking ShellExecute runas msiexec\"\r\n");
        sb.append("Dim shellApp\r\n");
        sb.append("Set shellApp = CreateObject(\"Shell.Application\")\r\n");
        sb.append("On Error Resume Next\r\n");
        sb.append("shellApp.ShellExecute \"msiexec.exe\", \"/i \"\"\" & MSI & \"\"\" /qb! /norestart /L*v \"\"\" & MSILOG & \"\"\"\", \"\", \"runas\", 1\r\n");
        sb.append("If Err.Number <> 0 Then\r\n");
        sb.append("    LogLine \"step03 ShellExecute FAILED: \" & Err.Number & \" \" & Err.Description\r\n");
        sb.append("    MsgBox \"RQ Tracker 更新失敗：無法觸發 Windows 授權視窗。\" & vbCrLf & \"錯誤：\" & Err.Number & \" \" & Err.Description, vbCritical, \"更新失敗\"\r\n");
        sb.append("    sh.Run \"\"\"\" & EXE & \"\"\"\", 1, False\r\n");
        sb.append("    WScript.Quit 1\r\n");
        sb.append("End If\r\n");
        sb.append("On Error Goto 0\r\n");
        sb.append("\r\n");
        // 等 msiexec 出現
        sb.append("LogLine \"step04 waiting for msiexec.exe to appear (max 30s)\"\r\n");
        sb.append("started = False\r\n");
        sb.append("If wmi Is Nothing Then\r\n");
        sb.append("    WScript.Sleep 3000\r\n");
        sb.append("    started = True\r\n");
        sb.append("Else\r\n");
        sb.append("    For t = 1 To 30\r\n");
        sb.append("        On Error Resume Next\r\n");
        sb.append("        Set procs = wmi.ExecQuery(\"SELECT * FROM Win32_Process WHERE Name='msiexec.exe'\")\r\n");
        sb.append("        Dim cnt2\r\n");
        sb.append("        cnt2 = 0\r\n");
        sb.append("        If Err.Number = 0 Then cnt2 = procs.Count\r\n");
        sb.append("        On Error Goto 0\r\n");
        sb.append("        If cnt2 > 0 Then\r\n");
        sb.append("            started = True\r\n");
        sb.append("            Exit For\r\n");
        sb.append("        End If\r\n");
        sb.append("        WScript.Sleep 1000\r\n");
        sb.append("    Next\r\n");
        sb.append("End If\r\n");
        sb.append("\r\n");
        sb.append("If Not started Then\r\n");
        sb.append("    LogLine \"step04 msiexec never appeared (likely UAC cancel)\"\r\n");
        sb.append("    MsgBox \"您取消了 Windows 授權，更新未進行。\" & vbCrLf & \"舊版 RQ Tracker 將重新啟動。\", vbInformation, \"更新取消\"\r\n");
        sb.append("    sh.Run \"\"\"\" & EXE & \"\"\"\", 1, False\r\n");
        sb.append("    Call CleanupTask\r\n");
        sb.append("    WScript.Quit 0\r\n");
        sb.append("End If\r\n");
        sb.append("\r\n");
        // 等 msiexec 結束（最多 5 分鐘）
        sb.append("LogLine \"step05 msiexec started, waiting for completion (max 300s)\"\r\n");
        sb.append("If wmi Is Nothing Then\r\n");
        sb.append("    WScript.Sleep 30000\r\n");
        sb.append("Else\r\n");
        sb.append("    For t = 1 To 300\r\n");
        sb.append("        On Error Resume Next\r\n");
        sb.append("        Set procs = wmi.ExecQuery(\"SELECT * FROM Win32_Process WHERE Name='msiexec.exe'\")\r\n");
        sb.append("        Dim cnt3\r\n");
        sb.append("        cnt3 = 0\r\n");
        sb.append("        If Err.Number = 0 Then cnt3 = procs.Count\r\n");
        sb.append("        On Error Goto 0\r\n");
        sb.append("        If cnt3 = 0 Then Exit For\r\n");
        sb.append("        WScript.Sleep 1000\r\n");
        sb.append("    Next\r\n");
        sb.append("End If\r\n");
        sb.append("LogLine \"step06 msiexec completed (after \" & t & \"s)\"\r\n");
        sb.append("\r\n");
        // 檢查並啟動新版 EXE
        sb.append("If Not fso.FileExists(EXE) Then\r\n");
        sb.append("    LogLine \"step07 EXE missing after install: \" & EXE\r\n");
        sb.append("    MsgBox \"RQ Tracker 更新可能失敗：找不到新版執行檔。\" & vbCrLf & \"請查看安裝記錄：\" & MSILOG, vbExclamation, \"更新異常\"\r\n");
        sb.append("    Call CleanupTask\r\n");
        sb.append("    WScript.Quit 1\r\n");
        sb.append("End If\r\n");
        sb.append("LogLine \"step08 launching new EXE: \" & EXE\r\n");
        sb.append("sh.Run \"\"\"\" & EXE & \"\"\"\", 1, False\r\n");
        sb.append("LogLine \"step09 done\"\r\n");
        sb.append("Call CleanupTask\r\n");
        sb.append("WScript.Quit 0\r\n");
        sb.append("\r\n");
        // 自我刪除 schtasks 工作（避免堆積）
        sb.append("Sub CleanupTask\r\n");
        sb.append("    On Error Resume Next\r\n");
        sb.append("    sh.Run \"schtasks /Delete /TN \"\"\" & TASKNAME & \"\"\" /F\", 0, False\r\n");
        sb.append("    On Error Goto 0\r\n");
        sb.append("End Sub\r\n");
        return sb.toString();
    }

    private static String vbsEscape(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
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
