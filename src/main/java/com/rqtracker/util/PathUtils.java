package com.rqtracker.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路徑與名稱工具函數（完整移植自 HTML 版）。
 * 所有方法皆為無狀態靜態方法。
 */
public final class PathUtils {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern RQ_NUMBER_PATTERN  = Pattern.compile("(RQ\\d+)", Pattern.CASE_INSENSITIVE);

    // SBOM 系統資料夾映射（vid → 資料夾名稱）
    private static final Map<String, String> SBOM_SYSTEM_MAP = Map.of(
        "pstID",   "1_網路郵局中文版",
        "pstEN",   "2_網路郵局英文版",
        "pstacce", "3_網路郵局友善專區",
        "pstsam",  "4_網路郵局後台",
        "pstmail", "8_郵件查詢前台"
    );

    private PathUtils() {}

    /**
     * 今日日期，YYYYMMDD 格式（對應 HTML 的 todayDate()）。
     * 例："20260416"
     */
    public static String todayDate() {
        return LocalDate.now().format(DATE_FMT);
    }

    /**
     * 從 RQ ID 提取純 RQ 號碼（對應 HTML 的 rqNumber(id)）。
     * 例："RQ100051742_請於網路郵局..." → "RQ100051742"
     * 例："unknown_no_rq_prefix" → "unknown_no_rq_prefix"（無 RQ 前綴則回傳原值）
     */
    public static String rqNumber(String id) {
        if (id == null) return "";
        Matcher m = RQ_NUMBER_PATTERN.matcher(id);
        return m.find() ? m.group(1) : id;
    }

    /**
     * 取版本名稱的最後一個空白分隔詞作為版本 ID（對應 HTML 的 versionId(name)）。
     * 例："網路郵局中文版 pstID" → "pstID"
     * 例："pstID"（無空格）→ "pstID"
     */
    public static String versionId(String name) {
        if (name == null || name.isBlank()) return name;
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    /**
     * 取版本名稱除最後一詞以外的部分作為顯示名（用於程式碼清單標題）。
     * 例："網路郵局中文版 pstID" → "網路郵局中文版"
     * 例："pstID"（無空格）→ "pstID"
     */
    public static String versionDisplayName(String name) {
        if (name == null || name.isBlank()) return name;
        String[] parts = name.trim().split("\\s+");
        if (parts.length <= 1) return name;
        return String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
    }

    /**
     * vid 對應的 SBOM 系統資料夾名稱（對應 HTML 的 sbomSystemFolder(vid)）。
     * 例："pstID" → "1_網路郵局中文版"
     */
    public static String sbomSystemFolder(String vid) {
        return SBOM_SYSTEM_MAP.getOrDefault(vid, "{系統_" + vid + "}");
    }

    /**
     * vid 對應的 SBOM 客戶資料夾名稱（移除數字前綴）。
     * 例："pstID" → "網路郵局中文版"
     */
    public static String sbomCustomerFolder(String vid) {
        return sbomSystemFolder(vid).replaceFirst("^\\d+_", "");
    }

    /**
     * 過濾 Windows 路徑中的不合法字元（對應 HTML 的 winSafeName(s)）。
     * 替換：/ → _
     * 移除：* ? " < > |
     * 保留：\ : 以及其他字元（整個路徑字串傳入時保留路徑分隔符）
     */
    public static String winSafeName(String s) {
        if (s == null) return "";
        return s.replace('/', '_').replaceAll("[*?\"<>|]", "");
    }

    /**
     * 將路徑中的正斜線統一轉為反斜線（Windows 路徑正規化）。
     */
    public static String toWindowsPath(String path) {
        if (path == null) return "";
        return path.replace('/', '\\');
    }

    /**
     * 確保路徑以反斜線結尾。
     */
    public static String ensureTrailingSlash(String path) {
        if (path == null) return "\\";
        return path.endsWith("\\") ? path : path + "\\";
    }
}
