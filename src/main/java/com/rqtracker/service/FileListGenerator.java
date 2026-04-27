package com.rqtracker.service;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.util.PathUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 程式碼清單產生器（對應 HTML 的 generateFileList）。
 *
 * 依 versionFiles 清單生成 list.txt 的格式內容；
 * 同時掃描 D:\Systex\{rqId}\ 下的其他子目錄（安全性文件、SBOM、測試報告），
 * 將其中的檔案附加為額外段落。
 */
public final class FileListGenerator {

    private static final Logger LOG = Logger.getLogger(FileListGenerator.class.getName());

    /** 最後幾個特殊目錄的排列順序 */
    private static final List<String> FOLDER_ORDER = List.of("安全性文件", "SBOM", "測試報告");

    private FileListGenerator() {}

    /**
     * 產生 list.txt 的文字內容。
     *
     * @param rq          目前 RQ
     * @param downloadsRoot D:\Systex 根目錄
     * @return list.txt 全文（各版本段落 + 其他目錄段落）
     */
    public static String generate(RQData rq, String downloadsRoot) {
        String rqId    = rq.getId();
        String rqNum   = PathUtils.rqNumber(rqId);
        String rqRoot  = ensureSlash(downloadsRoot) + PathUtils.winSafeName(rqId) + "\\";

        List<String> sections = new ArrayList<>();

        // ── 版本改動程式段落 ──────────────────────────────────────────────────
        if (rq.getVersions() != null) {
            for (int i = 0; i < rq.getVersions().size(); i++) {
                RQVersion ver  = rq.getVersions().get(i);
                String vName   = ver.getName();
                String vid     = PathUtils.versionId(vName);
                String rawText = rq.getVersionFilesText(i);
                if (rawText == null || rawText.isBlank()) continue;

                List<String> manualFiles = Arrays.stream(rawText.split("\n"))
                    .map(l -> l.trim().replace('/', '\\'))
                    .filter(l -> !l.isBlank())
                    .map(l -> vid + "\\" + l)
                    .toList();
                if (manualFiles.isEmpty()) continue;

                sections.add("[" + vName + "]\n" + String.join("\n", manualFiles));
            }
        }

        // ── 磁碟掃描：其他子目錄（安全性文件、SBOM、測試報告...）──────────────
        // 先在 rqRoot 下找 {date}_{rqNum} zip 目錄
        Path rqRootPath = Paths.get(rqRoot);
        if (Files.isDirectory(rqRootPath)) {
            String zipDirName = findZipDir(rqRootPath, rqNum);
            String zipDir     = zipDirName != null ? (rqRoot + zipDirName + "\\") : null;

            List<String[]> otherDirs = new ArrayList<>();  // [name, fullPath]
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rqRootPath)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry) && !name.equals(zipDirName)) {
                        otherDirs.add(new String[]{name, entry.toString()});
                    }
                }
            } catch (IOException ignored) {}

            // 按排列順序排序
            otherDirs.sort((a, b) -> {
                int ai = FOLDER_ORDER.indexOf(a[0]);
                int bi = FOLDER_ORDER.indexOf(b[0]);
                if (ai >= 0 && bi >= 0) return ai - bi;
                if (ai >= 0) return -1;
                if (bi >= 0) return 1;
                return a[0].compareTo(b[0]);
            });

            for (String[] dir : otherDirs) {
                List<String> files = listFilesRecursive(Paths.get(dir[1]), dir[0]);
                if (!files.isEmpty()) {
                    sections.add("[" + dir[0] + "]\n" + String.join("\n", files));
                }
            }
        }

        return sections.isEmpty() ? "（尚無資料）" : String.join("\n\n", sections);
    }

    /**
     * 將 list.txt 寫入 D:\Systex\{rqId}\ 根目錄（與任務定義路徑一致）。
     *
     * @return 寫入成功的路徑；若目錄不存在或寫入失敗則回傳 null
     */
    public static Path writeListFile(RQData rq, String downloadsRoot, String content) {
        String rqId = rq.getId();
        Path rqRootPath = Paths.get(ensureSlash(downloadsRoot) + PathUtils.winSafeName(rqId));
        if (!Files.isDirectory(rqRootPath)) return null;

        Path listFile = rqRootPath.resolve("list.txt");
        try {
            Files.writeString(listFile, content, java.nio.charset.StandardCharsets.UTF_8);
            return listFile;
        } catch (IOException e) {
            LOG.warning("寫入 list.txt 失敗：" + e.getMessage());
            return null;
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private static String findZipDir(Path root, String rqNum) {
        String safePat = rqNum.replace("\\", "\\\\").replace(".", "\\.");
        java.util.regex.Pattern zipPat = java.util.regex.Pattern.compile(
            "^\\d{8}_" + safePat + "$", java.util.regex.Pattern.CASE_INSENSITIVE);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && zipPat.matcher(entry.getFileName().toString()).matches()) {
                    return entry.getFileName().toString();
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    /** 遞迴列出目錄下所有檔案路徑（相對於 prefix），已排序 */
    private static List<String> listFilesRecursive(Path dir, String prefix) {
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .forEach(p -> {
                    String rel = dir.getParent() == null
                        ? p.toString()
                        : dir.getParent().relativize(p).toString();
                    result.add(rel);
                });
        } catch (IOException ignored) {}
        Collections.sort(result);
        return result;
    }

    private static String ensureSlash(String path) {
        return (path == null) ? "" : (path.endsWith("\\") ? path : path + "\\");
    }
}
