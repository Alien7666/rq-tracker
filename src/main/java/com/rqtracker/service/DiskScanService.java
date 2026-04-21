package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;
import com.rqtracker.model.TaskResult;
import com.rqtracker.model.TaskResult.ScanState;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.PathUtils;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 磁碟檔案掃描服務（對應 HTML 的 scanRQFiles / checkFileExists / checkFolderHasFiles）。
 *
 * Java 版優勢：直接讀 AppConfig 中的路徑，無需每次彈出資料夾授權視窗。
 * 掃描在背景執行緒執行，結果透過呼叫方的 Platform.runLater 回到 UI 執行緒。
 */
public class DiskScanService {

    private static final Logger LOG = Logger.getLogger(DiskScanService.class.getName());

    private final AppConfig appConfig;

    /** 最近一次掃描的結果快取，key = taskKey */
    private final Map<String, TaskResult> cache = new ConcurrentHashMap<>();

    public DiskScanService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    // ── 快取存取 ──────────────────────────────────────────────────────────────

    public TaskResult getCached(String key) {
        return cache.getOrDefault(key, TaskResult.unknown());
    }

    /** 取得不可修改的快取副本（傳給 RQContentPanel 渲染用） */
    public Map<String, TaskResult> getCacheCopy() {
        return Map.copyOf(cache);
    }

    public void clearCache() { cache.clear(); }

    // ── 主掃描方法（在背景執行緒呼叫）───────────────────────────────────────

    /**
     * 掃描指定 RQ 的所有有資料夾的任務。
     * 此方法同步執行，應在背景執行緒呼叫。
     *
     * @return 任務 key → 掃描結果的 Map
     */
    public Map<String, TaskResult> scan(RQData rq) {
        if (rq == null) return Map.of();

        String dlRoot  = appConfig.getDownloadsRoot();
        String svnRoot = appConfig.getSvnRoot();

        List<TaskDef> tasks = collectScanTasks(rq, dlRoot, svnRoot);

        Map<String, TaskResult> results = new LinkedHashMap<>();
        for (TaskDef t : tasks) {
            TaskResult result;
            try {
                if (t.isCheckHasFiles()) {
                    result = checkCodeFolder(t, rq);
                } else {
                    Long sinceMs = t.isCheckModTime() && rq.getCreatedAt() != null
                        ? parseIsoMillis(rq.getCreatedAt()) : null;
                    result = checkFileExists(t.getFolder(), t.getFilename(), sinceMs);
                }
            } catch (Exception e) {
                LOG.warning("掃描 " + t.getKey() + " 失敗：" + e.getMessage());
                result = TaskResult.unknown();
            }
            results.put(t.getKey(), result);
            cache.put(t.getKey(), result);
        }
        return results;
    }

    /**
     * 自動打勾：state=FILE 且尚未勾選 → 勾選並記錄時間戳。
     *
     * @return 是否有任何任務被自動打勾（需要通知 UI 刷新）
     */
    public boolean autoCheck(RQData rq, Map<String, TaskResult> results) {
        boolean anyChecked = false;
        for (Map.Entry<String, TaskResult> e : results.entrySet()) {
            if (e.getValue().state() == ScanState.FILE && !rq.isChecked(e.getKey())) {
                rq.check(e.getKey(), DateTimeUtils.nowZhTW());
                anyChecked = true;
            }
        }
        return anyChecked;
    }

    // ── checkFileExists（對應 HTML 的 checkFileExists）──────────────────────

    /**
     * 檢查指定資料夾中的檔案是否存在。
     *
     * @param folderPath 資料夾絕對路徑
     * @param filename   檔案名（可含通配符 \d{8} / Vx.x，多檔用 + 分隔）；null 表示只檢查資料夾
     * @param sinceMs    若不為 null，檢查檔案修改時間是否 > sinceMs（checkModTime 模式）
     */
    public TaskResult checkFileExists(String folderPath, String filename, Long sinceMs) {
        if (folderPath == null) return TaskResult.unknown();
        Path dir = Paths.get(folderPath);
        if (!Files.isDirectory(dir)) return TaskResult.none();
        if (filename == null || filename.isBlank()) return TaskResult.folder();

        // 以 + 或 / 分隔多個檔名
        List<String> files = Arrays.stream(filename.split("\\s*[+]\\s*"))
            .map(String::trim).filter(s -> !s.isBlank()).toList();

        // sinceMs 模式（checkModTime）：只針對單一檔名
        if (sinceMs != null && files.size() == 1) {
            long[] res = checkSingleFileWithTime(dir, files.get(0));
            if (res[0] == 0) return new TaskResult(ScanState.FOLDER, 0, 1);
            return res[1] > sinceMs ? TaskResult.file(1, 1) : TaskResult.stale();
        }

        int found = 0;
        for (String f : files) {
            if (checkSingleFile(dir, f)) found++;
        }
        int total = files.size();
        if (found == total) return TaskResult.file(found, total);
        if (found > 0)      return TaskResult.partial(found, total);
        return new TaskResult(ScanState.FOLDER, 0, total);
    }

    // ── checkCodeFolder（對應 HTML 的 checkHasFiles 分支）───────────────────

    /**
     * 檢查程式碼交付任務（del_code）。
     * 邏輯：在 codeRoot 下找 {date}_{rqNum} 目錄 → 進入 {vid} → 比對 versionFiles 清單。
     */
    public TaskResult checkCodeFolder(TaskDef task, RQData rq) {
        String codeRoot = task.getCodeRoot();
        if (codeRoot == null) return TaskResult.unknown();

        Path root = Paths.get(codeRoot);
        if (!Files.isDirectory(root)) return TaskResult.none();

        String rqNum  = PathUtils.rqNumber(rq.getId());
        Pattern zipPat = Pattern.compile(
            "^\\d{8}_" + Pattern.quote(rqNum) + "$", Pattern.CASE_INSENSITIVE);

        // 找 {date}_{rqNum} 子目錄
        String zipDirName = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) &&
                    zipPat.matcher(entry.getFileName().toString()).matches()) {
                    zipDirName = entry.getFileName().toString();
                    break;
                }
            }
        } catch (IOException e) {
            return TaskResult.unknown();
        }

        if (zipDirName == null) return TaskResult.none();

        String vid   = task.getCodeVid();
        Path vidDir  = root.resolve(zipDirName).resolve(vid);
        if (!Files.isDirectory(vidDir)) return new TaskResult(ScanState.FOLDER, 0, 0);

        // 比對 versionFiles 清單
        Integer vIdx    = task.getCodeVidIdx();
        String rawFiles = vIdx != null ? rq.getVersionFilesText(vIdx) : null;

        if (rawFiles != null && !rawFiles.isBlank()) {
            List<String> fileList = Arrays.stream(rawFiles.split("\n"))
                .map(l -> l.trim().replace('/', '\\'))
                .filter(l -> !l.isBlank())
                .toList();
            int found = 0;
            for (String filePath : fileList) {
                int lastSep  = filePath.lastIndexOf('\\');
                String dPart = lastSep >= 0 ? filePath.substring(0, lastSep) : "";
                String fName = lastSep >= 0 ? filePath.substring(lastSep + 1) : filePath;
                Path   fDir  = dPart.isBlank() ? vidDir : vidDir.resolve(dPart);
                if (Files.exists(fDir.resolve(fName))) found++;
            }
            int total = fileList.size();
            return found == total ? TaskResult.file(found, total)
                                  : new TaskResult(ScanState.FOLDER, found, total);
        } else {
            // 沒有填改動程式清單：資料夾有任何檔案即算
            return checkFolderHasFiles(vidDir)
                ? TaskResult.file(1, 1)
                : new TaskResult(ScanState.FOLDER, 0, 0);
        }
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private List<TaskDef> collectScanTasks(RQData rq, String dlRoot, String svnRoot) {
        List<TaskDef> all = new ArrayList<>();
        all.addAll(TaskFactory.sharedFlowTasks());
        if (rq.getVersions() != null) {
            for (int i = 0; i < rq.getVersions().size(); i++) {
                String vName = rq.getVersions().get(i).getName();
                all.addAll(TaskFactory.versionDevTasks(i));
                all.addAll(TaskFactory.versionDeliverables(i, rq, vName, dlRoot));
                all.addAll(TaskFactory.versionSVNTasks(i, vName, rq, svnRoot));
            }
        }
        all.addAll(TaskFactory.finalDeliveryTasks(rq, dlRoot));
        all.addAll(TaskFactory.sharedSVNTasks(rq, svnRoot));
        // 只處理有資料夾 or checkHasFiles 的任務
        return all.stream()
            .filter(t -> t.getFolder() != null || t.isCheckHasFiles())
            .toList();
    }

    /**
     * 檢查單一檔名是否存在（支援 \d{8} / Vx.x 通配）。
     */
    private boolean checkSingleFile(Path dir, String filename) {
        boolean hasWild = filename.matches(".*\\d{8}.*") || filename.contains("Vx.x");
        if (!hasWild) return Files.exists(dir.resolve(filename));
        Pattern pat = buildFilenamePattern(filename);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) &&
                    pat.matcher(entry.getFileName().toString()).matches()) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    /**
     * 檢查單一檔名並回傳修改時間。回傳 long[]{found, lastModifiedMs}。
     */
    private long[] checkSingleFileWithTime(Path dir, String filename) {
        boolean hasWild = filename.matches(".*\\d{8}.*") || filename.contains("Vx.x");
        if (!hasWild) {
            Path f = dir.resolve(filename);
            if (!Files.exists(f)) return new long[]{0, 0};
            try { return new long[]{1, Files.getLastModifiedTime(f).toMillis()}; }
            catch (IOException e) { return new long[]{1, 0}; }
        }
        Pattern pat = buildFilenamePattern(filename);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) &&
                    pat.matcher(entry.getFileName().toString()).matches()) {
                    long mtime = Files.getLastModifiedTime(entry).toMillis();
                    return new long[]{1, mtime};
                }
            }
        } catch (IOException ignored) {}
        return new long[]{0, 0};
    }

    /**
     * 遞迴確認資料夾中是否有任何檔案（對應 HTML 的 checkFolderHasFiles）。
     */
    private boolean checkFolderHasFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) return true;
                if (Files.isDirectory(entry) && checkFolderHasFiles(entry)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    /**
     * 建立檔名通配 regex Pattern（對應 HTML 的 buildFilenamePattern）。
     * - 8 位連續數字 → \d{8}
     * - Vx.x → V\d+\.\d+
     * - 其他特殊字元 → 跳脫
     */
    static Pattern buildFilenamePattern(String filename) {
        StringBuilder sb = new StringBuilder("(?i)^");
        int i = 0;
        while (i < filename.length()) {
            // 8 位數字序列
            if (i + 8 <= filename.length()) {
                String seg = filename.substring(i, i + 8);
                if (seg.matches("\\d{8}")) { sb.append("\\d{8}"); i += 8; continue; }
            }
            // Vx.x
            if (i + 4 <= filename.length() && filename.substring(i, i + 4).equals("Vx.x")) {
                sb.append("V\\d+\\.\\d+"); i += 4; continue;
            }
            char c = filename.charAt(i++);
            if ("\\.[]{}()*+?^$|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    private static long parseIsoMillis(String isoStr) {
        try { return Instant.parse(isoStr).toEpochMilli(); }
        catch (Exception e) { return 0L; }
    }
}
