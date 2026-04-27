package com.rqtracker.ui.dialog;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.service.DataStore;
import com.rqtracker.util.ClipboardUtils;
import com.rqtracker.util.DialogHelper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.List;

/**
 * 版本改動程式清單輸入對話框（對應 HTML 的 openVerFilesModal / submitVerFiles）。
 * 每行一條相對路徑，從 src/ 開始。
 */
public class VerFilesDialog {

    private final Stage     dialog;
    private final DataStore dataStore;
    private final String    rqId;
    private final int       vIdx;

    /** 儲存後回呼 */
    private Runnable onSaved;

    public VerFilesDialog(Window owner, DataStore dataStore, String rqId, int vIdx) {
        this.dataStore = dataStore;
        this.rqId      = rqId;
        this.vIdx      = vIdx;

        RQData rq = dataStore.getRQ(rqId);
        String vName = (rq != null && rq.getVersions() != null && vIdx < rq.getVersions().size())
            ? rq.getVersions().get(vIdx).getName() : "版本 " + (vIdx + 1);

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("改動程式 — " + vName);
        dialog.setMinWidth(480);
        dialog.setMinHeight(360);

        // ── Modal 標題 ────────────────────────────────────────────────
        Label titleLabel = new Label("✎ 改動程式 — " + vName);
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

        // ── 說明 ─────────────────────────────────────────────────────
        Label hint = new Label("每行一個路徑，從 src/ 開始（複製路徑後按下「查看清單」可產生 list.txt 內容）");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);

        // ── TextArea ─────────────────────────────────────────────────
        String existingText = (rq != null) ? rq.getVersionFilesText(vIdx) : "";
        TextArea textArea = new TextArea(existingText);
        textArea.getStyleClass().add("note-textarea");
        textArea.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 13px;");
        textArea.setPromptText("src/main/java/com/systex/jbranch/app/server/post/txn/EB100700.java");
        textArea.setPrefHeight(260);
        textArea.setWrapText(false);

        VBox body = new VBox(10, hint, textArea);
        body.getStyleClass().add("modal-body");
        body.setPadding(new Insets(16, 24, 16, 24));
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // ── Footer ──────────────────────────────────────────────────
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setOnAction(e -> dialog.close());

        Button viewListBtn = new Button("📋 查看清單");
        viewListBtn.getStyleClass().add("btn-ghost");
        viewListBtn.setOnAction(e -> {
            saveText(textArea.getText());
            showListOutput(rq, textArea.getText(), vName);
        });

        Button saveBtn = new Button("儲存");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            saveText(textArea.getText());
            dialog.close();
        });

        HBox footer = new HBox(12, cancelBtn, viewListBtn, saveBtn);
        footer.getStyleClass().add("modal-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 20, 24));

        VBox modal = new VBox(modalHeader, new Separator(), body, new Separator(), footer);
        modal.getStyleClass().add("modal");
        modal.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(body, Priority.ALWAYS);
        modal.setPrefWidth(560);
        modal.setPrefHeight(430);

        Scene scene = new Scene(modal);
        DialogHelper.applyTheme(scene, getClass());
        dialog.setScene(scene);
        DialogHelper.makeMovable(dialog, modalHeader);
        DialogHelper.makeResizable(dialog, modal);
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    public void show() { dialog.show(); }
    public Stage getStage() { return dialog; }

    // ──────────────────────────────────────────────────────────────

    private void saveText(String text) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;
        rq.setVersionFilesText(vIdx, text.trim());
        dataStore.saveRQ(rqId, rq);
        if (onSaved != null) onSaved.run();
    }

    /** 產生 list.txt 格式的預覽視窗（對應 HTML 的 listOutputPre） */
    private void showListOutput(RQData rq, String rawText, String vName) {
        // 建立格式化清單
        StringBuilder sb = new StringBuilder();
        String vid = vName.contains(" ") ? vName.substring(vName.lastIndexOf(' ') + 1) : vName;
        sb.append("[").append(vName).append("]\n");
        rawText.lines()
            .map(String::trim)
            .filter(l -> !l.isBlank())
            .map(l -> l.replace('/', '\\'))
            .forEach(l -> sb.append(vid).append("\\").append(l).append("\n"));

        String content = sb.toString().isBlank() ? "（尚無資料）" : sb.toString().trim();

        // 建立輸出視窗
        Stage outputStage = new Stage();
        DialogHelper.initTransparent(outputStage);
        outputStage.initModality(Modality.NONE);
        outputStage.initOwner(dialog);
        outputStage.setTitle("程式碼清單 — " + vName);

        Label titleLabel = new Label("📋 程式碼清單 — " + vName);
        titleLabel.getStyleClass().add("modal-title");
        Button closeBtn2 = new Button("✕");
        closeBtn2.getStyleClass().add("modal-close");
        closeBtn2.setOnAction(e -> outputStage.close());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox hdr = new HBox(titleLabel, sp, closeBtn2);
        hdr.getStyleClass().add("modal-header");
        hdr.setPadding(new Insets(14, 24, 14, 24));

        TextArea pre = new TextArea(content);
        pre.setEditable(false);
        pre.setStyle("-fx-font-family:'Consolas','Courier New',monospace; -fx-font-size:13px;");
        pre.setPrefHeight(320);
        pre.setWrapText(false);
        VBox bodyBox = new VBox(pre);
        bodyBox.setPadding(new Insets(16, 24, 16, 24));

        Button closeFtr = new Button("關閉");
        closeFtr.getStyleClass().add("btn-ghost");
        closeFtr.setOnAction(e -> outputStage.close());
        Button copyBtn2 = new Button("複製全部");
        copyBtn2.getStyleClass().add("btn-primary");
        copyBtn2.setOnAction(e -> { ClipboardUtils.copyText(content); });
        HBox ftr = new HBox(12, closeFtr, copyBtn2);
        ftr.getStyleClass().add("modal-footer");
        ftr.setAlignment(Pos.CENTER_RIGHT);
        ftr.setPadding(new Insets(12, 24, 20, 24));

        VBox modal2 = new VBox(hdr, new Separator(), bodyBox, new Separator(), ftr);
        modal2.getStyleClass().add("modal");
        modal2.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);
        modal2.setPrefWidth(580);
        modal2.setPrefHeight(460);

        Scene scene2 = new Scene(modal2);
        DialogHelper.applyTheme(scene2, getClass());
        outputStage.setScene(scene2);
        DialogHelper.makeMovable(outputStage, hdr);
        DialogHelper.makeResizable(outputStage, modal2);
        outputStage.show();
    }

}
