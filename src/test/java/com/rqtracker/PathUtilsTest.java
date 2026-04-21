package com.rqtracker;

import com.rqtracker.util.PathUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PathUtils 單元測試（驗證移植自 HTML 版的工具函數正確性）。
 */
class PathUtilsTest {

    @Test
    void rqNumber_extractsCorrectly() {
        assertEquals("RQ100051742", PathUtils.rqNumber("RQ100051742_請於網路郵局新增推薦員工編號欄位"));
        assertEquals("RQ100051742", PathUtils.rqNumber("RQ100051742"));
        assertEquals("rq100051742", PathUtils.rqNumber("rq100051742_test")); // 大小寫不敏感但保留原始大小寫
        // 無 RQ 前綴則回傳完整原始字串（與 HTML 版 rqNumber 行為一致）
        assertEquals("unknown_no_rq_prefix", PathUtils.rqNumber("unknown_no_rq_prefix"));
    }

    @Test
    void versionId_takesLastWord() {
        assertEquals("pstID",   PathUtils.versionId("網路郵局中文版 pstID"));
        assertEquals("pstEN",   PathUtils.versionId("網路郵局英文版 pstEN"));
        assertEquals("pstID",   PathUtils.versionId("pstID"));
        assertEquals("pstacce", PathUtils.versionId("網路郵局友善專區 pstacce"));
        assertEquals("v1",      PathUtils.versionId("版本 v1"));
    }

    @Test
    void versionDisplayName_removesLastWord() {
        assertEquals("網路郵局中文版", PathUtils.versionDisplayName("網路郵局中文版 pstID"));
        assertEquals("pstID",          PathUtils.versionDisplayName("pstID")); // 無空格，回傳原值
        assertEquals("A B",            PathUtils.versionDisplayName("A B C"));
    }

    @Test
    void sbomSystemFolder_mapsCorrectly() {
        assertEquals("1_網路郵局中文版",  PathUtils.sbomSystemFolder("pstID"));
        assertEquals("2_網路郵局英文版",  PathUtils.sbomSystemFolder("pstEN"));
        assertEquals("3_網路郵局友善專區", PathUtils.sbomSystemFolder("pstacce"));
        assertEquals("4_網路郵局後台",   PathUtils.sbomSystemFolder("pstsam"));
        assertEquals("8_郵件查詢前台",   PathUtils.sbomSystemFolder("pstmail"));
        assertEquals("{系統_unknown}",   PathUtils.sbomSystemFolder("unknown"));
    }

    @Test
    void winSafeName_removesIllegalChars() {
        // 無非法字元，原樣保留（含 _ 與中文）
        assertEquals("RQ100051742_名稱",  PathUtils.winSafeName("RQ100051742_名稱"));
        // / 替換為 _，其餘非法字元（* ? " < > |）移除
        assertEquals("RQ_名稱",           PathUtils.winSafeName("RQ/名*稱?\"<>|"));
        // 反斜線與冒號（路徑字元）保留
        assertEquals("D:\\Systex\\",      PathUtils.winSafeName("D:\\Systex\\"));
    }

    @Test
    void todayDate_returnsEightDigits() {
        String date = PathUtils.todayDate();
        assertEquals(8, date.length());
        assertTrue(date.matches("\\d{8}"), "應為 YYYYMMDD 格式：" + date);
    }
}
