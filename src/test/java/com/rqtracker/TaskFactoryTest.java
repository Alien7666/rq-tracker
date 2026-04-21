package com.rqtracker;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskDef;
import com.rqtracker.service.TaskFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskFactory 單元測試：驗證 6 個任務函數生成的 key 格式與 HTML 版完全一致。
 */
class TaskFactoryTest {

    private RQData rq;

    @BeforeEach
    void setUp() {
        rq = new RQData();
        rq.setId("RQ100051742_網路郵局新增欄位");
        rq.setProjectNum("POSMS");
        rq.setVersions(List.of(
            new RQVersion("網路郵局中文版 pstID"),
            new RQVersion("網路郵局英文版 pstEN")
        ));
    }

    // ────────────────────────────────────────
    // sharedFlowTasks
    // ────────────────────────────────────────

    @Test
    void sharedFlowTasks_keys() {
        List<String> keys = TaskFactory.sharedFlowTasks().stream().map(TaskDef::getKey).toList();
        assertEquals(List.of("sf_01", "sf_02"), keys);
    }

    // ────────────────────────────────────────
    // versionDevTasks
    // ────────────────────────────────────────

    @Test
    void versionDevTasks_keysForVersion0() {
        List<String> keys = TaskFactory.versionDevTasks(0).stream().map(TaskDef::getKey).toList();
        assertEquals(List.of(
            "v0_dev_03", "v0_dev_04", "v0_dev_05", "v0_dev_05b",
            "v0_dev_06", "v0_dev_07", "v0_dev_07b", "v0_dev_08",
            "v0_dev_09", "v0_dev_09b"
        ), keys);
    }

    @Test
    void versionDevTasks_keysForVersion1() {
        List<String> keys = TaskFactory.versionDevTasks(1).stream().map(TaskDef::getKey).toList();
        assertTrue(keys.stream().allMatch(k -> k.startsWith("v1_dev_")));
        assertEquals(10, keys.size());
    }

    // ────────────────────────────────────────
    // versionDeliverables
    // ────────────────────────────────────────

    @Test
    void versionDeliverables_keysForVersion0() {
        List<String> keys = TaskFactory.versionDeliverables(0, rq, "網路郵局中文版 pstID")
            .stream().map(TaskDef::getKey).toList();
        assertEquals(List.of(
            "v0_del_code", "v0_del_fortify", "v0_del_testrpt",
            "v0_del_vulnscan", "v0_del_zapexcl", "v0_del_pentest",
            "v0_del_pentestcsv", "v0_del_sbom"
        ), keys);
    }

    @Test
    void versionDeliverables_codeTaskHasCheckHasFiles() {
        TaskDef codeTask = TaskFactory.versionDeliverables(0, rq, "網路郵局中文版 pstID")
            .stream().filter(t -> t.getKey().endsWith("_del_code")).findFirst().orElseThrow();
        assertTrue(codeTask.isCheckHasFiles());
        assertNotNull(codeTask.getCreateFolder());
    }

    @Test
    void versionDeliverables_warnTasksMarked() {
        long warnCount = TaskFactory.versionDeliverables(0, rq, "網路郵局中文版 pstID")
            .stream().filter(TaskDef::isWarn).count();
        assertEquals(5, warnCount); // vulnscan, zapexcl, pentest, pentestcsv, sbom
    }

    @Test
    void versionDeliverables_folderContainsRqId() {
        TaskDef fortify = TaskFactory.versionDeliverables(0, rq, "網路郵局中文版 pstID")
            .stream().filter(t -> t.getKey().equals("v0_del_fortify")).findFirst().orElseThrow();
        // 資料夾路徑應包含 rq id（winSafeName 後的版本）
        assertTrue(fortify.getFolder().contains("RQ100051742_網路郵局新增欄位"));
    }

    // ────────────────────────────────────────
    // versionSVNTasks
    // ────────────────────────────────────────

    @Test
    void versionSVNTasks_keysForVersion0() {
        List<String> keys = TaskFactory.versionSVNTasks(0, "網路郵局中文版 pstID", rq)
            .stream().map(TaskDef::getKey).toList();
        assertEquals(List.of(
            "v0_svn_005", "v0_svn_004", "v0_svn_zap1", "v0_svn_zap2", "v0_svn_sbom"
        ), keys);
    }

    @Test
    void versionSVNTasks_sbomFolderContainsSystemName() {
        TaskDef sbom = TaskFactory.versionSVNTasks(0, "網路郵局中文版 pstID", rq)
            .stream().filter(t -> t.getKey().endsWith("_svn_sbom")).findFirst().orElseThrow();
        assertTrue(sbom.getFolder().contains("1_網路郵局中文版"));
    }

    // ────────────────────────────────────────
    // finalDeliveryTasks
    // ────────────────────────────────────────

    @Test
    void finalDeliveryTasks_keys() {
        List<String> keys = TaskFactory.finalDeliveryTasks(rq)
            .stream().map(TaskDef::getKey).toList();
        assertEquals(List.of("fin_sql", "fin_doc", "fin_list", "fin_zip"), keys);
    }

    @Test
    void finalDeliveryTasks_sqlAndListAreOptional() {
        List<TaskDef> tasks = TaskFactory.finalDeliveryTasks(rq);
        assertTrue(tasks.stream().filter(t -> t.getKey().equals("fin_sql")).findFirst().orElseThrow().isOptional());
        assertTrue(tasks.stream().filter(t -> t.getKey().equals("fin_list")).findFirst().orElseThrow().isOptional());
        assertFalse(tasks.stream().filter(t -> t.getKey().equals("fin_doc")).findFirst().orElseThrow().isOptional());
        assertFalse(tasks.stream().filter(t -> t.getKey().equals("fin_zip")).findFirst().orElseThrow().isOptional());
    }

    @Test
    void finalDeliveryTasks_docFilenameContainsRqNum() {
        TaskDef doc = TaskFactory.finalDeliveryTasks(rq)
            .stream().filter(t -> t.getKey().equals("fin_doc")).findFirst().orElseThrow();
        assertTrue(doc.getFilename().contains("RQ100051742"));
    }

    // ────────────────────────────────────────
    // sharedSVNTasks
    // ────────────────────────────────────────

    @Test
    void sharedSVNTasks_keys() {
        List<String> keys = TaskFactory.sharedSVNTasks(rq)
            .stream().map(TaskDef::getKey).toList();
        assertEquals(List.of("svn_003_doc", "svn_003_updrec", "svn_003_testrec"), keys);
    }

    @Test
    void sharedSVNTasks_checkModTimeSet() {
        TaskFactory.sharedSVNTasks(rq).forEach(t ->
            assertTrue(t.isCheckModTime(), t.getKey() + " 應設定 checkModTime=true")
        );
    }

    @Test
    void sharedSVNTasks_folderContainsProjectAndRqId() {
        TaskDef doc = TaskFactory.sharedSVNTasks(rq).get(0);
        assertTrue(doc.getFolder().contains("POSMS_RQ100051742_網路郵局新增欄位"));
        assertTrue(doc.getFolder().contains("003_維護服務紀錄單"));
    }
}
