package com.rqtracker.ui.dialog;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import com.rqtracker.util.DialogHelper;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * 新增 / 編輯 RQ 對話框（對應 HTML 的 openModal / submitModal）。
 */
public class NewEditRQDialog {

    private final Stage  dialog;
    private final boolean isEdit;

    private final TextField rqIdField;
    private final TextField projectNumField;
    private final VBox      versionList;
    private final List<TextField> versionFields = new ArrayList<>();

    /** 送出回呼：(rqId, projectNum, versionNames) */
    private Consumer<Result> onSubmit;

    public record Result(String rqId, String projectNum, List<String> versionNames) {}

    /**
     * @param owner    父視窗
     * @param existing 若為編輯模式，傳入現有 RQData；新增則傳 null
     */
    public NewEditRQDialog(Window owner, RQData existing) {
        this.isEdit = (existing != null);

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle(isEdit ? "編輯 RQ" : "新增 RQ");
        dialog.setMinWidth(520);
        dialog.setMinHeight(360);

        // ── RQ 編號欄位 ─────────────────────────────────────────────────────
        Label rqIdLabel = new Label("RQ 名稱");
        rqIdLabel.getStyleClass().add("form-label");
        Label rqIdHint = new Label("格式：RQ編號_需求說明。特殊字元（/ * ? \" < > |）會自動移除。");
        rqIdHint.getStyleClass().add("form-hint");
        rqIdHint.setWrapText(true);

        rqIdField = new TextField();
        rqIdField.getStyleClass().add("form-input");
        rqIdField.setPromptText("例：RQ100051742_新增推薦員工欄位");
        rqIdField.setPrefWidth(480);
        if (isEdit) {
            rqIdField.setText(existing.getId());
            rqIdField.setEditable(false);
            rqIdField.getStyleClass().add("form-input-disabled");
        }

        VBox rqIdGroup = new VBox(4, rqIdLabel, rqIdHint, rqIdField);

        // ── 專案號欄位 ─────────────────────────────────────────────────────
        Label projLabel = new Label("專案代號");
        projLabel.getStyleClass().add("form-label");
        Label projHint = new Label("用於建立 SVN 路徑的專案識別碼（例：POSMS、NETPOST）。可留空。");
        projHint.getStyleClass().add("form-hint");
        projHint.setWrapText(true);

        projectNumField = new TextField();
        projectNumField.getStyleClass().add("form-input");
        projectNumField.setPromptText("例：POSMS");
        if (isEdit && existing.getProjectNum() != null) {
            projectNumField.setText(existing.getProjectNum());
        }

        VBox projGroup = new VBox(4, projLabel, projHint, projectNumField);

        // ── 版本清單 ─────────────────────────────────────────────────────
        Label verLabel = new Label("版本名稱");
        verLabel.getStyleClass().add("form-label");

        versionList = new VBox(6);
        versionList.getStyleClass().add("version-list");

        if (isEdit && existing.getVersions() != null) {
            existing.getVersions().forEach(v -> addVersionRow(v.getName()));
        } else {
            addVersionRow(""); // 預設一個空版本欄位
        }

        Label verHint = new Label("每行輸入一個版本名稱（例：網路郵局中文版）。至少新增一個版本才能追蹤進度。");
        verHint.getStyleClass().add("form-hint");
        verHint.setWrapText(true);

        Button addVerBtn = new Button("＋ 新增版本");
        addVerBtn.getStyleClass().add("btn-add-ver");
        addVerBtn.setOnAction(e -> addVersionRow(""));

        VBox verGroup = new VBox(6, verLabel, verHint, versionList, addVerBtn);

        // ── 主體 ────────────────────────────────────────────────────────
        VBox body = new VBox(16, rqIdGroup, projGroup, verGroup);
        body.getStyleClass().add("modal-body");
        body.setPadding(new Insets(20, 24, 16, 24));

        // ── Footer ──────────────────────────────────────────────────────
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> dialog.close());

        Button submitBtn = new Button(isEdit ? "儲存" : "建立");
        submitBtn.getStyleClass().addAll("btn-primary");
        submitBtn.setDefaultButton(true);
        submitBtn.setOnAction(e -> handleSubmit());

        HBox footer = new HBox(12, cancelBtn, submitBtn);
        footer.getStyleClass().add("modal-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 20, 24));

        // ── Modal 標題 ────────────────────────────────────────────────
        Label titleLabel = new Label(isEdit ? "編輯 RQ" : "新增 RQ");
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

        ScrollPane bodyScroll = new ScrollPane(body);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.getStyleClass().add("edge-to-edge");

        VBox top    = new VBox(modalHeader, new Separator());
        VBox bottom = new VBox(new Separator(), footer);

        BorderPane modal = new BorderPane();
        modal.setTop(top);
        modal.setCenter(bodyScroll);
        modal.setBottom(bottom);
        modal.getStyleClass().add("modal");
        modal.setPrefWidth(560);
        modal.setPrefHeight(530);

        Scene scene = new Scene(modal);
        DialogHelper.applyTheme(scene, getClass());
        dialog.setScene(scene);
        DialogHelper.makeMovable(dialog, modalHeader);
        DialogHelper.makeResizable(dialog, modal);
    }

    public void setOnSubmit(Consumer<Result> handler) { this.onSubmit = handler; }

    public void show() { dialog.show(); }
    public Stage getStage() { return dialog; }

    // ──────────────────────────────────────────────────────────────

    private void addVersionRow(String initial) {
        TextField field = new TextField(initial);
        field.getStyleClass().add("form-input");
        field.setPromptText("例：網路郵局中文版 pstID");
        field.setPrefWidth(380);

        Button removeBtn = new Button("－");
        removeBtn.getStyleClass().add("btn-remove-ver");
        removeBtn.setOnAction(e -> {
            int idx = versionFields.indexOf(field);
            if (idx >= 0) {
                versionFields.remove(idx);
                versionList.getChildren().remove(idx);
            }
        });

        HBox row = new HBox(8, field, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        versionFields.add(field);
        versionList.getChildren().add(row);

        field.requestFocus();
    }

    private void handleSubmit() {
        String rqId = rqIdField.getText().trim();
        if (rqId.isBlank()) {
            showError("RQ 編號不可為空白");
            return;
        }

        String projectNum = projectNumField.getText().trim();

        List<String> versions = versionFields.stream()
            .map(f -> f.getText().trim())
            .filter(s -> !s.isBlank())
            .toList();

        if (onSubmit != null) {
            onSubmit.accept(new Result(rqId, projectNum, versions));
        }
        dialog.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(dialog);
        alert.showAndWait();
    }

}
