package com.rqtracker.ui.dialog;

import com.rqtracker.service.AppConfig;
import com.rqtracker.util.DialogHelper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SettingsDialog {

    private final Stage     dialog;
    private final AppConfig appConfig;

    private final TextField downloadsField;
    private final TextField svnField;
    private final TextField backupField;
    private final TextField updateUrlField;

    private Runnable onSaved;

    public SettingsDialog(Window owner, AppConfig appConfig) {
        this.appConfig = appConfig;

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("路徑設定");
        dialog.setMinWidth(520);
        dialog.setMinHeight(400);

        // ── Modal 標題 ────────────────────────────────────────────────
        Label titleLabel = new Label("⚙ 路徑設定");
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

        // ── 欄位 ─────────────────────────────────────────────────────
        downloadsField = new TextField(nvl(appConfig.getDownloadsRoot(), "D:\\Systex"));
        downloadsField.getStyleClass().add("form-input");
        downloadsField.setPrefWidth(340);

        svnField = new TextField(nvl(appConfig.getSvnRoot(), "C:\\SVN\\新系統開發"));
        svnField.getStyleClass().add("form-input");
        svnField.setPrefWidth(340);

        backupField = new TextField(nvl(appConfig.getBackupDir(), ""));
        backupField.getStyleClass().add("form-input");
        backupField.setPrefWidth(340);
        backupField.setPromptText("例：D:\\Backup\\RQTracker（留空表示停用）");

        updateUrlField = new TextField(nvl(appConfig.getUpdateCheckUrl(), ""));
        updateUrlField.getStyleClass().add("form-input");
        updateUrlField.setPrefWidth(340);
        updateUrlField.setPromptText("https://api.github.com/repos/{owner}/{repo}/releases/latest");

        // ── 說明文字 ─────────────────────────────────────────────────
        Label desc = new Label("設定以下路徑後，系統才能自動掃描交付檔案、生成 SVN 路徑提示，以及每日備份 RQ 資料。");
        desc.getStyleClass().add("settings-desc");
        desc.setWrapText(true);

        // ── 主體 ─────────────────────────────────────────────────────
        VBox body = new VBox();
        body.getStyleClass().add("modal-body");
        body.setPadding(new Insets(16, 24, 16, 24));
        body.setSpacing(0);

        body.getChildren().addAll(
            desc,
            spacerH(16),
            buildGroupHeader("📡 磁碟掃描路徑"),
            spacerH(12),
            buildPathRow("📂 成品交付資料夾",
                "放置各 RQ 交付成品的磁碟資料夾。\n掃描時系統會在此找對應的 ZIP 檔、程式碼等。",
                downloadsField, "downloads"),
            spacerH(14),
            buildPathRow("📁 SVN 版控資料夾",
                "本機 SVN checkout 的「新系統開發」資料夾路徑，例：C:\\SVN\\新系統開發\n" +
                "系統會在此下的 10_增修維護階段、9_文件 等子目錄產生路徑提示。",
                svnField, "svn"),
            spacerH(20),
            buildGroupHeader("💾 自動備份"),
            spacerH(12),
            buildPathRow("💾 備份儲存資料夾",
                "系統每次啟動時自動備份所有 RQ 資料到此資料夾。\n留空則停用自動備份功能。",
                backupField, "backup"),
            spacerH(20),
            buildGroupHeader("🔄 軟體更新"),
            spacerH(12),
            buildUrlRow("🔗 更新伺服器網址",
                "系統啟動時自動到此網址檢查是否有新版本。\n留空則停用自動更新檢查。",
                updateUrlField)
        );

        // ── Footer ──────────────────────────────────────────────────
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("儲存");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> handleSave());

        HBox footer = new HBox(12, cancelBtn, saveBtn);
        footer.getStyleClass().add("modal-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 20, 24));

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
        modal.setPrefWidth(580);
        modal.setPrefHeight(520);

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

    private Region spacerH(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    private HBox buildGroupHeader(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("settings-group-header");
        Separator sep = new Separator();
        HBox.setHgrow(sep, Priority.ALWAYS);
        HBox box = new HBox(10, lbl, sep);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox buildPathRow(String label, String hint, TextField field, String type) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        Label hintLbl = new Label(hint);
        hintLbl.getStyleClass().add("form-hint");
        hintLbl.setWrapText(true);

        Label pathStatus = new Label();
        pathStatus.getStyleClass().add("path-status-icon");
        updatePathStatus(pathStatus, field.getText(), type);
        field.textProperty().addListener((obs, o, n) -> updatePathStatus(pathStatus, n, type));

        Button browseBtn = new Button("瀏覽…");
        browseBtn.getStyleClass().add("btn-ghost");
        browseBtn.setOnAction(e -> browse(field, label));

        HBox inputRow = new HBox(8, field, pathStatus, browseBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);

        return new VBox(4, lbl, hintLbl, inputRow);
    }

    private VBox buildUrlRow(String label, String hint, TextField field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        Label hintLbl = new Label(hint);
        hintLbl.getStyleClass().add("form-hint");
        hintLbl.setWrapText(true);
        HBox.setHgrow(field, Priority.ALWAYS);
        return new VBox(4, lbl, hintLbl, field);
    }

    private void updatePathStatus(Label icon, String text, String type) {
        if (text == null || text.isBlank()) {
            if ("backup".equals(type)) {
                icon.setText("○");
                icon.getStyleClass().setAll("path-status-icon", "path-optional");
            } else {
                icon.setText("✗");
                icon.getStyleClass().setAll("path-status-icon", "path-invalid");
            }
            return;
        }
        boolean exists = new File(text.trim()).isDirectory();
        icon.setText(exists ? "✓" : "✗");
        icon.getStyleClass().setAll("path-status-icon", exists ? "path-valid" : "path-invalid");
    }

    private void browse(TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇" + title);
        String current = field.getText().trim();
        if (!current.isBlank()) {
            File init = new File(current);
            if (init.exists()) chooser.setInitialDirectory(init);
        }
        File chosen = chooser.showDialog(dialog);
        if (chosen != null) field.setText(chosen.getAbsolutePath());
    }

    private void handleSave() {
        String dl  = downloadsField.getText().trim();
        String svn = svnField.getText().trim();
        String bk  = backupField.getText().trim();

        List<String> warnings = new ArrayList<>();
        if (!dl.isBlank() && !new File(dl).isDirectory())
            warnings.add("• 成品交付資料夾「" + dl + "」不存在，請確認路徑是否正確");
        if (!svn.isBlank() && !new File(svn).isDirectory())
            warnings.add("• SVN 版控資料夾「" + svn + "」不存在，請確認路徑是否正確");

        if (!warnings.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                String.join("\n", warnings) +
                "\n\n（路徑不存在不影響儲存，但掃描功能將無法使用）\n\n確定儲存嗎？",
                ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle("路徑確認");
            alert.initOwner(dialog);
            Optional<ButtonType> res = alert.showAndWait();
            if (res.isEmpty() || res.get() == ButtonType.CANCEL) return;
        }

        if (!dl.isBlank())  appConfig.setDownloadsRoot(dl);
        if (!svn.isBlank()) appConfig.setSvnRoot(svn);
        appConfig.setBackupDir(bk.isBlank() ? null : bk);
        appConfig.setUpdateCheckUrl(updateUrlField.getText().trim());
        appConfig.save();

        if (onSaved != null) onSaved.run();
        dialog.close();
    }

    private static String nvl(String s, String defaultVal) {
        return (s == null || s.isBlank()) ? defaultVal : s;
    }

}
