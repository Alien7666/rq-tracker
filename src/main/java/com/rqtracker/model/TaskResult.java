package com.rqtracker.model;

/**
 * 磁碟掃描結果（對應 HTML 的 scanResults 狀態）。
 */
public record TaskResult(ScanState state, int found, int total) {

    /** 磁碟掃描狀態枚舉 */
    public enum ScanState {
        /** 所有指定檔案存在（可自動打勾） */
        FILE,
        /** 部分檔案存在 */
        PARTIAL,
        /** 資料夾存在但目標檔案未找到 */
        FOLDER,
        /** 路徑尚不存在 */
        NONE,
        /** 檔案存在但修改時間早於 RQ 建立時間（尚未編輯） */
        STALE,
        /** 磁碟路徑未設定，無法掃描 */
        UNKNOWN,
        /** 掃描進行中 */
        SCANNING
    }

    // ── 便利工廠方法 ─────────────────────────────────────────────────────────

    public static TaskResult file(int found, int total) {
        return new TaskResult(ScanState.FILE, found, total);
    }
    public static TaskResult partial(int found, int total) {
        return new TaskResult(ScanState.PARTIAL, found, total);
    }
    public static TaskResult folder() {
        return new TaskResult(ScanState.FOLDER, 0, 0);
    }
    public static TaskResult none() {
        return new TaskResult(ScanState.NONE, 0, 0);
    }
    public static TaskResult stale() {
        return new TaskResult(ScanState.STALE, 0, 0);
    }
    public static TaskResult unknown() {
        return new TaskResult(ScanState.UNKNOWN, 0, 0);
    }
    public static TaskResult scanning() {
        return new TaskResult(ScanState.SCANNING, 0, 0);
    }

    /** 是否代表「檔案完整存在」（可自動打勾的狀態） */
    public boolean isComplete() { return state == ScanState.FILE; }
}
