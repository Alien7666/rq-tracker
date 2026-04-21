package com.rqtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * 應用程式設定管理（對應 HTML 版的 IndexedDB handle 儲存 + localStorage 設定）。
 * 設定儲存至 %APPDATA%\RQTracker\settings.json。
 *
 * Java 版改進：磁碟路徑直接存字串，不需要每次彈出資料夾選擇器授權。
 */
public class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());
    private static final String SETTINGS_FILENAME = "settings.json";

    /** D:\Systex 根目錄（對應 HTML 的 DISK_PREFIXES.downloads） */
    private String downloadsRoot = "D:\\Systex";

    /** C:\SVN\新系統開發 根目錄（對應 HTML 的 DISK_PREFIXES.svn + 子目錄） */
    private String svnRoot = "C:\\SVN\\新系統開發";

    /** 備份資料夾路徑（對應 HTML 的 IndexedDB backupDirHandle） */
    private String backupDir;

    /** 最後備份日期 YYYYMMDD（對應 HTML 的 rq_backup_last_date） */
    private String lastBackupDate;

    /** 最後備份時間 HH:mm（對應 HTML 的 rq_backup_last_time） */
    private String lastBackupTime;

    /** 最後活躍的 RQ ID（對應 HTML 的 rq_active） */
    private String activeRQ;

    // ── 靜態載入 ──────────────────────────────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static AppConfig instance;
    private static Path settingsPath;

    public static AppConfig load(Path appDataDir) {
        settingsPath = appDataDir.resolve(SETTINGS_FILENAME);
        if (Files.exists(settingsPath)) {
            try {
                instance = MAPPER.readValue(settingsPath.toFile(), AppConfig.class);
                return instance;
            } catch (IOException e) {
                LOG.warning("無法讀取 settings.json，使用預設值：" + e.getMessage());
            }
        }
        instance = new AppConfig();
        return instance;
    }

    public static AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    public void save() {
        if (settingsPath == null) return;
        try {
            Files.createDirectories(settingsPath.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), this);
        } catch (IOException e) {
            LOG.warning("無法儲存 settings.json：" + e.getMessage());
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getDownloadsRoot() { return downloadsRoot; }
    public void setDownloadsRoot(String downloadsRoot) {
        this.downloadsRoot = downloadsRoot;
        save();
    }

    public String getSvnRoot() { return svnRoot; }
    public void setSvnRoot(String svnRoot) {
        this.svnRoot = svnRoot;
        save();
    }

    public String getBackupDir() { return backupDir; }
    public void setBackupDir(String backupDir) {
        this.backupDir = backupDir;
        save();
    }

    public String getLastBackupDate() { return lastBackupDate; }
    public void setLastBackupDate(String lastBackupDate) {
        this.lastBackupDate = lastBackupDate;
        save();
    }

    public String getLastBackupTime() { return lastBackupTime; }
    public void setLastBackupTime(String lastBackupTime) {
        this.lastBackupTime = lastBackupTime;
        save();
    }

    public String getActiveRQ() { return activeRQ; }
    public void setActiveRQ(String activeRQ) {
        this.activeRQ = activeRQ;
        save();
    }

    /** 是否已設定備份資料夾 */
    public boolean hasBackupDir() {
        return backupDir != null && !backupDir.isBlank();
    }

    /** 是否已設定 D:\Systex 路徑 */
    public boolean hasDownloadsRoot() {
        return downloadsRoot != null && !downloadsRoot.isBlank();
    }

    /** 是否已設定 SVN 根路徑 */
    public boolean hasSvnRoot() {
        return svnRoot != null && !svnRoot.isBlank();
    }

    /** 確保路徑以反斜線結尾後回傳 */
    public String getDownloadsRootSlash() {
        if (downloadsRoot == null) return "D:\\Systex\\";
        return downloadsRoot.endsWith("\\") ? downloadsRoot : downloadsRoot + "\\";
    }

    public String getSvnRootSlash() {
        if (svnRoot == null) return "C:\\SVN\\新系統開發\\";
        return svnRoot.endsWith("\\") ? svnRoot : svnRoot + "\\";
    }
}
