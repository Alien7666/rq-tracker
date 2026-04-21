package com.rqtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

/**
 * 匯出/匯入 JSON 的頂層包裝（與 HTML 版 getAllData() 格式完全相容）。
 *
 * HTML 版格式：
 * {
 *   "version": 1,
 *   "savedAt": "2026-04-16T09:00:00.000Z",
 *   "index": [...],
 *   "rqs": { "RQ...": {...} },
 *   "history": [...]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppData {

    /** 備份格式版本號（目前為 1） */
    private int version = 1;

    /** 儲存/匯出時間，ISO 8601 格式 */
    private String savedAt;

    /** RQ ID 的有序清單（對應 localStorage rqtracker_index） */
    private List<String> index = new ArrayList<>();

    /** 所有 RQ 資料，key = RQ ID */
    private Map<String, RQData> rqs = new LinkedHashMap<>();

    /** 歸檔的 RQ 清單（含 archivedAt 欄位） */
    private List<RQData> history = new ArrayList<>();

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSavedAt() { return savedAt; }
    public void setSavedAt(String savedAt) { this.savedAt = savedAt; }

    public List<String> getIndex() { return index; }
    public void setIndex(List<String> index) { this.index = index != null ? index : new ArrayList<>(); }

    public Map<String, RQData> getRqs() { return rqs; }
    public void setRqs(Map<String, RQData> rqs) { this.rqs = rqs != null ? rqs : new LinkedHashMap<>(); }

    public List<RQData> getHistory() { return history; }
    public void setHistory(List<RQData> history) { this.history = history != null ? history : new ArrayList<>(); }
}
