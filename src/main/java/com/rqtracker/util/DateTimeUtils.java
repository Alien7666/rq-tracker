package com.rqtracker.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * 日期時間格式化工具（對應 HTML 的 toLocaleString('zh-TW', ...) 系列呼叫）。
 */
public final class DateTimeUtils {

    /** zh-TW 時間戳格式，用於任務勾選時間顯示（例："2026/04/16 09:00"） */
    private static final DateTimeFormatter ZH_TW_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    /** 備份日期格式 YYYYMMDD */
    private static final DateTimeFormatter BACKUP_DATE_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 備份時間格式 HH:mm */
    private static final DateTimeFormatter BACKUP_TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm");

    /** 備份時間戳格式 yyyyMMdd_HHmmss（用於每次備份的唯一檔名） */
    private static final DateTimeFormatter BACKUP_TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** ISO 8601 格式（用於 createdAt / archivedAt / savedAt） */
    private static final DateTimeFormatter ISO_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                         .withZone(ZoneOffset.UTC);

    private DateTimeUtils() {}

    /** 取得目前時間的 zh-TW 時間戳字串，例："2026/04/16 09:00" */
    public static String nowZhTW() {
        return LocalDateTime.now().format(ZH_TW_TIMESTAMP);
    }

    /** 取得目前時間的 ISO 8601 字串，例："2026-04-16T01:00:00.000Z" */
    public static String nowIso() {
        return ISO_FMT.format(Instant.now());
    }

    /** 取得今日備份日期字串，例："20260416" */
    public static String todayBackupDate() {
        return LocalDate.now().format(BACKUP_DATE_FMT);
    }

    /** 取得目前備份時間字串，例："09:00" */
    public static String nowBackupTime() {
        return LocalTime.now().format(BACKUP_TIME_FMT);
    }

    /** 取得目前備份時間戳字串，例："20260507_143022" */
    public static String nowBackupTimestamp() {
        return LocalDateTime.now().format(BACKUP_TS_FMT);
    }

    /**
     * 將 ISO 8601 字串轉為 Instant（用於 checkModTime 比較）。
     * 若解析失敗回傳 null。
     */
    public static Instant parseIso(String isoStr) {
        if (isoStr == null || isoStr.isBlank()) return null;
        try {
            return Instant.parse(isoStr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 將 ISO 8601 字串轉為 zh-TW 顯示格式（用於歷史紀錄顯示）。
     * 例："2026-04-16T08:00:00.000Z" → "2026/04/16 16:00"（轉換為本地時區）
     */
    public static String isoToZhTW(String isoStr) {
        Instant instant = parseIso(isoStr);
        if (instant == null) return "";
        return ZH_TW_TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()));
    }
}
