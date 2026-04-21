package com.rqtracker.ui.controller;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskResult;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DataStore;
import com.rqtracker.service.BackupService;
import com.rqtracker.service.DiskScanService;
import com.rqtracker.service.FileListGenerator;
import com.rqtracker.service.FolderCreatorService;
import com.rqtracker.service.ImportExportService;
import com.rqtracker.service.ProgressCalc;
import com.rqtracker.ui.component.ConfirmDialog;
import com.rqtracker.ui.component.RQContentPanel;
import com.rqtracker.ui.component.RQTabBar;
import com.rqtracker.ui.component.ToastNotification;
import com.rqtracker.ui.dialog.HistoryDialog;
import com.rqtracker.ui.dialog.NewEditRQDialog;
import com.rqtracker.ui.dialog.SettingsDialog;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.PathUtils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 主控制器：協調標籤列、RQ 內容區域、Toast、Confirm 等子元件。
 */
public class MainController {

    private final DataStore        dataStore;
    private final AppConfig        appConfig;
    private final DiskScanService  diskScanService;

    private Stage             primaryStage;
    private RQTabBar          tabBar;
    private StackPane         rqContentArea;
    private VBox              emptyState;
    private ToastNotification toast;
    private VBox              scanResultPanel;
    private Label             backupStatusLabel;

    private String activeRqId;

    /** 最近一次掃描結果，傳給 RQContentPanel 渲染圓點用 */
    private Map<String, TaskResult> lastScanResults = Collections.emptyMap();

    private ScheduledExecutorService scanScheduler;

    public MainController(DataStore dataStore, AppConfig appConfig) {
        this.dataStore       = dataStore;
        this.appConfig       = appConfig;
        this.diskScanService = new DiskScanService(appConfig);
    }

    // ──────────────────────────────────────────────────────────────
    // 場景建立
    // ──────────────────────────────────────────────────────────────

    public Scene buildScene(Stage stage) {
        this.primaryStage = stage;

        tabBar = new RQTabBar();
        tabBar.setOnSelect(this::selectRQ);
        tabBar.setOnDelete(this::askDeleteRQ);

        Button newRqBtn = new Button("＋ 新增 RQ");
        newRqBtn.getStyleClass().add("btn-new-rq");
        newRqBtn.setOnAction(e -> openNewRQDialog());

        backupStatusLabel = new Label(BackupService.getStatusText(appConfig));
        backupStatusLabel.getStyleClass().add("backup-status");

        Button toolBtn = new Button("⚙ 工具 ▾");
        toolBtn.getStyleClass().add("btn-header-menu");
        toolBtn.setOnAction(e -> showToolMenu(toolBtn));

        Label titleLabel = new Label("RQ ");
        Label titleSub   = new Label("進度追蹤");
        titleSub.getStyleClass().add("app-title-sub");
        HBox titleBox = new HBox(titleLabel, titleSub);
        titleBox.getStyleClass().add("app-title");
        titleBox.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(tabBar, Priority.ALWAYS);

        HBox header = new HBox(12, titleBox, tabBar, backupStatusLabel, toolBtn, newRqBtn);
        header.getStyleClass().add("app-header-inner");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 16, 0, 16));

        VBox headerWrap = new VBox(header);
        headerWrap.getStyleClass().add("app-header");

        emptyState = buildEmptyState();
        rqContentArea = new StackPane();
        rqContentArea.setAlignment(Pos.TOP_LEFT);

        VBox mainContent = new VBox(emptyState, rqContentArea);
        mainContent.getStyleClass().add("main");
        VBox.setVgrow(rqContentArea, Priority.ALWAYS);

        ScrollPane contentScroll = new ScrollPane(mainContent);
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.getStyleClass().add("main-scroll");

        VBox layout = new VBox(headerWrap, contentScroll);
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        toast = new ToastNotification();
        toast.setPickOnBounds(false);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 20, 0));

        scanResultPanel = buildScanResultPanel();
        scanResultPanel.setVisible(false);
        scanResultPanel.setManaged(false);
        StackPane.setAlignment(scanResultPanel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(scanResultPanel, new Insets(0, 24, 60, 0));

        StackPane root = new StackPane(layout, toast, scanResultPanel);

        Scene scene = new Scene(root, 1200, 760);
        applyTheme(scene);
        return scene;
    }

    public void onAppStarted() {
        activeRqId = appConfig.getActiveRQ();
        refreshTabBar();
        List<String> ids = dataStore.getIndex();
        if (activeRqId != null && dataStore.getRQ(activeRqId) != null) {
            renderRQ(activeRqId);
        } else if (!ids.isEmpty()) {
            activeRqId = ids.get(0);
            renderRQ(activeRqId);
        } else {
            showEmptyState(true);
        }
        startScanScheduler();
        startBackup();
    }

    // ──────────────────────────────────────────────────────────────
    // RQ 選取 / 渲染
    // ──────────────────────────────────────────────────────────────

    private void selectRQ(String rqId) {
        activeRqId = rqId;
        appConfig.setActiveRQ(rqId);
        appConfig.save();
        refreshTabBar();
        renderRQ(rqId);
    }

    void renderRQ(String rqId) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) { showEmptyState(true); return; }

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        rqContentArea.setVisible(true);
        rqContentArea.setManaged(true);

        RQContentPanel panel = new RQContentPanel(
            dataStore, appConfig, rqId,
            lastScanResults,         // 掃描狀態圓點
            this::renderRQ,          // onRefresh
            this::showToast,         // onToast
            this::refreshTabBar,     // onTabRefresh
            this::openEditVersions,  // onEditVersions
            this::archiveRQ          // onArchive
        );
        rqContentArea.getChildren().setAll(panel);
    }

    // ──────────────────────────────────────────────────────────────
    // 標籤列
    // ──────────────────────────────────────────────────────────────

    void refreshTabBar() {
        List<String> ids = dataStore.getIndex();
        List<RQTabBar.TabEntry> entries = new ArrayList<>();
        for (String id : ids) {
            RQData rq = dataStore.getRQ(id);
            if (rq == null) continue;
            int pct = ProgressCalc.calcProgress(rq).percent();
            entries.add(new RQTabBar.TabEntry(id, pct));
        }
        tabBar.setTabs(entries, activeRqId);
    }

    // ──────────────────────────────────────────────────────────────
    // 新增 / 編輯 / 刪除 / 歸檔
    // ──────────────────────────────────────────────────────────────

    private void openNewRQDialog() {
        NewEditRQDialog dlg = new NewEditRQDialog(primaryStage, null);
        dlg.setOnSubmit(result -> {
            String rawId = result.rqId().trim();
            String rqId  = PathUtils.winSafeName(rawId);
            if (rqId.isBlank()) { showToast("RQ 編號不可為空白"); return; }
            if (dataStore.getRQ(rqId) != null) { showToast("RQ 編號已存在：" + rqId); return; }

            RQData rq = new RQData();
            rq.setId(rqId);
            rq.setProjectNum(result.projectNum().isBlank() ? null : result.projectNum());
            rq.setVersions(result.versionNames().stream()
                .map(RQVersion::new)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
            rq.setCreatedAt(DateTimeUtils.nowIso());

            dataStore.saveRQ(rqId, rq);
            activeRqId = rqId;
            appConfig.setActiveRQ(rqId);
            appConfig.save();
            refreshTabBar();
            renderRQ(rqId);
            showToast("已建立 " + rqId);
        });
        dlg.show();
    }

    private void openEditVersions(String rqId) {
        RQData existing = dataStore.getRQ(rqId);
        if (existing == null) return;
        NewEditRQDialog dlg = new NewEditRQDialog(primaryStage, existing);
        dlg.setOnSubmit(result -> {
            existing.setProjectNum(result.projectNum().isBlank() ? null : result.projectNum());
            List<RQVersion> newVersions = result.versionNames().stream()
                .map(RQVersion::new)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            existing.setVersions(newVersions);
            dataStore.saveRQ(rqId, existing);
            refreshTabBar();
            renderRQ(rqId);
            showToast("已更新版本設定");
        });
        dlg.show();
    }

    private void archiveRQ(String rqId) {
        ConfirmDialog.confirmArchive(primaryStage, rqId, () -> {
            dataStore.archiveRQ(rqId);
            List<String> ids = dataStore.getIndex();
            activeRqId = ids.isEmpty() ? null : ids.get(0);
            appConfig.setActiveRQ(activeRqId);
            appConfig.save();
            refreshTabBar();
            if (activeRqId != null) renderRQ(activeRqId);
            else showEmptyState(true);
            showToast("已歸檔 " + rqId);
        });
    }

    private void askDeleteRQ(String rqId) {
        ConfirmDialog.confirmDelete(primaryStage, rqId, () -> deleteRQ(rqId));
    }

    private void deleteRQ(String rqId) {
        dataStore.deleteRQ(rqId);
        List<String> ids = dataStore.getIndex();
        if (rqId.equals(activeRqId)) {
            activeRqId = ids.isEmpty() ? null : ids.get(0);
            appConfig.setActiveRQ(activeRqId);
            appConfig.save();
        }
        refreshTabBar();
        if (activeRqId != null) renderRQ(activeRqId);
        else showEmptyState(true);
        showToast("已刪除 " + rqId);
    }

    // ──────────────────────────────────────────────────────────────
    // 輔助方法
    // ──────────────────────────────────────────────────────────────

    private void showEmptyState(boolean empty) {
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        rqContentArea.setVisible(!empty);
        rqContentArea.setManaged(!empty);
    }

    public void showToast(String message) {
        Platform.runLater(() -> toast.show(message));
    }

    private VBox buildEmptyState() {
        Label icon = new Label("≡");
        icon.getStyleClass().add("empty-state-icon");
        Label h2 = new Label("尚無 RQ");
        h2.getStyleClass().add("empty-state-title");
        Label p  = new Label("點擊右上角「新增 RQ」開始追蹤");
        p.getStyleClass().add("empty-state-desc");
        VBox box = new VBox(12, icon, h2, p);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void showToolMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();

        MenuItem scanItem = new MenuItem("🔍 立即掃描磁碟");
        scanItem.setOnAction(e -> triggerManualScan());

        MenuItem createFolderItem = new MenuItem("📁 建立資料夾結構");
        createFolderItem.setOnAction(e -> triggerCreateFolders());

        MenuItem genListItem = new MenuItem("📋 產生 list.txt");
        genListItem.setOnAction(e -> triggerGenList());

        MenuItem backupItem = new MenuItem("💾 立即備份");
        backupItem.setOnAction(e -> triggerBackupNow());

        MenuItem historyItem = new MenuItem("📜 歷史紀錄");
        historyItem.setOnAction(e -> openHistoryDialog());

        MenuItem importItem = new MenuItem("📥 匯入 JSON");
        importItem.setOnAction(e -> ImportExportService.importFromFile(
            primaryStage, dataStore, this::showToast, () -> {
                List<String> ids = dataStore.getIndex();
                activeRqId = ids.isEmpty() ? null : ids.get(0);
                appConfig.setActiveRQ(activeRqId);
                appConfig.save();
                refreshTabBar();
                if (activeRqId != null) renderRQ(activeRqId);
                else showEmptyState(true);
            }));

        MenuItem exportItem = new MenuItem("📤 匯出 JSON");
        exportItem.setOnAction(e -> ImportExportService.exportToFile(
            primaryStage, dataStore, this::showToast));

        MenuItem settingsItem = new MenuItem("⚙ 設定");
        settingsItem.setOnAction(e -> {
            SettingsDialog dlg = new SettingsDialog(primaryStage, appConfig);
            dlg.setOnSaved(() -> {
                if (activeRqId != null) renderRQ(activeRqId);
            });
            dlg.show();
        });

        menu.getItems().addAll(
            scanItem, createFolderItem, genListItem, new SeparatorMenuItem(),
            backupItem, historyItem, new SeparatorMenuItem(),
            importItem, exportItem, new SeparatorMenuItem(), settingsItem);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void openHistoryDialog() {
        HistoryDialog dlg = new HistoryDialog(primaryStage, dataStore, appConfig,
            restored -> {
                activeRqId = restored;
                appConfig.setActiveRQ(restored);
                appConfig.save();
                refreshTabBar();
                renderRQ(restored);
                showToast("已復原 " + restored);
            });
        dlg.show();
    }

    // ──────────────────────────────────────────────────────────────
    // 磁碟掃描
    // ──────────────────────────────────────────────────────────────

    private void startScanScheduler() {
        scanScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DiskScanner");
            t.setDaemon(true);
            return t;
        });
        // 啟動後 5 秒執行第一次，之後每 3 分鐘
        scanScheduler.scheduleAtFixedRate(this::runScan, 5, 180, TimeUnit.SECONDS);
    }

    /** 在背景執行緒掃描，完成後回 UI 執行緒更新 */
    private void runScan() {
        if (activeRqId == null) return;
        RQData rq = dataStore.getRQ(activeRqId);
        if (rq == null) return;

        Map<String, TaskResult> results = diskScanService.scan(rq);

        boolean anyChecked = diskScanService.autoCheck(rq, results);
        if (anyChecked) dataStore.saveRQ(activeRqId, rq);

        Platform.runLater(() -> {
            lastScanResults = results;
            if (anyChecked) {
                refreshTabBar();
                renderRQ(activeRqId);
            } else {
                // 只更新圓點，重建 panel 即可
                renderRQ(activeRqId);
            }
            showScanResultNotification(results, rq);
        });
    }

    private void triggerManualScan() {
        showToast("掃描中…");
        if (scanScheduler != null) scanScheduler.submit(this::runScan);
    }

    private void triggerCreateFolders() {
        if (activeRqId == null) { showToast("請先選擇 RQ"); return; }
        RQData rq = dataStore.getRQ(activeRqId);
        if (rq == null) return;
        int[] result = FolderCreatorService.createFolders(rq, appConfig.getDownloadsRoot());
        int created = result[0], failed = result[1];
        showToast(failed == 0
            ? "✓ 已建立 " + created + " 個資料夾"
            : "⚠ 建立完成：" + created + " 成功，" + failed + " 失敗");
    }

    private void triggerGenList() {
        if (activeRqId == null) { showToast("請先選擇 RQ"); return; }
        RQData rq = dataStore.getRQ(activeRqId);
        if (rq == null) return;
        String content = FileListGenerator.generate(rq, appConfig.getDownloadsRoot());
        java.nio.file.Path written = FileListGenerator.writeListFile(
            rq, appConfig.getDownloadsRoot(), content);
        showToast(written != null ? "✓ list.txt 已寫入" : "⚠ 已產生清單，但無法寫入磁碟");
        // 顯示清單預覽（共用剪貼板工具）
        com.rqtracker.util.ClipboardUtils.copyText(content);
        showToast("✓ 已複製清單至剪貼板");
    }

    /** 顯示掃描結果通知面板（右下角） */
    private void showScanResultNotification(Map<String, TaskResult> results, RQData rq) {
        long broken = results.values().stream()
            .filter(r -> r.state() != TaskResult.ScanState.FILE &&
                         r.state() != TaskResult.ScanState.UNKNOWN)
            .count();
        long done = results.values().stream()
            .filter(r -> r.state() == TaskResult.ScanState.FILE)
            .count();

        if (broken == 0 && done == 0) {
            scanResultPanel.setVisible(false);
            scanResultPanel.setManaged(false);
            return;
        }

        // 重建面板內容
        scanResultPanel.getChildren().clear();
        String time = DateTimeUtils.nowZhTW().substring(11); // HH:mm
        Label title = new Label("🔍 掃描完成 " + time +
            "　檔案齊全 " + done + "　待確認 " + broken);
        title.getStyleClass().add("srp-title");
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("icon-btn-close");
        closeBtn.setOnAction(e -> {
            scanResultPanel.setVisible(false);
            scanResultPanel.setManaged(false);
        });
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox hdr = new HBox(title, sp, closeBtn);
        hdr.setAlignment(Pos.CENTER_LEFT);
        scanResultPanel.getChildren().add(hdr);
        scanResultPanel.setVisible(true);
        scanResultPanel.setManaged(true);
    }

    private VBox buildScanResultPanel() {
        VBox panel = new VBox(6);
        panel.getStyleClass().add("scan-result-panel");
        panel.setPrefWidth(300);
        panel.setPickOnBounds(false);
        return panel;
    }

    // ──────────────────────────────────────────────────────────────
    // 備份
    // ──────────────────────────────────────────────────────────────

    private void startBackup() {
        Thread t = new Thread(() -> {
            boolean ok = BackupService.runIfNeeded(dataStore, appConfig);
            Platform.runLater(() -> {
                backupStatusLabel.setText(BackupService.getStatusText(appConfig));
                if (ok) showToast("✓ 備份完成");
            });
        }, "BackupService");
        t.setDaemon(true);
        t.start();
    }

    private void triggerBackupNow() {
        Thread t = new Thread(() -> {
            boolean ok = BackupService.runNow(dataStore, appConfig);
            Platform.runLater(() -> {
                backupStatusLabel.setText(BackupService.getStatusText(appConfig));
                showToast(ok ? "✓ 備份完成" : "✗ 備份失敗（請確認備份資料夾設定）");
            });
        }, "BackupService-Manual");
        t.setDaemon(true);
        t.start();
    }

    /** 關閉排程（App 結束時呼叫） */
    public void shutdown() {
        if (scanScheduler != null) scanScheduler.shutdownNow();
        dataStore.shutdown();
    }

    private void showComingSoon(String feature) {
        showToast(feature + " — 即將實作");
    }

    private void applyTheme(Scene scene) {
        try {
            var css = getClass().getResource("/css/rq-theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception e) {
            System.err.println("無法載入 CSS：" + e.getMessage());
        }
    }
}
