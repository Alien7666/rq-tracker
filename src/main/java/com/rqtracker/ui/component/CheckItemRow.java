package com.rqtracker.ui.component;

import com.rqtracker.model.TaskDef;
import com.rqtracker.util.ClipboardUtils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/**
 * 任務勾選列（對應 HTML 的 check-item div）。
 * 點擊整列 → 勾選（未勾時）；點擊方格 → 勾選 / 確認取消勾選。
 */
public class CheckItemRow extends HBox {

    private final TaskDef task;
    private boolean checked;

    /** (rqId, taskKey) → 處理勾選 / 取消勾選 */
    private final BiConsumer<String, String> onCheckboxClick;
    /** (rqId, taskKey) → 點整列（只做勾選） */
    private final BiConsumer<String, String> onRowClick;

    private final String rqId;
    private final Label  checkBox;

    /** fileDot：磁碟掃描狀態圓點（階段 7 實作），預留位置 */
    private Label fileDot;

    public CheckItemRow(String rqId, TaskDef task, boolean checked, boolean showStep,
                        String timestamp,
                        BiConsumer<String, String> onCheckboxClick,
                        BiConsumer<String, String> onRowClick) {
        this.rqId = rqId;
        this.task = task;
        this.checked = checked;
        this.onCheckboxClick = onCheckboxClick;
        this.onRowClick = onRowClick;

        getStyleClass().add("check-item");
        if (checked)         getStyleClass().add("checked");
        if (task.isWarn())   getStyleClass().add("warn-item");
        if (task.isOptional()) getStyleClass().add("optional-item");

        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(6, 10, 6, 10));
        setSpacing(8);
        setCursor(Cursor.HAND);

        // ── 勾選方格 ───────────────────────────────────────────────────────
        checkBox = new Label(checked ? "✓" : "");
        checkBox.getStyleClass().add("check-box");
        checkBox.setMinSize(20, 20);
        checkBox.setMaxSize(20, 20);
        checkBox.setAlignment(Pos.CENTER);
        checkBox.setOnMouseClicked(e -> {
            e.consume();
            if (onCheckboxClick != null) onCheckboxClick.accept(rqId, task.getKey());
        });

        // ── 標籤區域 ───────────────────────────────────────────────────────
        VBox labelBox = new VBox(2);
        HBox.setHgrow(labelBox, Priority.ALWAYS);

        // 主標籤行
        HBox labelRow = new HBox(4);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        if (showStep && task.getStep() != null) {
            Label stepLabel = new Label(task.getStep());
            stepLabel.getStyleClass().add("check-step");
            labelRow.getChildren().add(stepLabel);
        }

        Label mainLabel = new Label(task.getLabel());
        mainLabel.getStyleClass().add("check-label-text");
        labelRow.getChildren().add(mainLabel);

        if (task.getSub() != null) {
            Label subLabel = new Label(task.getSub());
            subLabel.getStyleClass().add("check-sub");
            labelRow.getChildren().add(subLabel);
        }
        if (task.isWarn()) {
            Label warnTag = new Label("⚠ " + (task.getWarnText() != null ? task.getWarnText() : "注意"));
            warnTag.getStyleClass().add("warn-tag");
            labelRow.getChildren().add(warnTag);
        }
        if (task.isOptional()) {
            Label optTag = new Label("選填");
            optTag.getStyleClass().add("optional-tag");
            labelRow.getChildren().add(optTag);
        }

        labelBox.getChildren().add(labelRow);

        // 路徑 / 檔名行
        if (task.getFolder() != null || task.getFilename() != null) {
            VBox fileBox = buildFileHint(task);
            labelBox.getChildren().add(fileBox);
        }

        // 時間戳
        if (checked && timestamp != null && !timestamp.isBlank()) {
            Label tsLabel = new Label("✓ " + timestamp);
            tsLabel.getStyleClass().add("check-timestamp");
            labelBox.getChildren().add(tsLabel);
        }

        getChildren().addAll(checkBox, labelBox);

        // 整列點擊 → 只勾選（已勾不動）
        setOnMouseClicked(e -> {
            if (onRowClick != null) onRowClick.accept(rqId, task.getKey());
        });
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        checkBox.setText(checked ? "✓" : "");
        if (checked) getStyleClass().add("checked");
        else         getStyleClass().remove("checked");
    }

    public TaskDef getTask() { return task; }

    // ── 私有 ──────────────────────────────────────────────────────────────

    private VBox buildFileHint(TaskDef t) {
        VBox box = new VBox(1);
        box.getStyleClass().add("file-hint");

        if (t.getFolder() != null) {
            HBox folderRow = new HBox(4);
            folderRow.setAlignment(Pos.CENTER_LEFT);

            // 磁碟狀態圓點（階段 7 補充）
            fileDot = new Label("●");
            fileDot.getStyleClass().addAll("file-dot", "fs-unknown");
            if (t.getFilename() == null) {
                folderRow.getChildren().add(fileDot);
            }

            Label folderLabel = new Label(t.getFolder());
            folderLabel.getStyleClass().add("folder-path");
            folderLabel.setWrapText(false);

            Button copyBtn = copyButton(t.getFolder());
            folderRow.getChildren().addAll(folderLabel, copyBtn);
            box.getChildren().add(folderRow);
        }

        if (t.getFilename() != null) {
            HBox fileRow = new HBox(4);
            fileRow.setAlignment(Pos.CENTER_LEFT);

            Label dot = new Label("●");
            dot.getStyleClass().addAll("file-dot", "fs-unknown");
            fileDot = dot;

            Label fnLabel = new Label(t.getFilename());
            fnLabel.getStyleClass().add("filename-text");
            fnLabel.setWrapText(false);

            Button copyBtn = copyButton(t.getFilename());
            fileRow.getChildren().addAll(dot, fnLabel, copyBtn);
            box.getChildren().add(fileRow);
        }

        return box;
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

    /** 更新磁碟狀態圓點的 CSS class 並加 Tooltip 說明。 */
    public void setScanState(String cssClass) {
        if (fileDot != null) {
            fileDot.getStyleClass().removeIf(c -> c.startsWith("fs-"));
            fileDot.getStyleClass().add(cssClass);
            String tip = switch (cssClass) {
                case "fs-file"    -> "✓ 檔案存在且完整";
                case "fs-partial" -> "△ 部分檔案存在（可能不完整）";
                case "fs-folder"  -> "□ 資料夾存在，但找不到對應檔案";
                case "fs-none"    -> "✗ 找不到任何檔案或資料夾";
                case "fs-stale"   -> "⟳ 掃描資料已過期，請重新掃描";
                case "fs-scan"    -> "… 掃描中";
                default           -> "？ 尚未掃描";
            };
            Tooltip.uninstall(fileDot, null);
            Tooltip.install(fileDot, new Tooltip(tip));
        }
    }
}
