package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 進度計算（完整移植自 HTML 版 calcProgress / calcVersionProgress）。
 */
public final class ProgressCalc {

    private ProgressCalc() {}

    public record Progress(int done, int total) {
        public int percent() {
            return total == 0 ? 0 : Math.min(100, (int) Math.round(done * 100.0 / total));
        }
        public boolean isComplete() {
            return total > 0 && done >= total;
        }
    }

    /**
     * 計算整個 RQ 的進度（排除 optional 任務，與 HTML 版 calcProgress 一致）。
     */
    public static Progress calcProgress(RQData rq) {
        return calcProgress(rq, TaskFactory.DEFAULT_DOWNLOADS_ROOT, TaskFactory.DEFAULT_SVN_ROOT);
    }

    public static Progress calcProgress(RQData rq, String downloadsRoot, String svnRoot) {
        List<TaskDef> allTasks = allTasks(rq, downloadsRoot, svnRoot);
        Map<String, Boolean> checks = rq.getChecks();

        long total = allTasks.stream().filter(t -> !t.isOptional()).count();
        long done  = allTasks.stream().filter(t -> !t.isOptional() && Boolean.TRUE.equals(checks.get(t.getKey()))).count();
        return new Progress((int) done, (int) total);
    }

    /**
     * 計算指定版本的進度（開發 + 交付物 + SVN，與 HTML 版 calcVersionProgress 一致）。
     */
    public static Progress calcVersionProgress(RQData rq, int vIdx) {
        return calcVersionProgress(rq, vIdx, TaskFactory.DEFAULT_DOWNLOADS_ROOT, TaskFactory.DEFAULT_SVN_ROOT);
    }

    public static Progress calcVersionProgress(RQData rq, int vIdx, String downloadsRoot, String svnRoot) {
        List<RQVersion> versions = rq.getVersions();
        if (vIdx < 0 || vIdx >= versions.size()) return new Progress(0, 0);

        String vName = versions.get(vIdx).getName();
        List<TaskDef> tasks = new ArrayList<>();
        tasks.addAll(TaskFactory.versionDevTasks(vIdx));
        tasks.addAll(TaskFactory.versionDeliverables(vIdx, rq, vName, downloadsRoot));
        tasks.addAll(TaskFactory.versionSVNTasks(vIdx, vName, rq, svnRoot));

        Map<String, Boolean> checks = rq.getChecks();
        int total = tasks.size();
        long done = tasks.stream().filter(t -> Boolean.TRUE.equals(checks.get(t.getKey()))).count();
        return new Progress((int) done, total);
    }

    /** 取得所有任務清單（供磁碟掃描使用）。 */
    public static List<TaskDef> allTasks(RQData rq, String downloadsRoot, String svnRoot) {
        List<RQVersion> versions = rq.getVersions();
        List<TaskDef> tasks = new ArrayList<>(TaskFactory.sharedFlowTasks());

        for (int i = 0; i < versions.size(); i++) {
            String vName = versions.get(i).getName();
            tasks.addAll(TaskFactory.versionDevTasks(i));
            tasks.addAll(TaskFactory.versionDeliverables(i, rq, vName, downloadsRoot));
            tasks.addAll(TaskFactory.versionSVNTasks(i, vName, rq, svnRoot));
        }

        tasks.addAll(TaskFactory.finalDeliveryTasks(rq, downloadsRoot));
        tasks.addAll(TaskFactory.sharedSVNTasks(rq, svnRoot));
        return List.copyOf(tasks);
    }
}
