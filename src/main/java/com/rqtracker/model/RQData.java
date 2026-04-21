package com.rqtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

/**
 * RQ 需求單的完整資料模型。
 * 欄位設計與 HTML 版 localStorage 格式完全相容，可直接從 HTML 版匯出的 JSON 匯入。
 *
 * JSON 相容性注意事項：
 * - versionFiles 的 key 是字串化的 int（JavaScript 物件行為），使用 Map<String,String>
 * - archivedAt 只在歷史紀錄條目中存在，一般 RQ 無此欄位
 * - checks 值在 HTML 版為 true（已勾），此處統一以 Boolean 儲存
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RQData {

    /** RQ 完整識別碼，例："RQ100051742_請於網路郵局新增推薦員工編號欄位" */
    private String id;

    /** 專案號，例："POSMS"，用於 SVN 路徑 */
    private String projectNum;

    /** 版本清單，順序對應版本 A、B、C... */
    private List<RQVersion> versions = new ArrayList<>();

    /**
     * 任務勾選狀態。key = 任務 key（如 "sf_01"），value = true（已勾）。
     * HTML 版只儲存 true，未勾的 key 不存在；Java 版維持同樣語意。
     */
    private Map<String, Boolean> checks = new HashMap<>();

    /**
     * 勾選時間戳。key = 任務 key，value = zh-TW 格式時間（如 "2026/04/16 09:00"）。
     */
    private Map<String, String> timestamps = new HashMap<>();

    /** 備忘錄文字 */
    private String note;

    /**
     * UI 折疊狀態。key = section ID（如 "scard_共用流程"、"vcard_0"），value = true（已折疊）。
     */
    private Map<String, Boolean> collapseState = new HashMap<>();

    /**
     * 各 section 全部完成的時間戳。key = section key（如 "dev_v0"、"shared_fin"）。
     */
    private Map<String, String> sectionDone = new HashMap<>();

    /**
     * 各版本的改動程式清單。key = 版本索引字串（如 "0"、"1"），value = 多行文字，每行一個相對路徑。
     * 使用 Map<String,String> 以相容 JavaScript 字串化 int key 的行為。
     */
    private Map<String, String> versionFiles = new HashMap<>();

    /** 建立時間，ISO 8601 格式（如 "2026-04-16T08:00:00.000Z"） */
    private String createdAt;

    /** 歸檔時間，只在歷史紀錄條目中存在，ISO 8601 格式 */
    private String archivedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public RQData() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectNum() { return projectNum; }
    public void setProjectNum(String projectNum) { this.projectNum = projectNum; }

    public List<RQVersion> getVersions() { return versions; }
    public void setVersions(List<RQVersion> versions) { this.versions = versions != null ? versions : new ArrayList<>(); }

    public Map<String, Boolean> getChecks() { return checks; }
    public void setChecks(Map<String, Boolean> checks) { this.checks = checks != null ? checks : new HashMap<>(); }

    public Map<String, String> getTimestamps() { return timestamps; }
    public void setTimestamps(Map<String, String> timestamps) { this.timestamps = timestamps != null ? timestamps : new HashMap<>(); }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Map<String, Boolean> getCollapseState() { return collapseState; }
    public void setCollapseState(Map<String, Boolean> collapseState) { this.collapseState = collapseState != null ? collapseState : new HashMap<>(); }

    public Map<String, String> getSectionDone() { return sectionDone; }
    public void setSectionDone(Map<String, String> sectionDone) { this.sectionDone = sectionDone != null ? sectionDone : new HashMap<>(); }

    public Map<String, String> getVersionFiles() { return versionFiles; }
    public void setVersionFiles(Map<String, String> versionFiles) { this.versionFiles = versionFiles != null ? versionFiles : new HashMap<>(); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getArchivedAt() { return archivedAt; }
    public void setArchivedAt(String archivedAt) { this.archivedAt = archivedAt; }

    // ── 便利方法 ──────────────────────────────────────────────────────────────

    /** 判斷某任務是否已勾選 */
    public boolean isChecked(String taskKey) {
        Boolean v = checks.get(taskKey);
        return v != null && v;
    }

    /** 勾選任務並記錄時間戳（zh-TW 格式） */
    public void check(String taskKey, String timestamp) {
        checks.put(taskKey, true);
        timestamps.put(taskKey, timestamp);
    }

    /** 取消勾選，移除時間戳 */
    public void uncheck(String taskKey) {
        checks.remove(taskKey);
        timestamps.remove(taskKey);
    }

    /** 取得版本改動程式文字（vIdx 整數轉字串 key） */
    public String getVersionFilesText(int vIdx) {
        return versionFiles.getOrDefault(String.valueOf(vIdx), "");
    }

    /** 儲存版本改動程式文字 */
    public void setVersionFilesText(int vIdx, String text) {
        if (text == null || text.isBlank()) {
            versionFiles.remove(String.valueOf(vIdx));
        } else {
            versionFiles.put(String.valueOf(vIdx), text);
        }
    }

    /** 取得折疊狀態 */
    public boolean getCollapsed(String key, boolean defaultValue) {
        Boolean v = collapseState.get(key);
        return v != null ? v : defaultValue;
    }

    /** 儲存折疊狀態 */
    public void setCollapsed(String key, boolean collapsed) {
        collapseState.put(key, collapsed);
    }

    @Override
    public String toString() { return id; }
}
