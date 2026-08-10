package com.rqtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 使用者可編輯的版本預設值。
 * vid          — 版本識別碼（例：pstID），對應路徑變數中的 ${vid}
 * displayName  — 顯示名稱（例：網路郵局中文版），用於下拉選單顯示與程式碼清單標題
 * sbomFolder   — SBOM/ZAP 等資料夾的完整名稱（例：1_網路郵局中文版）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionPreset {

    private String vid;
    private String displayName;
    private String sbomFolder;

    public VersionPreset() {}

    public VersionPreset(String vid, String displayName, String sbomFolder) {
        this.vid = vid;
        this.displayName = displayName;
        this.sbomFolder = sbomFolder;
    }

    public String getVid() { return vid; }
    public void setVid(String vid) { this.vid = vid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSbomFolder() { return sbomFolder; }
    public void setSbomFolder(String sbomFolder) { this.sbomFolder = sbomFolder; }

    /** 組合「顯示名 vid」字串，存進 RQVersion.name 後可由 PathUtils.versionId/versionDisplayName 解析。 */
    public String toCombinedName() {
        String d = displayName == null ? "" : displayName.trim();
        String v = vid == null ? "" : vid.trim();
        if (d.isBlank()) return v;
        if (v.isBlank()) return d;
        return d + " " + v;
    }

    @Override
    public String toString() {
        return toCombinedName();
    }
}
