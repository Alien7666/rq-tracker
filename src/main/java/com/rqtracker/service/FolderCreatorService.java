package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * 批量建立 D:\Systex 下的 RQ 資料夾結構（對應 HTML 的 createRQFolders）。
 * 只建立交付給客戶（downloadsRoot）相關的資料夾。
 */
public final class FolderCreatorService {

    private static final Logger LOG = Logger.getLogger(FolderCreatorService.class.getName());

    private FolderCreatorService() {}

    /**
     * 建立指定 RQ 所有必填交付任務對應的資料夾。
     *
     * @return [created, failed] 建立成功數 / 失敗數
     */
    public static int[] createFolders(RQData rq, String downloadsRoot) {
        String prefix = downloadsRoot.endsWith("\\") ? downloadsRoot : downloadsRoot + "\\";

        // 收集所有需要建立的資料夾路徑（使用 getCreateFolder 以對應 del_code 的子路徑）
        Set<String> folderSet = new LinkedHashSet<>();

        List<TaskDef> all = new ArrayList<>();
        all.addAll(TaskFactory.sharedFlowTasks());
        if (rq.getVersions() != null) {
            for (int i = 0; i < rq.getVersions().size(); i++) {
                String vName = rq.getVersions().get(i).getName();
                all.addAll(TaskFactory.versionDeliverables(i, rq, vName, downloadsRoot));
                // versionSVNTasks 資料夾在 SVN 根目錄，不在 downloadsRoot，跳過
            }
        }
        all.addAll(TaskFactory.finalDeliveryTasks(rq, downloadsRoot));

        for (TaskDef t : all) {
            if (t.isOptional()) continue;
            String folder = t.getCreateFolder();  // fallback to folder if createFolder null
            if (folder == null) continue;
            // 只建立 downloadsRoot 下的路徑
            if (folder.toLowerCase().startsWith(prefix.toLowerCase())) {
                // 去掉末尾反斜線
                folderSet.add(folder.endsWith("\\") ? folder.substring(0, folder.length() - 1) : folder);
            }
        }

        int created = 0, failed = 0;
        for (String folder : folderSet) {
            try {
                Path path = Paths.get(folder);
                Files.createDirectories(path);
                created++;
                LOG.fine("建立資料夾：" + folder);
            } catch (IOException e) {
                failed++;
                LOG.warning("無法建立資料夾 " + folder + "：" + e.getMessage());
            }
        }
        return new int[]{created, failed};
    }
}
