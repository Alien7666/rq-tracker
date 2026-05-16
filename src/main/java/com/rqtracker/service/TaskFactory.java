package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;
import com.rqtracker.util.PathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 任務定義生成器（完整移植自 HTML 版 6 個任務函數）。
 * 所有方法皆為無狀態靜態方法，接受路徑設定注入。
 */
public final class TaskFactory {

    /** 預設交付物根目錄 */
    public static final String DEFAULT_DOWNLOADS_ROOT = "D:\\Systex";
    /** 預設 SVN 根目錄 */
    public static final String DEFAULT_SVN_ROOT = "C:\\SVN\\新系統開發";

    private TaskFactory() {}

    // ──────────────────────────────────────────────
    // 1. sharedFlowTasks() — 共用流程任務（2 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> sharedFlowTasks() {
        return List.of(
            TaskDef.builder("sf_01", "閱讀需求單").step("01").build(),
            TaskDef.builder("sf_02", "理解並規劃程式").step("02").build()
        );
    }

    // ──────────────────────────────────────────────
    // 2. versionDevTasks(vIdx) — 版本開發任務（10 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> versionDevTasks(int vIdx) {
        String p = "v" + vIdx + "_dev_";
        return List.of(
            TaskDef.builder(p + "03", "撰寫程式（含 SQL）").step("03").build(),
            TaskDef.builder(p + "04", "本機測試程式").step("04").build(),
            TaskDef.builder(p + "05", "Fortify").step("05").sub("靜態掃描").build(),
            TaskDef.builder(p + "05b", "製版").step("06").build(),
            TaskDef.builder(p + "06", "上 SIT").step("07").sub("部署").build(),
            TaskDef.builder(p + "07", "SIT 測試").step("08").build(),
            TaskDef.builder(p + "07b", "測試報告").step("09").build(),
            TaskDef.builder(p + "08", "ZAP 動態掃描").step("10").build(),
            TaskDef.builder(p + "09", "匯出弱點報告").step("11").build(),
            TaskDef.builder(p + "09b", "滲透測試報告").step("12").build()
        );
    }

    // ──────────────────────────────────────────────
    // 3. versionDeliverables(vIdx, rq, vName) — 版本交付物（8 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> versionDeliverables(int vIdx, RQData rq, String vName) {
        return versionDeliverables(vIdx, rq, vName, DEFAULT_DOWNLOADS_ROOT);
    }

    public static List<TaskDef> versionDeliverables(int vIdx, RQData rq, String vName, String downloadsRoot) {
        String rqId  = rq.getId();
        String rqNum = PathUtils.rqNumber(rqId);
        String vid   = PathUtils.versionId(vName);
        String td    = PathUtils.todayDate();
        String ROOT  = ensureSlash(downloadsRoot) + PathUtils.winSafeName(rqId) + "\\";
        String p     = "v" + vIdx + "_del_";

        List<TaskDef> tasks = new ArrayList<>();

        // del_code：放入程式碼（checkHasFiles）
        String codeFolder = ROOT + td + "_" + rqNum + "\\" + vid + "\\";
        tasks.add(TaskDef.builder(p + "code", "放入程式碼")
            .folder(codeFolder)
            .codeRoot(ROOT)
            .createFolder(codeFolder)
            .codeVid(vid)
            .codeVidIdx(vIdx)
            .checkHasFiles()
            .build());

        // del_fortify
        tasks.add(TaskDef.builder(p + "fortify", "Fortify OWASP 報告")
            .folder(ROOT + "安全性文件\\" + vid + "\\")
            .filename(rqNum + "_Fortify_" + vid + "_OWASPAPITop10_" + td + ".doc")
            .build());

        // del_testrpt
        tasks.add(TaskDef.builder(p + "testrpt", "測試報告")
            .folder(ROOT + "測試報告\\" + vid + "\\")
            .filename("中華郵政_網路郵局系統_測試報告_" + rqNum + "_" + vid + ".xlsx")
            .build());

        // del_vulnscan
        tasks.add(TaskDef.builder(p + "vulnscan", "廠商系統弱點掃描報告")
            .folder(ROOT + "安全性文件\\" + vid + "\\")
            .filename("廠商系統弱點掃描_" + vid + "_" + td + ".pdf")
            .warn("當天產生")
            .build());

        // del_zapexcl
        tasks.add(TaskDef.builder(p + "zapexcl", "ZAP 弱點掃描排除說明")
            .folder(ROOT + "安全性文件\\" + vid + "\\")
            .filename("ZAP弱點掃描排除說明_" + vid + ".xlsx")
            .warn("當天產生")
            .build());

        // del_pentest
        tasks.add(TaskDef.builder(p + "pentest", "滲透測試報告")
            .folder(ROOT + "安全性文件\\" + vid + "\\")
            .filename("滲透測試_" + vid + "_" + td + ".docx")
            .warn("當天產生")
            .build());

        // del_pentestcsv
        tasks.add(TaskDef.builder(p + "pentestcsv", "滲透測試過程報告")
            .folder(ROOT + "安全性文件\\" + vid + "\\")
            .filename("滲透測試過程報告_" + vid + "_" + td + ".csv")
            .warn("當天產生")
            .build());

        // del_sbom
        tasks.add(TaskDef.builder(p + "sbom", "SBOM")
            .folder(ROOT + "SBOM\\" + vid + "\\")
            .filename("file.json  +  manifest.spdx.json")
            .warn("當天產生")
            .build());

        return List.copyOf(tasks);
    }

    // ──────────────────────────────────────────────
    // 4. versionSVNTasks(vIdx, vName, rq) — 版本 SVN 任務（5 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> versionSVNTasks(int vIdx, String vName, RQData rq) {
        return versionSVNTasks(vIdx, vName, rq, DEFAULT_SVN_ROOT);
    }

    public static List<TaskDef> versionSVNTasks(int vIdx, String vName, RQData rq, String svnRoot) {
        String rqId  = rq != null ? rq.getId() : "{RQ}";
        String rqNum = PathUtils.rqNumber(rqId);
        String proj  = (rq != null && rq.getProjectNum() != null) ? rq.getProjectNum() : "{專案}";
        String vid   = PathUtils.versionId(vName);
        String td    = PathUtils.todayDate();
        String SVN   = ensureSlash(svnRoot) + "10_增修維護階段\\變更需求單\\" + proj + "_" + PathUtils.winSafeName(rqId) + "\\";
        String SBOM_BASE = ensureSlash(svnRoot) + "9_文件\\99_共用文件\\18.軟體物料清單(SBOM)\\2_系統盤點\\";
        String p     = "v" + vIdx + "_svn_";

        return List.of(
            TaskDef.builder(p + "005", "放入 Fortify 報告")
                .folder(SVN + "005_廠商弱掃報告\\")
                .filename(rqNum + "_Fortify_" + vid + "_OWASPAPITop10_" + td + ".doc")
                .build(),

            TaskDef.builder(p + "004", "放入測試報告")
                .folder(SVN + "004_測試報告\\")
                .filename("中華郵政_網路郵局系統_測試報告_" + rqNum + "_" + vid + ".xlsx")
                .build(),

            TaskDef.builder(p + "zap1", "ZAP 中華郵政弱點掃描")
                .sub("由交付給客戶的「廠商系統弱點掃描_" + vid + "_" + td + ".pdf」改名後放入")
                .folder(ensureSlash(svnRoot) + "9_文件\\99_共用文件\\19.弱掃及滲透測試(ZAP)\\1_中華郵政\\" + PathUtils.sbomSystemFolder(vid) + "\\" + td + "\\")
                .filename("中華郵政_廠商系統弱點掃描_" + vid + "_" + td + ".pdf")
                .warn("當天產生")
                .build(),

            TaskDef.builder(p + "zap2", "ZAP 內部系統盤點弱點掃描")
                .folder(ensureSlash(svnRoot) + "9_文件\\99_共用文件\\19.弱掃及滲透測試(ZAP)\\2_內部系統盤點\\" + PathUtils.sbomSystemFolder(vid) + "\\" + td + "\\")
                .filename("內部盤點_廠商系統弱點掃描_" + vid + ".pdf")
                .warn("當天產生")
                .build(),

            TaskDef.builder(p + "sbom", "SBOM 系統盤點更新")
                .folder(SBOM_BASE + PathUtils.sbomSystemFolder(vid) + "\\" + td + "\\_manifest\\spdx_2.2\\")
                .filename("file.json  +  manifest.spdx.json  +  manifest.spdx.json.sha256")
                .warn("當天產生")
                .build()
        );
    }

    // ──────────────────────────────────────────────
    // 5. finalDeliveryTasks(rq) — 最終交付任務（4 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> finalDeliveryTasks(RQData rq) {
        return finalDeliveryTasks(rq, DEFAULT_DOWNLOADS_ROOT);
    }

    public static List<TaskDef> finalDeliveryTasks(RQData rq, String downloadsRoot) {
        String rqId  = rq.getId();
        String rqNum = PathUtils.rqNumber(rqId);
        String td    = PathUtils.todayDate();
        String ROOT  = ensureSlash(downloadsRoot) + PathUtils.winSafeName(rqId) + "\\";

        return List.of(
            TaskDef.builder("fin_sql", "專案設定是否更動到 SQL")
                .sub("建表 / SP 更新")
                .folder(ROOT + "SQL\\")
                .filename("01_xxx_create.sql  /  02_xxx_update.sql")
                .optional()
                .build(),

            TaskDef.builder("fin_doc", "廠商交付程式說明")
                .folder(ROOT)
                .filename("中華郵政_網路郵局系統_廠商交付程式說明(" + rqNum + ").docx")
                .warn("當天編輯")
                .build(),

            TaskDef.builder("fin_list", "程式碼清單")
                .folder(ROOT)
                .filename("list.txt")
                .optional()
                .build(),

            TaskDef.builder("fin_zip", "打包 ZIP")
                .sub("含所有版本修改的程式碼（含路徑，例 src/xxx/xxx/xxx.java）")
                .folder(ROOT)
                .filename(td + "_" + rqNum + ".zip")
                .build()
        );
    }

    // ──────────────────────────────────────────────
    // 6. sharedSVNTasks(rq) — 共用 SVN 任務（3 個）
    // ──────────────────────────────────────────────

    public static List<TaskDef> sharedSVNTasks(RQData rq) {
        return sharedSVNTasks(rq, DEFAULT_SVN_ROOT);
    }

    public static List<TaskDef> sharedSVNTasks(RQData rq, String svnRoot) {
        String rqId  = rq.getId();
        String rqNum = PathUtils.rqNumber(rqId);
        String proj  = (rq.getProjectNum() != null) ? rq.getProjectNum() : "{專案}";
        String SVN   = ensureSlash(svnRoot) + "10_增修維護階段\\變更需求單\\" + proj + "_" + PathUtils.winSafeName(rqId) + "\\003_維護服務紀錄單\\";

        return List.of(
            TaskDef.builder("svn_003_doc", "廠商交付程式說明 更新")
                .sub("由交付給客戶的「中華郵政_網路郵局系統_廠商交付程式說明(" + rqNum + ").docx」複製放入")
                .folder(SVN)
                .filename("中華郵政_網路郵局系統_廠商交付程式說明(" + rqNum + ").docx")
                .checkModTime()
                .build(),

            TaskDef.builder("svn_003_updrec", "程式更新紀錄單 更新")
                .folder(SVN)
                .filename(proj + "_" + rqNum + "_開放系統程式更新紀錄單_Vx.x.docx")
                .checkModTime()
                .build(),

            TaskDef.builder("svn_003_testrec", "程式測試報告單 更新")
                .folder(SVN)
                .filename(proj + "_" + rqNum + "_開放系統程式測試報告單_Vx.x.docx")
                .checkModTime()
                .build()
        );
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    private static String ensureSlash(String path) {
        if (path == null) return "\\";
        return path.endsWith("\\") ? path : path + "\\";
    }
}
