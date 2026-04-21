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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每日自動備份服務（對應 HTML 的 autoBackupOnLoad / doBackup）。
 *
 * 備份邏輯：
 * 1. 啟動時判斷今日是否已備份（AppConfig.lastBackupDate == today）
 * 2. 若未備份且 backupDir 有效 → 寫入 RQ_backup_{YYYYMMDD}.json
 * 3. 刪除超過 7 天的舊備份
 * 4. 更新 AppConfig.lastBackupDate / lastBackupTime
 */
public final class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class.getName());

    /** 保留備份天數 */
    private static final int BACKUP_DAYS = 7;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern BACKUP_FILE_PAT =
        Pattern.compile("^RQ_backup_(\\d{8})\\.json$");

    private BackupService() {}

    // ──────────────────────────────────────────────────────────────

    /**
     * 執行備份（應在背景執行緒呼叫）。
     * 若 backupDir 未設定或今日已備份，直接回傳 false。
     *
     * @return true = 備份成功；false = 無需備份或失敗
     */
    public static boolean runIfNeeded(DataStore dataStore, AppConfig appConfig) {
        if (!appConfig.hasBackupDir()) {
            LOG.fine("備份資料夾未設定，跳過備份");
            return false;
        }

        String today = DateTimeUtils.todayBackupDate();
        if (today.equals(appConfig.getLastBackupDate())) {
            LOG.fine("今日已備份（" + appConfig.getLastBackupDate() + "），跳過");
            return false;
        }

        return doBackup(dataStore, appConfig, today);
    }

    /**
     * 強制立即備份（不管今日是否已備份）。
     */
    public static boolean runNow(DataStore dataStore, AppConfig appConfig) {
        if (!appConfig.hasBackupDir()) return false;
        return doBackup(dataStore, appConfig, DateTimeUtils.todayBackupDate());
    }

    // ──────────────────────────────────────────────────────────────

    private static boolean doBackup(DataStore dataStore, AppConfig appConfig, String today) {
        Path backupDir = Paths.get(appConfig.getBackupDir());
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            LOG.warning("無法建立備份資料夾：" + e.getMessage());
            return false;
        }

        // 寫入今日備份
        String filename = "RQ_backup_" + today + ".json";
        Path   target   = backupDir.resolve(filename);

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

        // 刪除超過 7 天的舊備份
        purgeOldBackups(backupDir, today);

        // 記錄備份日期 / 時間
        appConfig.setLastBackupDate(today);
        appConfig.setLastBackupTime(DateTimeUtils.nowBackupTime());
        // AppConfig.setLastBackupDate 和 setLastBackupTime 內部各自呼叫 save()，
        // 為避免重複 IO，直接設定欄位後統一 save：
        appConfig.save();

        LOG.info("備份完成：" + target);
        return true;
    }

    private static void purgeOldBackups(Path backupDir, String today) {
        // 計算 7 天前的日期字串（YYYYMMDD 可直接字串比較）
        long cutoffEpoch = java.time.LocalDate.now()
            .minusDays(BACKUP_DAYS)
            .toEpochDay();
        String cutoff = java.time.LocalDate.ofEpochDay(cutoffEpoch)
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "RQ_backup_*.json")) {
            for (Path file : stream) {
                Matcher m = BACKUP_FILE_PAT.matcher(file.getFileName().toString());
                if (m.matches() && m.group(1).compareTo(cutoff) < 0) {
                    try {
                        Files.delete(file);
                        LOG.info("已刪除舊備份：" + file.getFileName());
                    } catch (IOException e) {
                        LOG.warning("刪除舊備份失敗：" + file.getFileName() + " — " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("清理舊備份時出錯：" + e.getMessage());
        }
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

    /**
     * 取得給 Header 顯示的簡短狀態文字。
     */
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
