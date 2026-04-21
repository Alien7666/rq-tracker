package com.rqtracker.model;

/**
 * 任務定義（對應 HTML 任務物件的所有屬性）。
 * 由 TaskFactory 的各生成函數動態產生，不儲存在 data.json 中。
 */
public class TaskDef {

    /** 任務唯一識別碼，例："sf_01"、"v0_dev_03"、"fin_zip" */
    private final String key;

    /** 步驟編號，用於共用流程/版本開發流程的序號顯示，例："01" */
    private final String step;

    /** 任務標籤文字，例："閱讀需求單" */
    private final String label;

    /** 副說明文字（可 null），例："靜態掃描" */
    private final String sub;

    /** 對應的資料夾路徑（可 null），例："D:\\Systex\\RQ100051742_xxx\\" */
    private final String folder;

    /** 對應的檔案名稱（可 null），例："RQ100051742_Fortify_pstID_OWASPAPITop10_20260416.doc" */
    private final String filename;

    /** 代碼根路徑（checkHasFiles 任務用），例："D:\\Systex\\" */
    private final String codeRoot;

    /** 版本 ID（checkHasFiles 任務用），例："pstID" */
    private final String codeVid;

    /** 版本索引（checkHasFiles 任務用） */
    private final Integer codeVidIdx;

    /** 是否顯示「⚠ 當天產生/編輯」警告標籤 */
    private final boolean warn;

    /** 警告標籤文字，例："當天產生"、"當天編輯" */
    private final String warnText;

    /** 是否為選填項目（不計入進度計算） */
    private final boolean optional;

    /**
     * 是否需要檢查資料夾中是否有程式碼檔案（代碼交付任務）。
     * 若 true，掃描時使用 codeRoot + versionFiles 清單來驗證。
     */
    private final boolean checkHasFiles;

    /**
     * 是否需要檢查檔案修改時間 > RQ 建立時間（確認已編輯）。
     */
    private final boolean checkModTime;

    /**
     * 建立資料夾時使用的路徑（若與 folder 不同）。
     * 通常是 folder 的子路徑。
     */
    private final String createFolder;

    // ── Builder 模式 ──────────────────────────────────────────────────────────

    private TaskDef(Builder b) {
        this.key          = b.key;
        this.step         = b.step;
        this.label        = b.label;
        this.sub          = b.sub;
        this.folder       = b.folder;
        this.filename     = b.filename;
        this.codeRoot     = b.codeRoot;
        this.codeVid      = b.codeVid;
        this.codeVidIdx   = b.codeVidIdx;
        this.warn         = b.warn;
        this.warnText     = b.warnText;
        this.optional     = b.optional;
        this.checkHasFiles= b.checkHasFiles;
        this.checkModTime = b.checkModTime;
        this.createFolder = b.createFolder;
    }

    public static Builder builder(String key, String label) {
        return new Builder(key, label);
    }

    public static class Builder {
        private final String key;
        private final String label;
        private String step;
        private String sub;
        private String folder;
        private String filename;
        private String codeRoot;
        private String codeVid;
        private Integer codeVidIdx;
        private boolean warn;
        private String warnText = "當天產生";
        private boolean optional;
        private boolean checkHasFiles;
        private boolean checkModTime;
        private String createFolder;

        public Builder(String key, String label) {
            this.key   = key;
            this.label = label;
        }

        public Builder step(String step)              { this.step = step; return this; }
        public Builder sub(String sub)                { this.sub = sub; return this; }
        public Builder folder(String folder)          { this.folder = folder; return this; }
        public Builder filename(String filename)      { this.filename = filename; return this; }
        public Builder codeRoot(String codeRoot)      { this.codeRoot = codeRoot; return this; }
        public Builder codeVid(String codeVid)        { this.codeVid = codeVid; return this; }
        public Builder codeVidIdx(int idx)            { this.codeVidIdx = idx; return this; }
        public Builder warn(String warnText)          { this.warn = true; this.warnText = warnText; return this; }
        public Builder warn()                         { this.warn = true; return this; }
        public Builder optional()                     { this.optional = true; return this; }
        public Builder checkHasFiles()                { this.checkHasFiles = true; return this; }
        public Builder checkModTime()                 { this.checkModTime = true; return this; }
        public Builder createFolder(String cf)        { this.createFolder = cf; return this; }

        public TaskDef build() { return new TaskDef(this); }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getKey()           { return key; }
    public String getStep()          { return step; }
    public String getLabel()         { return label; }
    public String getSub()           { return sub; }
    public String getFolder()        { return folder; }
    public String getFilename()      { return filename; }
    public String getCodeRoot()      { return codeRoot; }
    public String getCodeVid()       { return codeVid; }
    public Integer getCodeVidIdx()   { return codeVidIdx; }
    public boolean isWarn()          { return warn; }
    public String getWarnText()      { return warnText; }
    public boolean isOptional()      { return optional; }
    public boolean isCheckHasFiles() { return checkHasFiles; }
    public boolean isCheckModTime()  { return checkModTime; }
    public String getCreateFolder()  { return createFolder != null ? createFolder : folder; }

    @Override
    public String toString() { return "TaskDef{key='" + key + "', label='" + label + "'}"; }
}
