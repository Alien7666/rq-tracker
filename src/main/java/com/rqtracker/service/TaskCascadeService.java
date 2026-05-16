package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.util.DateTimeUtils;

/**
 * 任務勾選連動規則。
 */
public final class TaskCascadeService {

    private TaskCascadeService() {}

    /**
     * 依「交付給客戶」欄位目前的勾選狀態，回填同版本的「開發流程」。
     */
    public static boolean applyDeliveryToDevelopment(RQData rq, String timestamp) {
        if (rq == null || rq.getVersions() == null) return false;
        String ts = (timestamp == null || timestamp.isBlank()) ? DateTimeUtils.nowZhTW() : timestamp;
        boolean changed = false;
        for (int v = 0; v < rq.getVersions().size(); v++) {
            changed |= applyDeliveryToDevelopment(rq, v, ts);
        }
        return changed;
    }

    public static boolean applyDeliveryToDevelopment(RQData rq, int versionIndex, String timestamp) {
        if (rq == null || versionIndex < 0) return false;
        String ts = (timestamp == null || timestamp.isBlank()) ? DateTimeUtils.nowZhTW() : timestamp;
        String p = "v" + versionIndex + "_";

        boolean codeOk       = rq.isChecked(p + "del_code");
        boolean fortifyOk    = rq.isChecked(p + "del_fortify");
        boolean testrptOk    = rq.isChecked(p + "del_testrpt");
        boolean vulnscanOk   = rq.isChecked(p + "del_vulnscan");
        boolean zapexclOk    = rq.isChecked(p + "del_zapexcl");
        boolean pentestOk    = rq.isChecked(p + "del_pentest");
        boolean pentestCsvOk = rq.isChecked(p + "del_pentestcsv");
        boolean zapBoth      = vulnscanOk && zapexclOk;

        boolean changed = false;

        if (codeOk) {
            changed |= checkAll(rq, ts, "sf_01", "sf_02", p + "dev_03");
        }

        if (fortifyOk) {
            changed |= checkAll(rq, ts,
                "sf_01", "sf_02", p + "dev_03", p + "dev_04", p + "dev_05");
        }

        if (testrptOk) {
            changed |= checkAll(rq, ts, p + "dev_07b");
        }

        if (vulnscanOk) {
            changed |= checkAll(rq, ts, p + "dev_09");
        }

        if (pentestOk || pentestCsvOk) {
            changed |= checkAll(rq, ts, p + "dev_09b");
        }

        if (zapBoth) {
            changed |= checkAll(rq, ts,
                "sf_01", "sf_02",
                p + "dev_03", p + "dev_04", p + "dev_05", p + "dev_05b",
                p + "dev_06", p + "dev_07", p + "dev_08");
        }

        return changed;
    }

    private static boolean checkAll(RQData rq, String timestamp, String... keys) {
        boolean changed = false;
        for (String key : keys) {
            if (!rq.isChecked(key)) {
                rq.check(key, timestamp);
                changed = true;
            }
        }
        return changed;
    }
}
