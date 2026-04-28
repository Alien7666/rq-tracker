package com.rqtracker.ui.controller;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskResult;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.BackupService;
import com.rqtracker.service.DataStore;
import com.rqtracker.service.DiskScanService;
import com.rqtracker.service.FileListGenerator;
import com.rqtracker.service.FolderCreatorService;
import com.rqtracker.service.ImportExportService;
import com.rqtracker.service.ProgressCalc;
import com.rqtracker.ui.component.ConfirmDialog;
import com.rqtracker.ui.component.RQContentPanel;
import com.rqtracker.ui.component.RQTabBar;
import com.rqtracker.ui.component.SidePanel;
import com.rqtracker.ui.component.ToastNotification;
import com.rqtracker.BuildInfo;
import com.rqtracker.service.UpdateService;
import com.rqtracker.ui.dialog.HistoryDialog;
import com.rqtracker.ui.dialog.NewEditRQDialog;
import com.rqtracker.ui.dialog.SettingsDialog;
import com.rqtracker.ui.dialog.UpdateDialog;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.PathUtils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 主控制器：協調側邊欄、RQ 內容區域、Toast、Confirm 等子元件。
 * v2：BorderPane 側邊欄架構，取代原本的水平 RQTabBar。
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    private final DataStore        dataStore;
    private final AppConfig        appConfig;
    private final DiskScanService  diskScanService;

    private Stage             primaryStage;
    private SidePanel         sidePanel;
    private BorderPane        appLayout;     // 主佈局（毛玻璃 blur 的目標）
    private BorderPane        contentPane;   // CENTER of app-layout
    private StackPane         rqContentArea;
    private ToastNotification toast;
    private VBox              scanResultPanel;
    private StackPane         rootPane;      // 用於手動定位 scanResultPanel
    private Pane              glassPane;     // 毛玻璃遮罩
    private Stage             activeModal;   // 目前顯示中的子視窗

    // RQ Header 元件（顯示在 contentPane TOP）
    private HBox  rqHeader;
    private Label rqHeaderTitle;
    private Label rqHeaderSub;
    private ProgressBar rqHeaderBar;
    private Label rqHeaderPct;
    private Button rqFolderBtn;
    private Button rqEditVerBtn;
    private Button rqArchiveBtn;
    private Button rqScanBtn;

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

        // ── 左側邊欄 ────────────────────────────────────────────────
        sidePanel = new SidePanel(this::openNewRQDialog);
        sidePanel.setOnSelect(this::selectRQ);
        sidePanel.setOnDelete(this::askDeleteRQ);
        wireSidePanelMenu();

        // ── RQ Header（頂部標題列） ─────────────────────────────────
        rqHeader = buildRQHeader();
        rqHeader.setVisible(false);
        rqHeader.setManaged(false);

        // ── 空狀態 ───────────────────────────────────────────────────
        VBox emptyState = buildEmptyState();

        // ── 內容捲動區 ───────────────────────────────────────────────
        rqContentArea = new StackPane();
        rqContentArea.setAlignment(Pos.TOP_LEFT);

        ScrollPane contentScroll = new ScrollPane(rqContentArea);
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScroll.getStyleClass().add("main-scroll");
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        // ── 右側主佈局（標題 + 內容 OR 空狀態） ─────────────────────
        contentPane = new BorderPane();
        contentPane.getStyleClass().add("content-area");
        contentPane.setTop(rqHeader);
        contentPane.setCenter(emptyState);   // 預設顯示空狀態

        // 方便切換：儲存 contentScroll 與 emptyState 的引用
        contentPane.setUserData(new Object[]{ emptyState, contentScroll });

        // ── 整體 BorderPane 佈局 ─────────────────────────────────────
        appLayout = new BorderPane();
        appLayout.getStyleClass().add("app-layout");
        appLayout.setLeft(sidePanel);
        appLayout.setCenter(contentPane);

        // ── 毛玻璃遮罩（子視窗開啟時顯示） ─────────────────────────
        glassPane = new Pane();
        glassPane.getStyleClass().add("glass-overlay");
        glassPane.setVisible(false);
        glassPane.setOnMouseClicked(e -> {
            if (activeModal != null && activeModal.isShowing()) {
                activeModal.close();
            }
        });

        // ── Overlay：Toast + 掃描結果面板 ───────────────────────────
        toast = new ToastNotification();
        toast.setPickOnBounds(false);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 20, 0));

        scanResultPanel = buildScanResultPanel();
        scanResultPanel.setVisible(false);
        scanResultPanel.setManaged(false);

        StackPane root = new StackPane(appLayout, glassPane, toast, scanResultPanel);
        root.getStyleClass().add("app-root");
        rootPane = root;

        // 每當視窗大小變化時重新定位掃描結果面板
        root.widthProperty().addListener((o, ov, w) -> repositionScanPanel());
        root.heightProperty().addListener((o, ov, h) -> repositionScanPanel());

        Scene scene = new Scene(root);
        applyTheme(scene);
        return scene;
    }

    public void onAppStarted() {
        activeRqId = appConfig.getActiveRQ();
        refreshSidePanel();
        List<String> ids = dataStore.getIndex();
        if (activeRqId != null && dataStore.getRQ(activeRqId) != null) {
            renderRQ(activeRqId);
        } else if (!ids.isEmpty()) {
            activeRqId = ids.get(0);
            renderRQ(activeRqId);
        } else {
            showEmptyState();
        }
        startScanScheduler();
        startBackup();
        scheduleUpdateCheck();
    }

    // ──────────────────────────────────────────────────────────────
    // RQ 標題列（固定於內容區頂部）
    // ──────────────────────────────────────────────────────────────

    private HBox buildRQHeader() {
        rqHeaderTitle = new Label();
        rqHeaderTitle.getStyleClass().add("rq-header-title");

        rqHeaderSub = new Label();
        rqHeaderSub.getStyleClass().add("rq-header-sub");

        VBox titleBox = new VBox(2, rqHeaderTitle, rqHeaderSub);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        rqHeaderBar = new ProgressBar(0);
        rqHeaderBar.getStyleClass().add("rq-header-bar");
        rqHeaderBar.setPrefWidth(200);
        rqHeaderBar.setPrefHeight(11);

        rqHeaderPct = new Label("0%");
        rqHeaderPct.getStyleClass().add("rq-header-pct");

        rqFolderBtn = new Button("📂 資料夾清單");
        rqFolderBtn.getStyleClass().addAll("btn-ghost", "btn-sm");
        rqFolderBtn.setTooltip(new Tooltip("查看目前 RQ 的資料夾與檔案狀態"));
        rqFolderBtn.setOnAction(e -> openFolderList());

        rqEditVerBtn = new Button("✏ 編輯版本");
        rqEditVerBtn.getStyleClass().addAll("btn-ghost", "btn-sm");
        rqEditVerBtn.setTooltip(new Tooltip("調整此 RQ 的版本清單"));
        rqEditVerBtn.setOnAction(e -> { if (activeRqId != null) openEditVersions(activeRqId); });

        rqArchiveBtn = new Button("▣ 歸檔");
        rqArchiveBtn.getStyleClass().addAll("btn-archive", "btn-sm");
        rqArchiveBtn.setTooltip(new Tooltip("將已完成的 RQ 移到歸檔"));
        rqArchiveBtn.setVisible(false);
        rqArchiveBtn.setManaged(false);
        rqArchiveBtn.setOnAction(e -> { if (activeRqId != null) archiveRQ(activeRqId); });

        rqScanBtn = new Button("⟳ 掃描");
        rqScanBtn.getStyleClass().addAll("btn-ghost", "btn-sm");
        rqScanBtn.setTooltip(new Tooltip("立即重新掃描此 RQ 的檔案狀態"));
        rqScanBtn.setOnAction(e -> triggerManualScan());

        HBox header = new HBox(12, titleBox, rqHeaderBar, rqHeaderPct,
                               rqScanBtn, rqFolderBtn, rqEditVerBtn, rqArchiveBtn);
        header.getStyleClass().add("rq-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void updateRQHeader(RQData rq) {
        rqHeaderTitle.setText(rq.getId());
        rqHeaderSub.setText(rq.getProjectNum() != null ? "專案：" + rq.getProjectNum() : "");

        int pct = ProgressCalc.calcProgress(rq).percent();
        rqHeaderBar.setProgress(pct / 100.0);
        rqHeaderBar.getStyleClass().removeAll("done");
        if (pct >= 100) rqHeaderBar.getStyleClass().add("done");

        rqHeaderPct.setText(pct + "%");
        rqHeaderPct.getStyleClass().removeAll("done");
        if (pct >= 100) rqHeaderPct.getStyleClass().add("done");

        boolean done = pct >= 100;
        rqArchiveBtn.setVisible(done);
        rqArchiveBtn.setManaged(done);

        rqHeader.setVisible(true);
        rqHeader.setManaged(true);
    }

    // ──────────────────────────────────────────────────────────────
    // RQ 選取 / 渲染
    // ──────────────────────────────────────────────────────────────

    private void selectRQ(String rqId) {
        activeRqId = rqId;
        appConfig.setActiveRQ(rqId);
        appConfig.save();
        refreshSidePanel();
        renderRQ(rqId);
    }

    void renderRQ(String rqId) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) { showEmptyState(); return; }

        updateRQHeader(rq);

        Object[] userData = (Object[]) contentPane.getUserData();
        ScrollPane contentScroll = (ScrollPane) userData[1];
        contentPane.setCenter(contentScroll);

        if (!rqContentArea.getChildren().isEmpty() &&
                rqContentArea.getChildren().get(0) instanceof RQContentPanel old) {
            old.dispose();
        }

        RQContentPanel panel = new RQContentPanel(
            dataStore, appConfig, rqId,
            lastScanResults,
            this::renderRQ,
            this::showToast,
            this::refreshSidePanel,
            this::openEditVersions,
            this::archiveRQ,
            this::showGlass
        );
        rqContentArea.getChildren().setAll(panel);
    }

    // ──────────────────────────────────────────────────────────────
    // 側邊欄刷新（取代 refreshTabBar）
    // ──────────────────────────────────────────────────────────────

    void refreshSidePanel() {
        List<String> ids = dataStore.getIndex();
        List<RQTabBar.TabEntry> entries = new ArrayList<>();
        for (String id : ids) {
            RQData rq = dataStore.getRQ(id);
            if (rq == null) continue;
            int pct = ProgressCalc.calcProgress(rq).percent();
            entries.add(new RQTabBar.TabEntry(id, pct));
        }
        sidePanel.setRQList(entries, activeRqId);
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
                .collect(Collectors.toCollection(ArrayList::new)));
            rq.setCreatedAt(DateTimeUtils.nowIso());

            dataStore.saveRQ(rqId, rq);
            activeRqId = rqId;
            appConfig.setActiveRQ(rqId);
            appConfig.save();
            refreshSidePanel();
            renderRQ(rqId);
            showToast("已建立 " + rqId);
        });
        dlg.show();
        showGlass(dlg.getStage());
    }

    private void openEditVersions(String rqId) {
        RQData existing = dataStore.getRQ(rqId);
        if (existing == null) return;
        NewEditRQDialog dlg = new NewEditRQDialog(primaryStage, existing);
        dlg.setOnSubmit(result -> {
            existing.setProjectNum(result.projectNum().isBlank() ? null : result.projectNum());
            existing.setVersions(result.versionNames().stream()
                .map(RQVersion::new)
                .collect(Collectors.toCollection(ArrayList::new)));
            dataStore.saveRQ(rqId, existing);
            refreshSidePanel();
            renderRQ(rqId);
            showToast("已更新版本設定");
        });
        dlg.show();
        showGlass(dlg.getStage());
    }

    private void openFolderList() {
        if (activeRqId == null) return;
        try {
            com.rqtracker.ui.dialog.FolderListDialog dlg =
                new com.rqtracker.ui.dialog.FolderListDialog(
                    primaryStage, dataStore, appConfig, activeRqId);
            dlg.show();
            showGlass(dlg.getStage());
        } catch (Exception ignored) {}
    }

    private void archiveRQ(String rqId) {
        ConfirmDialog.confirmArchive(primaryStage, rqId, () -> {
            dataStore.archiveRQ(rqId);
            List<String> ids = dataStore.getIndex();
            activeRqId = ids.isEmpty() ? null : ids.get(0);
            appConfig.setActiveRQ(activeRqId);
            appConfig.save();
            refreshSidePanel();
            if (activeRqId != null) renderRQ(activeRqId);
            else showEmptyState();
            showToast("已歸檔 " + rqId);
        }, this::showGlass);
    }

    private void askDeleteRQ(String rqId) {
        ConfirmDialog.confirmDelete(primaryStage, rqId, () -> deleteRQ(rqId), this::showGlass);
    }

    private void deleteRQ(String rqId) {
        dataStore.deleteRQ(rqId);
        List<String> ids = dataStore.getIndex();
        if (rqId.equals(activeRqId)) {
            activeRqId = ids.isEmpty() ? null : ids.get(0);
            appConfig.setActiveRQ(activeRqId);
            appConfig.save();
        }
        refreshSidePanel();
        if (activeRqId != null) renderRQ(activeRqId);
        else showEmptyState();
        showToast("已刪除 " + rqId);
    }

    // ──────────────────────────────────────────────────────────────
    // 側邊欄工具選單接線
    // ──────────────────────────────────────────────────────────────

    private void wireSidePanelMenu() {
        sidePanel.menuSettings.setOnAction(e -> {
            SettingsDialog dlg = new SettingsDialog(primaryStage, appConfig);
            dlg.setOnSaved(() -> {
                if (activeRqId != null) renderRQ(activeRqId);
                showToast("✓ 設定已儲存");
            });
            dlg.show();
            showGlass(dlg.getStage());
        });

        sidePanel.menuHistory.setOnAction(e -> openHistoryDialog());

        sidePanel.menuImport.setOnAction(e ->
            ImportExportService.importFromFile(
                primaryStage, dataStore, this::showToast, () -> {
                    List<String> ids = dataStore.getIndex();
                    activeRqId = ids.isEmpty() ? null : ids.get(0);
                    appConfig.setActiveRQ(activeRqId);
                    appConfig.save();
                    refreshSidePanel();
                    if (activeRqId != null) renderRQ(activeRqId);
                    else showEmptyState();
                }));

        sidePanel.menuExport.setOnAction(e ->
            ImportExportService.exportToFile(primaryStage, dataStore, this::showToast));

        sidePanel.menuCreateDirs.setOnAction(e -> triggerCreateFolders());
        sidePanel.menuFileList.setOnAction(e -> triggerGenList());
        sidePanel.menuCheckUpdate.setOnAction(e -> openUpdateDialog());
        sidePanel.getUpdateBadge().setOnMouseClicked(e -> openUpdateDialog());
    }

    private void openUpdateDialog() {
        UpdateDialog dlg = new UpdateDialog(primaryStage, appConfig,
            appConfig.getUpdateCheckUrl());
        dlg.show();
        showGlass(dlg.getStage());
    }

    private void scheduleUpdateCheck() {
        String url = appConfig.getUpdateCheckUrl();
        if (url == null || url.isBlank()) return;
        String today = java.time.LocalDate.now().toString();
        if (today.equals(appConfig.getLastUpdateCheckDate())) return;

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(5000);
                var info = UpdateService.checkForUpdate(url, BuildInfo.VERSION);
                info.ifPresent(u -> {
                    if (u.version().equals(appConfig.getSkipVersion())) return;
                    Platform.runLater(() -> sidePanel.setUpdateAvailable(u.version()));
                });
                appConfig.setLastUpdateCheckDate(today);
                appConfig.save();
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                LOG.warning("自動更新檢查失敗：" + e.getMessage());
            }
        }, "UpdateCheck");
        t.setDaemon(true);
        t.start();
    }

    private void openHistoryDialog() {
        HistoryDialog dlg = new HistoryDialog(primaryStage, dataStore, appConfig,
            restored -> {
                activeRqId = restored;
                appConfig.setActiveRQ(restored);
                appConfig.save();
                refreshSidePanel();
                renderRQ(restored);
                showToast("已復原 " + restored);
            });
        dlg.show();
        showGlass(dlg.getStage());
    }

    // ──────────────────────────────────────────────────────────────
    // 輔助方法
    // ──────────────────────────────────────────────────────────────

    private void showEmptyState() {
        rqHeader.setVisible(false);
        rqHeader.setManaged(false);
        Object[] userData = (Object[]) contentPane.getUserData();
        VBox emptyState = (VBox) userData[0];
        contentPane.setCenter(emptyState);
        rqContentArea.getChildren().clear();
    }

    public void showToast(String message) {
        Platform.runLater(() -> toast.show(message));
    }

    void showGlass(Stage modal) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> showGlass(modal)); return; }
        activeModal = modal;
        appLayout.setEffect(new GaussianBlur(10));
        glassPane.setVisible(true);
        modal.setOnHidden(e -> hideGlass());
    }

    private void hideGlass() {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(this::hideGlass); return; }
        activeModal = null;
        appLayout.setEffect(null);
        glassPane.setVisible(false);
    }

    private VBox buildEmptyState() {
        Label icon = new Label("≡");
        icon.getStyleClass().add("empty-state-icon");
        Label h2 = new Label("尚無 RQ");
        h2.getStyleClass().add("empty-state-title");
        Label p  = new Label("點擊左側「＋ 新增 RQ」，或直接按下方按鈕開始追蹤");
        p.getStyleClass().add("empty-state-desc");
        Button quickAdd = new Button("＋ 立即新增 RQ");
        quickAdd.getStyleClass().add("btn-primary");
        quickAdd.setOnAction(e -> openNewRQDialog());
        VBox box = new VBox(12, icon, h2, p, quickAdd);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
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
        scanScheduler.scheduleAtFixedRate(this::runScan, 5, 180, TimeUnit.SECONDS);
    }

    private void runScan() {
        if (activeRqId == null) return;
        RQData rq = dataStore.getRQ(activeRqId);
        if (rq == null) return;

        Platform.runLater(() -> sidePanel.setScanIndicator(true));

        Map<String, TaskResult> results = diskScanService.scan(rq);
        boolean anyChecked = diskScanService.autoCheck(rq, results);
        if (anyChecked) dataStore.saveRQ(activeRqId, rq);

        Platform.runLater(() -> {
            sidePanel.setScanIndicator(false);
            lastScanResults = results;
            if (anyChecked) refreshSidePanel();
            renderRQ(activeRqId);
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
        showToast(result[1] == 0
            ? "✓ 已建立 " + result[0] + " 個資料夾"
            : "⚠ 建立完成：" + result[0] + " 成功，" + result[1] + " 失敗");
    }

    private void triggerGenList() {
        if (activeRqId == null) { showToast("請先選擇 RQ"); return; }
        RQData rq = dataStore.getRQ(activeRqId);
        if (rq == null) return;
        String content = FileListGenerator.generate(rq, appConfig.getDownloadsRoot());
        java.nio.file.Path written = FileListGenerator.writeListFile(
            rq, appConfig.getDownloadsRoot(), content);
        com.rqtracker.util.ClipboardUtils.copyText(content);
        showToast(written != null ? "✓ list.txt 已寫入並複製至剪貼板" : "✓ 已複製清單至剪貼板");
    }

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
            return;
        }

        scanResultPanel.getChildren().clear();
        String time = DateTimeUtils.nowZhTW().substring(11);
        Label title = new Label("◉ 掃描完成 " + time +
            "　齊全 " + done + "　待確認 " + broken);
        title.getStyleClass().add("srp-title");
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("icon-btn-close");
        closeBtn.setOnAction(e -> scanResultPanel.setVisible(false));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox hdr = new HBox(title, sp, closeBtn);
        hdr.setAlignment(Pos.CENTER_LEFT);
        scanResultPanel.getChildren().add(hdr);
        // 先定位再顯示，避免瞬間閃現在錯誤位置
        repositionScanPanel();
        scanResultPanel.setVisible(true);
    }

    private VBox buildScanResultPanel() {
        VBox panel = new VBox(6);
        panel.getStyleClass().add("scan-result-panel");
        panel.setPrefWidth(300);
        panel.setPickOnBounds(false);
        return panel;
    }

    /** 將掃描結果面板定位到右下角（managed=false，需手動設 layoutX/Y）。 */
    private void repositionScanPanel() {
        if (rootPane == null) return;
        double pw = scanResultPanel.prefWidth(-1);
        double ph = scanResultPanel.prefHeight(pw);
        scanResultPanel.setLayoutX(rootPane.getWidth() - pw - 24);
        scanResultPanel.setLayoutY(rootPane.getHeight() - ph - 60);
    }

    // ──────────────────────────────────────────────────────────────
    // 備份
    // ──────────────────────────────────────────────────────────────

    private void startBackup() {
        Thread t = new Thread(() -> {
            boolean ok = BackupService.runIfNeeded(dataStore, appConfig);
            Platform.runLater(() -> {
                sidePanel.setBackupStatus(BackupService.getStatusText(appConfig), !ok && !appConfig.hasBackupDir());
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
                sidePanel.setBackupStatus(BackupService.getStatusText(appConfig), !ok);
                showToast(ok ? "✓ 備份完成" : "✗ 備份失敗（請確認備份資料夾設定）");
            });
        }, "BackupService-Manual");
        t.setDaemon(true);
        t.start();
    }

    public void shutdown() {
        if (scanScheduler != null) scanScheduler.shutdownNow();
        dataStore.shutdown();
        if (!rqContentArea.getChildren().isEmpty() &&
                rqContentArea.getChildren().get(0) instanceof RQContentPanel last) {
            last.dispose();
        }
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
