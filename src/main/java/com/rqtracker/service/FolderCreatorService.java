package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;
import com.rqtracker.util.PathUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 批量建立 D:\Systex 下的 RQ 資料夾結構（對應 HTML 的 createRQFolders）。
 * 只建立交付給客戶（downloadsRoot）相關的資料夾。
 * 若已有相同規則但日期不同的舊資料夾，會先更名為今日日期，避免重複建立。
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

        // Step 1: 先處理日期前綴資料夾的更名（{td}_{rqNum} 與 ZAP/SBOM 的 {td}）
        renameDateFoldersForRQ(rq, downloadsRoot);

        // Step 2: 收集所有需要建立的資料夾路徑（使用 getCreateFolder 以對應 del_code 的子路徑）
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

    // ────────────────────────────────────────────────────────────────────
    // 日期資料夾更名邏輯
    // ────────────────────────────────────────────────────────────────────

    /**
     * 對單一 RQ：掃描 downloadsRoot\{rqId}\，若發現 {oldDate}_{rqNum} 但今日 {td}_{rqNum} 不存在，
     * 則更名為今日；避免每日重新建立空殼資料夾。
     */
    private static void renameDateFoldersForRQ(RQData rq, String downloadsRoot) {
        String rqId  = rq.getId();
        String rqNum = PathUtils.rqNumber(rqId);
        String td    = PathUtils.todayDate();
        String prefix = downloadsRoot.endsWith("\\") ? downloadsRoot : downloadsRoot + "\\";

        Path parent = Paths.get(prefix + PathUtils.winSafeName(rqId));
        if (!Files.isDirectory(parent)) return;

        Pattern pat = Pattern.compile("^(\\d{8})_" + Pattern.quote(rqNum) + "$");
        renameDateFolder(parent, pat, td + "_" + rqNum, td);
    }

    /**
     * 在 parent 中尋找符合 pat 的子資料夾並更名為 targetName。
     * 規則：
     *   - 若 parent\targetName 已存在：不動（使用者已建立今日資料夾）
     *   - 若僅一個符合 pat 的舊資料夾：更名為 targetName
     *   - 若多個符合：取最早的更名，其餘留給使用者手動處理（避免合併造成資料覆蓋）
     *   - 若舊資料夾日期分組剛好等於 todayDate：跳過（已是今日）
     */
    private static void renameDateFolder(Path parent, Pattern pat, String targetName, String todayDate) {
        Path target = parent.resolve(targetName);
        if (Files.exists(target)) return;

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String name = p.getFileName().toString();
                var m = pat.matcher(name);
                if (m.matches() && !todayDate.equals(m.group(1))) {
                    candidates.add(p);
                }
            }
        } catch (IOException e) {
            LOG.warning("掃描舊日期資料夾失敗 " + parent + "：" + e.getMessage());
            return;
        }
        if (candidates.isEmpty()) return;

        // 多個時取檔名最小（最早日期）作為被更名的主體
        candidates.sort(Comparator.comparing(p -> p.getFileName().toString()));
        Path src = candidates.get(0);
        try {
            Files.move(src, target);
            LOG.info("更名舊資料夾：" + src.getFileName() + " → " + targetName);
            if (candidates.size() > 1) {
                LOG.warning("發現多個舊日期資料夾於 " + parent
                    + "，只更名 " + src.getFileName() + "，其餘請手動清理");
            }
        } catch (IOException e) {
            LOG.warning("更名資料夾失敗 " + src + " → " + target + "：" + e.getMessage());
        }
    }
}
