package com.rqtracker.ui.component;

import com.rqtracker.model.TaskDef;
import com.rqtracker.util.ClipboardUtils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.function.BiConsumer;

/**
 * 任務勾選列（對應 HTML 的 check-item div）。
 * 點擊整列 → 勾選（未勾時）；點擊方格 → 勾選 / 確認取消勾選。
 */
public class CheckItemRow extends HBox {

    // ── 主題色（對應 rq-theme.css 變數，Canvas 無法讀 CSS 變數需硬寫） ──
    private static final Color C_SURFACE2     = Color.web("#1e242d");
    private static final Color C_BORDER       = Color.web("#4d5f75");
    private static final Color C_ACCENT       = Color.web("#5a9e78");
    private static final Color C_ACCENT_HOVER = Color.web("#73b892");
    private static final Color C_CHECK        = Color.web("#ffffff");

    private final TaskDef task;
    private boolean checked;

    private final BiConsumer<String, String> onCheckboxClick;
    private final BiConsumer<String, String> onRowClick;

    private final String rqId;
    private final Canvas checkCanvas;
    private boolean hovered = false;

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
        if (checked)           getStyleClass().add("checked");
        if (task.isWarn())     getStyleClass().add("warn-item");
        if (task.isOptional()) getStyleClass().add("optional-item");

        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(6, 10, 6, 10));
        setSpacing(8);
        setCursor(Cursor.HAND);

        // ── Canvas 勾選方格 ────────────────────────────────────────────
        checkCanvas = new Canvas(22, 22);
        drawBox(checked, false);

        checkCanvas.setOnMouseEntered(e -> { hovered = true;  drawBox(this.checked, true); });
        checkCanvas.setOnMouseExited (e -> { hovered = false; drawBox(this.checked, false); });
        checkCanvas.setOnMouseClicked(e -> {
            e.consume();
            if (onCheckboxClick != null) onCheckboxClick.accept(rqId, task.getKey());
        });
        checkCanvas.setCursor(Cursor.HAND);

        // ── 標籤區域 ───────────────────────────────────────────────────
        VBox labelBox = new VBox(2);
        HBox.setHgrow(labelBox, Priority.ALWAYS);

        HBox labelRow = new HBox(4);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        if (showStep && task.getStep() != null) {
            Label stepLabel = new Label(task.getStep());
            stepLabel.getStyleClass().add("check-step");
            labelRow.getChildren().add(stepLabel);
        }

        Label mainLabel = new Label(task.getLabel());
        mainLabel.getStyleClass().add("check-label-text");
        if (checked) mainLabel.getStyleClass().add("checked");
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

        if (task.getFolder() != null || task.getFilename() != null) {
            labelBox.getChildren().add(buildFileHint(task));
        }

        if (checked && timestamp != null && !timestamp.isBlank()) {
            Label tsLabel = new Label("✓ " + timestamp);
            tsLabel.getStyleClass().add("check-timestamp");
            labelBox.getChildren().add(tsLabel);
        }

        getChildren().addAll(checkCanvas, labelBox);

        setOnMouseClicked(e -> {
            if (onRowClick != null) onRowClick.accept(rqId, task.getKey());
        });
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        drawBox(checked, hovered);
        if (checked) {
            getStyleClass().add("checked");
        } else {
            getStyleClass().remove("checked");
        }
    }

    public TaskDef getTask() { return task; }

    // ── Canvas 繪製 ────────────────────────────────────────────────────

    private void drawBox(boolean isChecked, boolean isHovered) {
        GraphicsContext gc = checkCanvas.getGraphicsContext2D();
        double w = checkCanvas.getWidth();
        double h = checkCanvas.getHeight();
        double pad = 1.0;
        double size = w - pad * 2;
        double r = 5.0;

        gc.clearRect(0, 0, w, h);

        if (isChecked) {
            // 填色背景
            Color bg = isHovered ? C_ACCENT_HOVER : C_ACCENT;
            gc.setFill(bg);
            gc.fillRoundRect(pad, pad, size, size, r * 2, r * 2);

            // 微光暈（模擬 dropshadow）
            gc.setFill(Color.color(
                bg.getRed(), bg.getGreen(), bg.getBlue(), 0.25));
            gc.fillRoundRect(pad - 1.5, pad - 1.5, size + 3, size + 3, r * 2 + 2, r * 2 + 2);
            gc.setFill(bg);
            gc.fillRoundRect(pad, pad, size, size, r * 2, r * 2);

            // 向量勾勾路徑（相對於 22×22 canvas）
            gc.setStroke(C_CHECK);
            gc.setLineWidth(2.1);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.strokePolyline(
                new double[]{ 5.5, 9.2, 16.5 },
                new double[]{ 11.2, 15.5, 6.5 },
                3
            );

        } else {
            // 空框
            Color borderColor = isHovered ? C_ACCENT : C_BORDER;
            Color bgColor     = isHovered ? Color.web("#5b8fac22") : C_SURFACE2;

            gc.setFill(bgColor);
            gc.fillRoundRect(pad, pad, size, size, r * 2, r * 2);

            gc.setStroke(borderColor);
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(pad, pad, size, size, r * 2, r * 2);
        }
    }

    // ── 私有輔助 ───────────────────────────────────────────────────────

    private VBox buildFileHint(TaskDef t) {
        VBox box = new VBox(1);
        box.getStyleClass().add("file-hint");

        if (t.getFolder() != null) {
            HBox folderRow = new HBox(4);
            folderRow.setAlignment(Pos.CENTER_LEFT);

            fileDot = new Label("");
            fileDot.getStyleClass().addAll("file-dot", "fs-unknown");
            if (t.getFilename() == null) folderRow.getChildren().add(fileDot);

            Label folderLabel = new Label(t.getFolder());
            folderLabel.getStyleClass().add("folder-path");
            folderLabel.setWrapText(false);

            folderRow.getChildren().addAll(folderLabel, copyButton(t.getFolder()));
            box.getChildren().add(folderRow);
        }

        if (t.getFilename() != null) {
            HBox fileRow = new HBox(4);
            fileRow.setAlignment(Pos.CENTER_LEFT);

            Label dot = new Label("");
            dot.getStyleClass().addAll("file-dot", "fs-unknown");
            fileDot = dot;

            Label fnLabel = new Label(t.getFilename());
            fnLabel.getStyleClass().add("filename-text");
            fnLabel.setWrapText(false);

            fileRow.getChildren().addAll(dot, fnLabel, copyButton(t.getFilename()));
            box.getChildren().add(fileRow);
        }

        return box;
    }

    private Button copyButton(String text) {
        Button btn = new Button("⧉");
        btn.getStyleClass().add("copy-btn");
        btn.setOnAction(e -> { e.consume(); ClipboardUtils.copyText(text); });
        return btn;
    }

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
