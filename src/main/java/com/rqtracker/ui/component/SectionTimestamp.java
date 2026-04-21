package com.rqtracker.ui.component;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;
import com.rqtracker.util.DateTimeUtils;

import java.util.List;
import java.util.Map;

/**
 * 區段完成時間戳工具（對應 HTML 的 updateSectionTimestamps / sectionTimeBadge）。
 */
public final class SectionTimestamp {

    private SectionTimestamp() {}

    /**
     * 依勾選狀態更新 rq.sectionDone（完整移植自 HTML 版 updateSectionTimestamps）。
     * 若某區段所有必填任務都勾選 → 記錄完成時間；若有未勾 → 清除時間。
     */
    public static void update(RQData rq,
                              java.util.function.Function<Integer, List<TaskDef>> devTasksFor,
                              java.util.function.Function<Integer, List<TaskDef>> svnTasksFor,
                              java.util.function.Function<Integer, List<TaskDef>> delTasksFor,
                              List<TaskDef> finTasks,
                              List<TaskDef> sharedSvnTasks) {
        Map<String, String> sectionDone = rq.getSectionDone();
        Map<String, Boolean> checks = rq.getChecks();
        String now = DateTimeUtils.nowZhTW();

        for (int i = 0; i < rq.getVersions().size(); i++) {
            checkSection(sectionDone, checks, "dev_v" + i, devTasksFor.apply(i), now);
            checkSection(sectionDone, checks, "svn_v" + i, svnTasksFor.apply(i), now);
            checkSection(sectionDone, checks, "del_v" + i, delTasksFor.apply(i), now);
        }
        checkSection(sectionDone, checks, "shared_fin", finTasks, now);
        checkSection(sectionDone, checks, "shared_svn", sharedSvnTasks, now);
    }

    /** 取得區段完成時間戳顯示文字（可為 null）。 */
    public static String get(RQData rq, String sectionKey) {
        return rq.getSectionDone().get(sectionKey);
    }

    // ── 私有 ──────────────────────────────────────────────────────

    private static void checkSection(Map<String, String> sectionDone,
                                     Map<String, Boolean> checks,
                                     String key,
                                     List<TaskDef> tasks,
                                     String now) {
        List<TaskDef> required = tasks.stream().filter(t -> !t.isOptional()).toList();
        boolean allDone = !required.isEmpty() && required.stream()
            .allMatch(t -> Boolean.TRUE.equals(checks.get(t.getKey())));

        if (allDone && !sectionDone.containsKey(key)) {
            sectionDone.put(key, now);
        } else if (!allDone) {
            sectionDone.remove(key);
        }
    }
}
