package com.rqtracker;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskResult;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DiskScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiskScanServiceTest {

    private RQData rq;
    private DiskScanService diskScanService;

    @BeforeEach
    void setUp() {
        rq = new RQData();
        rq.setId("RQ100051742_測試");
        rq.setProjectNum("POSMS");
        rq.setVersions(List.of(new RQVersion("網路郵局中文版 pstID")));
        diskScanService = new DiskScanService(new AppConfig());
    }

    @Test
    void autoCheck_checkedDeliveryColumnBackfillsDevelopmentFlow() {
        String ts = "2026/05/16 10:00";
        rq.check("v0_del_code", ts);
        rq.check("v0_del_fortify", ts);
        rq.check("v0_del_testrpt", ts);
        rq.check("v0_del_vulnscan", ts);
        rq.check("v0_del_zapexcl", ts);
        rq.check("v0_del_pentest", ts);
        rq.check("v0_del_pentestcsv", ts);
        rq.check("v0_del_sbom", ts);

        boolean changed = diskScanService.autoCheck(rq, Map.of());

        assertTrue(changed);
        assertAll(
            () -> assertTrue(rq.isChecked("v0_dev_03")),
            () -> assertTrue(rq.isChecked("v0_dev_04")),
            () -> assertTrue(rq.isChecked("v0_dev_05")),
            () -> assertTrue(rq.isChecked("v0_dev_05b")),
            () -> assertTrue(rq.isChecked("v0_dev_06")),
            () -> assertTrue(rq.isChecked("v0_dev_07")),
            () -> assertTrue(rq.isChecked("v0_dev_07b")),
            () -> assertTrue(rq.isChecked("v0_dev_08")),
            () -> assertTrue(rq.isChecked("v0_dev_09")),
            () -> assertTrue(rq.isChecked("v0_dev_09b"))
        );
    }

    @Test
    void autoCheck_codeDeliveryFileBackfillsCodeDevelopmentTask() {
        boolean changed = diskScanService.autoCheck(rq, Map.of(
            "v0_del_code", TaskResult.file(1, 1)
        ));

        assertTrue(changed);
        assertTrue(rq.isChecked("v0_del_code"));
        assertTrue(rq.isChecked("v0_dev_03"));
        assertTrue(rq.isChecked("sf_01"));
        assertTrue(rq.isChecked("sf_02"));
    }
}
