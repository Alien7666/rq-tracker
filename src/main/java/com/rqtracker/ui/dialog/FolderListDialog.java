package com.rqtracker.ui.dialog;

import com.rqtracker.model.RQData;
import com.rqtracker.model.TaskDef;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DataStore;
import com.rqtracker.service.TaskFactory;
import com.rqtracker.util.ClipboardUtils;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.DialogHelper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.*;

/**
 * 資料夾清單視窗（對應 HTML 的 openFolderView / closeFolderView）。
 *
 * 按 folder 分組，分兩段：「交付給客戶」和「SVN 內部」。
 * 每個檔案列可以勾選/取消勾選，並支援複製檔名與完整路徑。
 */
public class FolderListDialog {

    private static final record FileEntry(String filename, String key, boolean warn, boolean optional) {}

    private final Stage     dialog;
    private final DataStore dataStore;
    private final AppConfig appConfig;
    private final String    rqId;

    /** 勾選狀態變動後的回呼 */
    private Runnable onCheckChanged;

    /** 列表容器（勾選後重建） */
    private VBox contentBox;

    public FolderListDialog(Window owner, DataStore dataStore, AppConfig appConfig, String rqId) {
        this.dataStore = dataStore;
        this.appConfig = appConfig;
        this.rqId      = rqId;

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("資料夾清單");
        dialog.setMinWidth(600);
        dialog.setMinHeight(400);

        // ── Modal 標題 ────────────────────────────────────────────────
        Label titleLabel = new Label("📂 資料夾清單");
        titleLabel.getStyleClass().add("modal-title");

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close");
        closeBtn.setOnAction(e -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox modalHeader = new HBox(titleLabel, spacer, closeBtn);
        modalHeader.getStyleClass().add("modal-header");
        modalHeader.setAlignment(Pos.CENTER_LEFT);
        modalHeader.setPadding(new Insets(14, 24, 14, 24));

        // ── 捲動內容 ─────────────────────────────────────────────────
        contentBox = new VBox(12);
        contentBox.setPadding(new Insets(16, 24, 16, 24));

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox modal = new VBox(modalHeader, new Separator(), scroll);
        modal.getStyleClass().add("modal");
        modal.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        modal.setPrefWidth(740);
        modal.setPrefHeight(580);

        Scene scene = new Scene(modal);
        DialogHelper.applyTheme(scene, getClass());
        dialog.setScene(scene);
        DialogHelper.makeMovable(dialog, modalHeader);
        DialogHelper.makeResizable(dialog, modal);

        buildContent();
    }

    public void setOnCheckChanged(Runnable cb) { this.onCheckChanged = cb; }

    public void show() { dialog.show(); }
    public Stage getStage() { return dialog; }

    // ──────────────────────────────────────────────────────────────

    private void buildContent() {
        contentBox.getChildren().clear();
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;

        String dlRoot  = appConfig.getDownloadsRoot();
        String svnRoot = appConfig.getSvnRoot();

        // delivMap: folder → list of FileEntry (交付給客戶)
        LinkedHashMap<String, List<FileEntry>> delivMap = new LinkedHashMap<>();
        // svnMap: folder → list of FileEntry (SVN 內部)
        LinkedHashMap<String, List<FileEntry>> svnMap   = new LinkedHashMap<>();

        // 版本任務
        if (rq.getVersions() != null) {
            for (int i = 0; i < rq.getVersions().size(); i++) {
                String vName = rq.getVersions().get(i).getName();
                TaskFactory.versionDeliverables(i, rq, vName, dlRoot).forEach(t -> addToMap(delivMap, t));
                TaskFactory.versionSVNTasks(i, vName, rq, svnRoot).forEach(t -> addToMap(svnMap, t));
            }
        }
        // 共用最終
        TaskFactory.finalDeliveryTasks(rq, dlRoot).forEach(t -> addToMap(delivMap, t));
        TaskFactory.sharedSVNTasks(rq, svnRoot).forEach(t -> addToMap(svnMap, t));

        if (delivMap.isEmpty() && svnMap.isEmpty()) {
            Label empty = new Label("尚無版本資料，請先新增版本。");
            empty.getStyleClass().add("empty-state-desc");
            contentBox.getChildren().add(empty);
            return;
        }

        if (!delivMap.isEmpty()) {
            Label sec = new Label("📂 交付給客戶");
            sec.getStyleClass().add("folder-section-title");
            contentBox.getChildren().add(sec);
            delivMap.forEach((folder, files) ->
                contentBox.getChildren().add(buildFolderGroup(rq, folder, files)));
        }
        if (!svnMap.isEmpty()) {
            Label sec = new Label("📂 SVN 內部");
            sec.getStyleClass().add("folder-section-title");
            contentBox.getChildren().add(sec);
            svnMap.forEach((folder, files) ->
                contentBox.getChildren().add(buildFolderGroup(rq, folder, files)));
        }
    }

    private void addToMap(LinkedHashMap<String, List<FileEntry>> map, TaskDef t) {
        String folder = t.getFolder();
        if (folder == null) return;

        // 同一個 task 可能有多個檔名（用 + 分隔）
        String filename = t.getFilename();
        if (filename != null) {
            String[] parts = filename.split("\\s*\\+\\s*");
            for (String part : parts) {
                String fn = part.trim();
                if (fn.isBlank()) continue;
                map.computeIfAbsent(folder, k -> new ArrayList<>())
                   .add(new FileEntry(fn, t.getKey(), t.isWarn(), t.isOptional()));
            }
        } else {
            // folder-only task（checkHasFiles）
            map.computeIfAbsent(folder, k -> new ArrayList<>())
               .add(new FileEntry(null, t.getKey(), t.isWarn(), t.isOptional()));
        }
    }

    private VBox buildFolderGroup(RQData rq, String folder, List<FileEntry> files) {
        // 資料夾路徑列
        Label folderLabel = new Label(folder);
        folderLabel.getStyleClass().add("folder-path-text");
        folderLabel.setWrapText(true);
        HBox.setHgrow(folderLabel, Priority.ALWAYS);

        Button copyFolderBtn = copyButton(folder);

        HBox pathRow = new HBox(8, folderLabel, copyFolderBtn);
        pathRow.getStyleClass().add("folder-path-row");
        pathRow.setAlignment(Pos.CENTER_LEFT);

        // 檔案列清單
        VBox fileRows = new VBox(0);
        files.forEach(entry -> fileRows.getChildren().add(buildFileRow(rq, folder, entry)));

        VBox group = new VBox(pathRow, fileRows);
        group.getStyleClass().add("folder-group");
        return group;
    }

    private HBox buildFileRow(RQData rq, String folder, FileEntry entry) {
        boolean checked = (entry.key() != null) && Boolean.TRUE.equals(rq.getChecks().get(entry.key()));

        // 勾選方格
        Label checkBox = new Label(checked ? "✓" : "");
        checkBox.getStyleClass().addAll("check-box");
        if (checked) checkBox.getStyleClass().add("checked");
        checkBox.setMinSize(20, 20);
        checkBox.setMaxSize(20, 20);
        checkBox.setAlignment(Pos.CENTER);

        HBox row = new HBox(8);
        row.getStyleClass().add("folder-file-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        if (entry.filename() != null) {
            Label fnLabel = new Label(entry.filename());
            fnLabel.getStyleClass().add("folder-path-text");
            HBox.setHgrow(fnLabel, Priority.ALWAYS);

            if (entry.warn()) {
                Label warnTag = new Label("⚠ 當天");
                warnTag.getStyleClass().add("warn-tag");
                row.getChildren().addAll(checkBox, fnLabel, warnTag);
            } else if (entry.optional()) {
                Label optTag = new Label("選填");
                optTag.getStyleClass().add("optional-tag");
                row.getChildren().addAll(checkBox, fnLabel, optTag);
            } else {
                row.getChildren().addAll(checkBox, fnLabel);
            }

            // 複製檔名、複製完整路徑
            String fullPath = folder.replaceAll("\\\\$", "") + "\\" + entry.filename();
            row.getChildren().addAll(
                copyButton(entry.filename()),
                copyButton(fullPath)
            );
        } else {
            // 只有資料夾
            Label lbl = new Label("（資料夾本身）");
            lbl.getStyleClass().add("folder-path-text");
            HBox.setHgrow(lbl, Priority.ALWAYS);
            row.getChildren().addAll(checkBox, lbl);
        }

        // 點擊整列 → 勾選（未勾）或 noOp（已勾，需透過方格確認）
        row.setOnMouseClicked(e -> {
            if (entry.key() == null) return;
            RQData latest = dataStore.getRQ(rqId);
            if (latest == null) return;
            if (!Boolean.TRUE.equals(latest.getChecks().get(entry.key()))) {
                latest.getChecks().put(entry.key(), true);
                latest.getTimestamps().put(entry.key(), DateTimeUtils.nowZhTW());
                dataStore.saveRQ(rqId, latest);
                if (onCheckChanged != null) onCheckChanged.run();
                buildContent();
            }
        });

        checkBox.setOnMouseClicked(e -> {
            e.consume();
            if (entry.key() == null) return;
            RQData latest = dataStore.getRQ(rqId);
            if (latest == null) return;
            boolean wasChecked = Boolean.TRUE.equals(latest.getChecks().get(entry.key()));
            latest.getChecks().put(entry.key(), !wasChecked);
            if (!wasChecked) {
                latest.getTimestamps().put(entry.key(), DateTimeUtils.nowZhTW());
            } else {
                latest.getTimestamps().remove(entry.key());
            }
            dataStore.saveRQ(rqId, latest);
            if (onCheckChanged != null) onCheckChanged.run();
            buildContent();
        });

        return row;
    }

    private Button copyButton(String text) {
        Button btn = new Button("⧉");
        btn.getStyleClass().add("copy-btn");
        btn.setOnAction(e -> {
            e.consume();
            ClipboardUtils.copyText(text);
        });
        return btn;
    }

}
