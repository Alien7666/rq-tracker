package com.rqtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rqtracker.model.AppData;
import com.rqtracker.util.DateTimeUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自動備份服務（每 6 分鐘執行一次）。
 *
 * 備份邏輯：
 * 1. 寫入 RQ_backup_{yyyyMMdd_HHmmss}.json
 * 2. 清理規則：
 *    - 24 小時內：保留最近 50 筆
 *    - 超過 24 小時、7 天內：每天保留最早一筆
 *    - 超過 7 天：全部刪除
 * 3. 更新 AppConfig.lastBackupDate / lastBackupTime
 */
public final class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class.getName());

    private static final int BACKUP_DAYS       = 7;
    private static final int BACKUP_RECENT_MAX = 50;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern BACKUP_NEW_PAT =
        Pattern.compile("^RQ_backup_(\\d{8})_(\\d{6})\\.json$");
    private static final Pattern BACKUP_OLD_PAT =
        Pattern.compile("^RQ_backup_(\\d{8})\\.json$");

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

    private BackupService() {}

    private record BackupFile(Path path, LocalDateTime dateTime, String dateStr) {}

    // ──────────────────────────────────────────────────────────────

    /**
     * 立即執行備份。若 backupDir 未設定則回傳 false。
     */
    public static boolean runNow(DataStore dataStore, AppConfig appConfig) {
        if (!appConfig.hasBackupDir()) return false;
        return doBackup(dataStore, appConfig);
    }

    // ──────────────────────────────────────────────────────────────

    private static boolean doBackup(DataStore dataStore, AppConfig appConfig) {
        Path backupDir = Paths.get(appConfig.getBackupDir());
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            LOG.warning("無法建立備份資料夾：" + e.getMessage());
            return false;
        }

        String ts     = DateTimeUtils.nowBackupTimestamp(); // yyyyMMdd_HHmmss
        Path   target = backupDir.resolve("RQ_backup_" + ts + ".json");

        AppData snapshot = dataStore.exportAll();
        snapshot.setSavedAt(DateTimeUtils.nowIso());

        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(target, StandardOpenOption.CREATE,
                                      StandardOpenOption.TRUNCATE_EXISTING),
                StandardCharsets.UTF_8)) {
            MAPPER.writeValue(w, snapshot);
        } catch (IOException e) {
            LOG.warning("備份寫入失敗：" + e.getMessage());
            return false;
        }

        purgeBackups(backupDir);

        // 記錄備份日期與時間（yyyyMMdd → HH:mm）
        String dateStr = ts.substring(0, 8);
        String timeStr = ts.substring(9, 11) + ":" + ts.substring(11, 13);
        appConfig.setLastBackupDate(dateStr);
        appConfig.setLastBackupTime(timeStr);
        appConfig.save();

        LOG.info("備份完成：" + target);
        return true;
    }

    private static void purgeBackups(Path backupDir) {
        LocalDateTime now    = LocalDateTime.now();
        LocalDateTime cut24h = now.minusHours(24);
        LocalDateTime cut7d  = now.minusDays(BACKUP_DAYS);

        List<BackupFile> all = collectBackupFiles(backupDir);
        all.sort(Comparator.comparing(BackupFile::dateTime));

        Set<Path> toKeep = new HashSet<>();

        // 24 小時內：保留最近 50 筆
        List<BackupFile> recent = all.stream()
            .filter(f -> f.dateTime().isAfter(cut24h))
            .toList();
        recent.stream()
            .skip(Math.max(0L, recent.size() - BACKUP_RECENT_MAX))
            .forEach(f -> toKeep.add(f.path()));

        // 7 天內但超過 24 小時：每天只保留最早一筆
        Set<String> seenDays = new HashSet<>();
        for (BackupFile f : all) {
            if (!f.dateTime().isAfter(cut24h) && f.dateTime().isAfter(cut7d)) {
                if (seenDays.add(f.dateStr())) {
                    toKeep.add(f.path());
                }
            }
        }

        // 刪除其餘（含超過 7 天的舊檔）
        for (BackupFile f : all) {
            if (!toKeep.contains(f.path())) {
                try {
                    Files.delete(f.path());
                    LOG.info("已刪除備份：" + f.path().getFileName());
                } catch (IOException e) {
                    LOG.warning("刪除備份失敗：" + f.path().getFileName() + " — " + e.getMessage());
                }
            }
        }
    }

    private static List<BackupFile> collectBackupFiles(Path backupDir) {
        List<BackupFile> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "RQ_backup_*.json")) {
            for (Path p : stream) {
                BackupFile bf = parseBackupFile(p);
                if (bf != null) result.add(bf);
            }
        } catch (IOException e) {
            LOG.warning("讀取備份清單失敗：" + e.getMessage());
        }
        return result;
    }

    private static BackupFile parseBackupFile(Path p) {
        String name = p.getFileName().toString();
        Matcher m = BACKUP_NEW_PAT.matcher(name);
        if (m.matches()) {
            LocalDateTime dt = LocalDateTime.parse(m.group(1) + " " + m.group(2), DT_FMT);
            return new BackupFile(p, dt, m.group(1));
        }
        m = BACKUP_OLD_PAT.matcher(name);
        if (m.matches()) {
            LocalDateTime dt = LocalDateTime.parse(m.group(1) + " 000000", DT_FMT);
            return new BackupFile(p, dt, m.group(1));
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 備份狀態查詢（UI 顯示用）
    // ──────────────────────────────────────────────────────────────

    public enum BackupStatus { NOT_CONFIGURED, TODAY_DONE, NOT_TODAY }

    public static BackupStatus getStatus(AppConfig appConfig) {
        if (!appConfig.hasBackupDir()) return BackupStatus.NOT_CONFIGURED;
        String today = DateTimeUtils.todayBackupDate();
        return today.equals(appConfig.getLastBackupDate())
            ? BackupStatus.TODAY_DONE
            : BackupStatus.NOT_TODAY;
    }

    public static String getStatusText(AppConfig appConfig) {
        return switch (getStatus(appConfig)) {
            case NOT_CONFIGURED -> "備份：未設定";
            case TODAY_DONE     -> "✓ 備份 " + (appConfig.getLastBackupTime() != null
                                        ? appConfig.getLastBackupTime() : "今日");
            case NOT_TODAY      -> {
                String d = appConfig.getLastBackupDate();
                if (d == null || d.length() != 8) yield "⚠ 尚未備份";
                yield "⚠ 上次 " + d.substring(4, 6) + "/" + d.substring(6);
            }
        };
    }
}
