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
     */
    public static Optional<UpdateInfo> checkForUpdate(String url, String currentVersion) {
        if (url == null || url.isBlank()) return Optional.empty();
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "RQTracker/" + currentVersion)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();

            UpdateInfo info = url.contains("api.github.com")
                ? parseGitHub(resp.body())
                : parseCustomJson(resp.body());

            if (info == null) return Optional.empty();
            return isNewer(info.version(), currentVersion) ? Optional.of(info) : Optional.empty();
        } catch (Exception e) {
            LOG.warning("更新檢查失敗：" + e.getMessage());
            return Optional.empty();
        }
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
     * 啟動 MSI 安裝程式後退出 JVM。
     * 採用 /passive 模式（顯示進度條但不需使用者點擊）。
     */
    public static void launchInstaller(Path msi) throws IOException {
        new ProcessBuilder("msiexec", "/i", msi.toAbsolutePath().toString(), "/passive")
            .start();
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
