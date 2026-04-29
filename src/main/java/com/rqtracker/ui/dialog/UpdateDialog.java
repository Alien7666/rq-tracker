package com.rqtracker.ui.dialog;

import com.rqtracker.BuildInfo;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.UpdateService;
import com.rqtracker.util.DialogHelper;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 軟體更新對話框，包含 5 種狀態：
 * CHECKING → UP_TO_DATE | UPDATE_AVAILABLE → DOWNLOADING → READY_TO_INSTALL
 */
public class UpdateDialog {

    private enum State { CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, DOWNLOADING, READY_TO_INSTALL, ERROR }

    private final Stage       dialog;
    private final AppConfig   appConfig;
    private final String      checkUrl;

    private final VBox        body        = new VBox(16);
    private final HBox        footer      = new HBox(12);
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label       progressLbl = new Label("準備下載…");
    private final Label       statusLbl   = new Label();

    private UpdateService.UpdateInfo pendingInfo;
    private Path                     downloadedMsi;
    private final AtomicBoolean      cancelled = new AtomicBoolean(false);
    private final AtomicBoolean      installStarted = new AtomicBoolean(false);
    private Thread                   dlThread;

    public UpdateDialog(Window owner, AppConfig appConfig, String checkUrl) {
        this.appConfig = appConfig;
        this.checkUrl  = checkUrl;

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("軟體更新");

        Label titleLabel = new Label("⬆ 軟體更新");
        titleLabel.getStyleClass().add("modal-title");

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close");
        closeBtn.setCancelButton(true);
        closeBtn.setOnAction(e -> cancelAndClose());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox modalHeader = new HBox(titleLabel, spacer, closeBtn);
        modalHeader.getStyleClass().add("modal-header");
        modalHeader.setAlignment(Pos.CENTER_LEFT);
        modalHeader.setPadding(new Insets(14, 24, 14, 24));

        body.setPadding(new Insets(20, 24, 16, 24));
        body.setMinWidth(420);

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
        modal.setPrefWidth(480);
        modal.setPrefHeight(280);

        Scene scene = new Scene(modal);
        DialogHelper.applyTheme(scene, getClass());
        dialog.setScene(scene);
        DialogHelper.makeMovable(dialog, modalHeader);

        dialog.setOnCloseRequest(e -> cancelAndClose());
    }

    public Stage getStage() { return dialog; }

    public void show() {
        dialog.show();
        showState(State.CHECKING);
        startCheck();
    }

    // ── 狀態切換 ─────────────────────────────────────────────────────────────

    private void showState(State state) {
        body.getChildren().clear();
        footer.getChildren().clear();

        switch (state) {
            case CHECKING -> {
                Label lbl = new Label("正在檢查更新，請稍候…");
                lbl.getStyleClass().add("form-hint");
                Label spin = new Label("◌");
                spin.getStyleClass().add("modal-title");
                body.getChildren().addAll(spin, lbl);
                dialog.setMinHeight(220);
            }
            case UP_TO_DATE -> {
                Label ico = new Label("✓");
                ico.getStyleClass().add("modal-title");
                Label msg = new Label("目前已是最新版本 v" + BuildInfo.VERSION);
                msg.getStyleClass().add("form-label");
                Label sub = new Label("無需更新。");
                sub.getStyleClass().add("form-hint");
                body.getChildren().addAll(ico, msg, sub);

                Button ok = new Button("確定");
                ok.getStyleClass().add("btn-primary");
                ok.setDefaultButton(true);
                ok.setCancelButton(true);
                ok.setOnAction(e -> dialog.close());
                footer.getChildren().add(ok);
                dialog.setMinHeight(240);
            }
            case UPDATE_AVAILABLE -> {
                Label verLbl = new Label("發現新版本  v" + pendingInfo.version());
                verLbl.getStyleClass().add("modal-title");

                Label dateLbl = new Label("發布日期：" + pendingInfo.publishedAt());
                dateLbl.getStyleClass().add("form-hint");

                TextArea notes = new TextArea(pendingInfo.releaseNotes());
                notes.setEditable(false);
                notes.setWrapText(true);
                notes.setPrefHeight(120);
                notes.getStyleClass().add("note-textarea");

                Label cur = new Label("目前版本：v" + BuildInfo.VERSION);
                cur.getStyleClass().add("form-hint");

                body.getChildren().addAll(verLbl, dateLbl, notes, cur);

                Button cancel = new Button("略過此版本");
                cancel.getStyleClass().add("btn-ghost");
                cancel.setCancelButton(true);
                cancel.setOnAction(e -> {
                    appConfig.setSkipVersion(pendingInfo.version());
                    appConfig.save();
                    dialog.close();
                });

                Button dl = new Button("下載並安裝");
                dl.getStyleClass().add("btn-primary");
                dl.setDefaultButton(true);
                dl.setOnAction(e -> startDownload());

                footer.getChildren().addAll(cancel, dl);
                dialog.setMinHeight(360);
            }
            case DOWNLOADING -> {
                Label lbl = new Label("正在下載更新…");
                lbl.getStyleClass().add("form-label");
                progressBar.setPrefWidth(400);
                progressBar.setProgress(0);
                progressLbl.getStyleClass().add("form-hint");

                body.getChildren().addAll(lbl, progressBar, progressLbl);

                Button cancelBtn = new Button("取消");
                cancelBtn.getStyleClass().add("btn-ghost");
                cancelBtn.setCancelButton(true);
                cancelBtn.setOnAction(e -> {
                    cancelled.set(true);
                    if (dlThread != null) dlThread.interrupt();
                    dialog.close();
                });
                footer.getChildren().add(cancelBtn);
                dialog.setMinHeight(240);
            }
            case READY_TO_INSTALL -> {
                installStarted.set(false);
                Label ico = new Label("✓");
                ico.getStyleClass().add("modal-title");
                Label msg = new Label("下載完成！");
                msg.getStyleClass().add("form-label");
                Label hint = new Label(
                    "點擊「立即安裝」後 3 秒會關閉 RQ Tracker。\n" +
                    "接著 Windows 會跳出授權確認視窗（UAC），請點「是」。\n" +
                    "新版安裝約 5–15 秒，完成後會自動啟動新版。\n" +
                    "若安裝失敗，畫面會跳出錯誤訊息，且舊版會自動重啟。");
                hint.getStyleClass().add("form-hint");
                hint.setWrapText(true);

                body.getChildren().addAll(ico, msg, hint);

                Button later = new Button("稍後安裝");
                later.getStyleClass().add("btn-ghost");
                later.setCancelButton(true);
                later.setOnAction(e -> dialog.close());

                Button install = new Button("立即安裝");
                install.getStyleClass().add("btn-primary");
                install.setDefaultButton(true);
                install.setOnAction(e -> {
                    if (!installStarted.compareAndSet(false, true)) return;
                    install.setDisable(true);
                    later.setDisable(true);
                    startInstall();
                });
                footer.getChildren().addAll(later, install);
                dialog.setMinHeight(280);
            }
            case ERROR -> {
                Label ico = new Label("✗");
                ico.getStyleClass().add("modal-title");
                statusLbl.getStyleClass().add("form-hint");
                body.getChildren().addAll(ico, statusLbl);

                Button ok = new Button("確定");
                ok.getStyleClass().add("btn-ghost");
                ok.setDefaultButton(true);
                ok.setCancelButton(true);
                ok.setOnAction(e -> dialog.close());
                footer.getChildren().add(ok);
                dialog.setMinHeight(220);
            }
        }
    }

    // ── 非同步操作 ────────────────────────────────────────────────────────────

    private void startCheck() {
        if (checkUrl == null || checkUrl.isBlank()) {
            Platform.runLater(() -> showError("尚未設定更新伺服器網址，請至設定頁面填入更新 URL。"));
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Optional<UpdateService.UpdateInfo> info =
                    UpdateService.checkForUpdate(checkUrl, BuildInfo.VERSION);
                Platform.runLater(() -> {
                    if (info.isPresent()) {
                        pendingInfo = info.get();
                        showState(State.UPDATE_AVAILABLE);
                    } else {
                        showState(State.UP_TO_DATE);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("檢查更新失敗：" + e.getMessage()));
            }
        }, "UpdateDialog-Check");
        t.setDaemon(true);
        t.start();
    }

    private void startDownload() {
        showState(State.DOWNLOADING);
        cancelled.set(false);

        dlThread = new Thread(() -> {
            try {
                Path msi = UpdateService.download(pendingInfo,
                    pct -> Platform.runLater(() -> {
                        progressBar.setProgress(pct);
                        progressLbl.setText(String.format("下載中 %.0f%%", pct * 100));
                    }),
                    cancelled);

                Platform.runLater(() -> {
                    if (msi != null) {
                        downloadedMsi = msi;
                        showState(State.READY_TO_INSTALL);
                    }
                });
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                Platform.runLater(() -> showError("下載失敗：" + e.getMessage()));
            }
        }, "UpdateDialog-Download");
        dlThread.setDaemon(true);
        dlThread.start();
    }

    private void startInstall() {
        try {
            UpdateService.launchInstaller(downloadedMsi);
        } catch (Exception ex) {
            showError("無法啟動安裝程式：" + ex.getMessage());
            return;
        }

        body.getChildren().clear();
        footer.getChildren().clear();

        Label ico = new Label("⏳");
        ico.getStyleClass().add("modal-title");
        Label msg = new Label("正在準備更新…");
        msg.getStyleClass().add("form-label");
        Label countdown = new Label();
        countdown.getStyleClass().add("modal-title");
        Label hint = new Label(
            "RQ Tracker 即將關閉。\n" +
            "請於接下來的 Windows 授權視窗（UAC）點「是」以開始安裝。");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        body.getChildren().addAll(ico, msg, countdown, hint);
        dialog.setMinHeight(260);

        final int[] remaining = { 3 };
        countdown.setText(remaining[0] + " 秒後自動關閉…");
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remaining[0]--;
            if (remaining[0] > 0) {
                countdown.setText(remaining[0] + " 秒後自動關閉…");
            } else {
                countdown.setText("關閉中…");
                UpdateService.exitForUpdate();
            }
        }));
        timer.setCycleCount(3);
        timer.play();
    }

    private void showError(String msg) {
        statusLbl.setText(msg);
        showState(State.ERROR);
    }

    private void cancelAndClose() {
        cancelled.set(true);
        if (dlThread != null) dlThread.interrupt();
        dialog.close();
    }

}
