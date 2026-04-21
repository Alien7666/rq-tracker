package com.rqtracker;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.service.ProgressCalc;
import com.rqtracker.service.TaskFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProgressCalc 單元測試。
 */
class ProgressCalcTest {

    private RQData rq;

    @BeforeEach
    void setUp() {
        rq = new RQData();
        rq.setId("RQ100051742_測試");
        rq.setProjectNum("POSMS");
        rq.setVersions(List.of(new RQVersion("網路郵局中文版 pstID")));
        rq.setChecks(new HashMap<>());
    }

    @Test
    void calcProgress_noChecks_doneIsZero() {
        ProgressCalc.Progress p = ProgressCalc.calcProgress(rq);
        assertEquals(0, p.done());
        assertTrue(p.total() > 0, "應有任務");
    }

    @Test
    void calcProgress_allNonOptionalChecked_isComplete() {
        // 取得所有非 optional 任務並全部打勾
        Map<String, Boolean> checks = new HashMap<>();
        ProgressCalc.allTasks(rq, TaskFactory.DEFAULT_DOWNLOADS_ROOT, TaskFactory.DEFAULT_SVN_ROOT)
            .stream()
            .filter(t -> !t.isOptional())
            .forEach(t -> checks.put(t.getKey(), true));
        rq.setChecks(checks);

        ProgressCalc.Progress p = ProgressCalc.calcProgress(rq);
        assertTrue(p.isComplete());
        assertEquals(100, p.percent());
    }

    @Test
    void calcProgress_optionalTasksNotCounted() {
        // 只勾選 optional 任務，done 仍應為 0
        Map<String, Boolean> checks = new HashMap<>();
        checks.put("fin_sql", true);
        checks.put("fin_list", true);
        rq.setChecks(checks);

        ProgressCalc.Progress p = ProgressCalc.calcProgress(rq);
        assertEquals(0, p.done());
    }

    @Test
    void calcProgress_sf01Checked_doneIsOne() {
        Map<String, Boolean> checks = new HashMap<>();
        checks.put("sf_01", true);
        rq.setChecks(checks);

        ProgressCalc.Progress p = ProgressCalc.calcProgress(rq);
        assertEquals(1, p.done());
    }

    @Test
    void calcVersionProgress_noChecks_doneIsZero() {
        ProgressCalc.Progress p = ProgressCalc.calcVersionProgress(rq, 0);
        assertEquals(0, p.done());
        // version 任務 = dev(10) + deliverables(8) + svn(5) = 23
        assertEquals(23, p.total());
    }

    @Test
    void calcVersionProgress_invalidIndex_returnsZero() {
        ProgressCalc.Progress p = ProgressCalc.calcVersionProgress(rq, 5);
        assertEquals(0, p.done());
        assertEquals(0, p.total());
    }

    @Test
    void progress_percent_roundsCorrectly() {
        ProgressCalc.Progress p = new ProgressCalc.Progress(1, 3);
        assertEquals(33, p.percent());
    }

    @Test
    void progress_percent_zeroTotalReturnsZero() {
        ProgressCalc.Progress p = new ProgressCalc.Progress(0, 0);
        assertEquals(0, p.percent());
    }
}
