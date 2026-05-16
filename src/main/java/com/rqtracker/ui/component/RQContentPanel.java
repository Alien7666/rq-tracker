package com.rqtracker.ui.component;

import com.rqtracker.model.RQData;
import com.rqtracker.model.RQVersion;
import com.rqtracker.model.TaskDef;
import com.rqtracker.model.TaskResult;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DataStore;
import com.rqtracker.service.ProgressCalc;
import com.rqtracker.service.TaskCascadeService;
import com.rqtracker.service.TaskFactory;
import com.rqtracker.ui.dialog.VerFilesDialog;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.PathUtils;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RQ 內容面板：進度列、備忘錄、共用流程、版本卡片、最終交付。
 * 對應 HTML 的 renderRQ() 函數。
 */
public class RQContentPanel extends VBox {

    private final DataStore  dataStore;
    private final AppConfig  appConfig;
    private final String     rqId;

    /** 外部回呼：要求重新渲染（通常是 MainController.renderRQ） */
    private final Consumer<String>  onRefresh;
    /** 外部回呼：顯示 Toast */
    private final Consumer<String>  onToast;
    /** 外部回呼：刷新 TabBar */
    private final Runnable          onTabRefresh;
    /** 外部回呼：開啟編輯版本對話框 */
    private final Consumer<String>  onEditVersions;
    /** 外部回呼：歸檔 */
    private final Consumer<String>  onArchive;
    /** 外部回呼：顯示毛玻璃（傳入子視窗 Stage） */
    private final Consumer<javafx.stage.Stage> onShowGlass;

    /** 最近一次磁碟掃描結果（key=taskKey, value=掃描狀態） */
    private final Map<String, TaskResult> scanResults;

    private PauseTransition noteDebounce;
    private PauseTransition noteResizeDebounce;
    private PauseTransition splitResizeDebounce;

    public void dispose() {
        if (noteDebounce        != null) noteDebounce.stop();
        if (noteResizeDebounce  != null) noteResizeDebounce.stop();
        if (splitResizeDebounce != null) splitResizeDebounce.stop();
    }

    public RQContentPanel(DataStore dataStore, AppConfig appConfig, String rqId,
                          Map<String, TaskResult> scanResults,
                          Consumer<String> onRefresh, Consumer<String> onToast,
                          Runnable onTabRefresh, Consumer<String> onEditVersions,
                          Consumer<String> onArchive,
                          Consumer<javafx.stage.Stage> onShowGlass) {
        this.dataStore      = dataStore;
        this.appConfig      = appConfig;
        this.rqId           = rqId;
        this.scanResults    = scanResults != null ? scanResults : Map.of();
        this.onRefresh      = onRefresh;
        this.onToast        = onToast;
        this.onTabRefresh   = onTabRefresh;
        this.onEditVersions = onEditVersions;
        this.onArchive      = onArchive;
        this.onShowGlass    = onShowGlass;

        setSpacing(12);
        setPadding(new Insets(12, 16, 16, 16));
        build();
    }

    // ──────────────────────────────────────────────────────────────
    // 完整建構
    // ──────────────────────────────────────────────────────────────

    private void build() {
        getChildren().clear();
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;

        String dlRoot = appConfig.getDownloadsRoot();
        String svnRoot = appConfig.getSvnRoot();

        ProgressCalc.Progress prog = ProgressCalc.calcProgress(rq, dlRoot, svnRoot);
        int pct = prog.percent();

        // ── 100% 完成 → 歸檔橫幅（顯示在最頂部） ────────────────
        if (pct >= 100) {
            getChildren().add(buildArchiveBanner(rqId));
        }

        // ── 備忘錄 ────────────────────────────────────────────────
        getChildren().add(buildNoteBlock(rq));

        // ── 共用流程（預設收闔） ────────────────────────────────────
        List<TaskDef> sfTasks = TaskFactory.sharedFlowTasks();
        boolean sfCollapsed = rq.getCollapseState().getOrDefault("scard_共用流程", true);
        VBox sfCard = buildSectionCard("≡", "共用流程", "", sfTasks, rq, true, sfCollapsed,
            "scard_共用流程", null, null);
        sfCard.getStyleClass().add("type-flow");
        getChildren().add(sfCard);

        // ── 版本卡片 ───────────────────────────────────────────────
        if (rq.getVersions().isEmpty()) {
            Label noVer = new Label("尚無版本，請編輯此 RQ 以新增版本。");
            noVer.getStyleClass().add("no-version-hint");
            getChildren().add(noVer);
        } else {
            for (int i = 0; i < rq.getVersions().size(); i++) {
                getChildren().add(buildVersionCard(rq, i, dlRoot, svnRoot));
            }
        }

        // ── 最終交付 ──────────────────────────────────────────────
        List<TaskDef> finTasks = TaskFactory.finalDeliveryTasks(rq, dlRoot);
        List<TaskDef> svnShared = TaskFactory.sharedSVNTasks(rq, svnRoot);
        List<TaskDef> finAllRequired = new ArrayList<>();
        finTasks.stream().filter(t -> !t.isOptional()).forEach(finAllRequired::add);
        finAllRequired.addAll(svnShared);

        boolean finCollapsed = rq.getCollapseState().getOrDefault("scard_共用最終交付", false);
        VBox finCard = buildSectionCard("▪", "共用最終交付", "", finTasks, rq, false, finCollapsed,
            "scard_共用最終交付", svnShared, rq.getProjectNum());
        finCard.getStyleClass().add("type-delivery");
        getChildren().add(finCard);
    }

    // ──────────────────────────────────────────────────────────────
    // 備忘錄
    // ──────────────────────────────────────────────────────────────

    private VBox buildNoteBlock(RQData rq) {
        boolean noteCollapsed = rq.getCollapseState().getOrDefault("noteBlock", false);

        Label noteIcon  = new Label("📝");
        Label noteTitle = new Label("備忘 / 做到哪裡了");
        noteTitle.getStyleClass().add("note-header-title");
        Label noteSaved = new Label("已儲存");
        noteSaved.getStyleClass().add("note-saved");
        noteSaved.setVisible(false);
        Label collapseIcon = new Label(noteCollapsed ? "▸" : "▾");
        collapseIcon.getStyleClass().add("note-collapse-icon");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox noteHeader = new HBox(6, noteIcon, noteTitle, spacer, noteSaved, collapseIcon);
        noteHeader.getStyleClass().add("note-header");
        noteHeader.setAlignment(Pos.CENTER_LEFT);
        noteHeader.setPadding(new Insets(8, 12, 8, 12));

        TextArea noteArea = new TextArea(rq.getNote() != null ? rq.getNote() : "");
        noteArea.getStyleClass().add("note-textarea");
        noteArea.setPromptText("記錄目前進度、待確認事項、下次要接著做的地方…");
        noteArea.setWrapText(true);
        double initH = appConfig.getNoteHeight() > 0 ? appConfig.getNoteHeight() : 90;
        noteArea.setMinHeight(initH);
        noteArea.setPrefHeight(initH);
        noteArea.setMaxHeight(initH);

        // debounce 600ms 自動儲存（對應 HTML 的 scheduleNoteSave）
        noteDebounce = new PauseTransition(Duration.millis(600));
        noteDebounce.setOnFinished(e -> {
            RQData latest = dataStore.getRQ(rqId);
            if (latest == null) return;
            latest.setNote(noteArea.getText());
            dataStore.saveRQ(rqId, latest);
            String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            noteSaved.setText("已儲存 " + t);
            noteSaved.setVisible(true);
            PauseTransition hideTimer = new PauseTransition(Duration.seconds(2));
            hideTimer.setOnFinished(ev -> noteSaved.setVisible(false));
            hideTimer.play();
            if (onTabRefresh != null) onTabRefresh.run();
        });
        noteArea.textProperty().addListener((obs, o, n) -> {
            noteSaved.setVisible(false);
            noteDebounce.playFromStart();
        });

        // 垂直拖曳把手：8px 條 + 裝飾文字，拖動改變備忘錄高度
        Label dragDecoLabel = new Label("— — —");
        dragDecoLabel.getStyleClass().add("note-resize-deco");
        dragDecoLabel.setMouseTransparent(true);

        Pane dragHandle = new Pane();
        dragHandle.getStyleClass().add("note-resize-handle");
        dragHandle.setPrefHeight(8);
        dragHandle.setMaxWidth(Double.MAX_VALUE);

        StackPane dragWrapper = new StackPane(dragHandle, dragDecoLabel);
        dragWrapper.setAlignment(Pos.CENTER);
        dragWrapper.setMaxWidth(Double.MAX_VALUE);
        dragWrapper.setCursor(Cursor.V_RESIZE);

        final double[] drag = {0, 0};
        dragWrapper.setOnMousePressed(ev -> {
            drag[0] = ev.getScreenY();
            drag[1] = noteArea.getHeight();
            ev.consume();
        });
        dragWrapper.setOnMouseDragged(ev -> {
            double newH = Math.max(50, drag[1] + ev.getScreenY() - drag[0]);
            noteArea.setMinHeight(newH);
            noteArea.setPrefHeight(newH);
            noteArea.setMaxHeight(newH);
            appConfig.setNoteHeight((int) newH);
            if (noteResizeDebounce == null) {
                noteResizeDebounce = new PauseTransition(Duration.millis(500));
                noteResizeDebounce.setOnFinished(e2 -> appConfig.save());
            }
            noteResizeDebounce.playFromStart();
            ev.consume();
        });

        VBox noteBody = new VBox(noteArea, dragWrapper);
        noteBody.getStyleClass().add("note-body");
        noteBody.setPadding(new Insets(0, 12, 4, 12));
        noteBody.setManaged(!noteCollapsed);
        noteBody.setVisible(!noteCollapsed);

        noteHeader.setOnMouseClicked(e -> {
            boolean nowCollapsed = !noteBody.isVisible();
            noteBody.setVisible(nowCollapsed);
            noteBody.setManaged(nowCollapsed);
            collapseIcon.setText(nowCollapsed ? "▸" : "▾");
            saveCollapseState(rqId, "noteBlock", !nowCollapsed);
            e.consume();
        });

        VBox noteBlock = new VBox(noteHeader, noteBody);
        noteBlock.getStyleClass().add("note-block");
        return noteBlock;
    }

    // ──────────────────────────────────────────────────────────────
    // 區段卡片（CollapsibleCard 的業務版本）
    // ──────────────────────────────────────────────────────────────

    private VBox buildSectionCard(String icon, String title, String sub,
                                  List<TaskDef> mainTasks, RQData rq,
                                  boolean showStep, boolean collapsed,
                                  String collapseKey,
                                  List<TaskDef> extraTasks,
                                  String projectNum) {
        // 進度徽章
        Map<String, Boolean> checks = rq.getChecks();
        long nonOptCount = mainTasks.stream().filter(t -> !t.isOptional()).count();
        long doneCount   = mainTasks.stream().filter(t -> !t.isOptional() && Boolean.TRUE.equals(checks.get(t.getKey()))).count();
        if (extraTasks != null) {
            nonOptCount += extraTasks.stream().filter(t -> !t.isOptional()).count();
            doneCount   += extraTasks.stream().filter(t -> !t.isOptional() && Boolean.TRUE.equals(checks.get(t.getKey()))).count();
        }

        boolean sectionDone = (nonOptCount > 0 && doneCount >= nonOptCount);
        double  sectionPct  = nonOptCount > 0 ? (double) doneCount / nonOptCount : 0.0;

        Label badgeLabel = new Label(doneCount + " / " + nonOptCount);
        badgeLabel.getStyleClass().addAll("section-progress", sectionDone ? "done" : "");

        Label iconLabel  = new Label(icon + " " + title);
        iconLabel.getStyleClass().add("section-title");
        Label colIcon = new Label(collapsed ? "▸" : "▾");
        colIcon.getStyleClass().add("section-collapse-icon");

        ProgressBar sectionBar = new ProgressBar(sectionPct);
        sectionBar.getStyleClass().add("rq-progress-bar");
        if (sectionDone) sectionBar.getStyleClass().add("done");
        HBox.setHgrow(sectionBar, Priority.ALWAYS);
        sectionBar.setMaxWidth(Double.MAX_VALUE);
        sectionBar.setMouseTransparent(true);

        HBox header = new HBox(10, iconLabel, sectionBar, badgeLabel, colIcon);
        header.getStyleClass().add("section-header");
        header.setPadding(new Insets(10, 14, 10, 14));

        // 內容：任務清單
        VBox body = new VBox(2);
        body.getStyleClass().add("section-body");
        body.setPadding(new Insets(8, 14, 12, 14));

        if (extraTasks == null) {
            // 單欄清單
            mainTasks.forEach(t -> body.getChildren().add(buildCheckRow(rq, t, showStep)));
        } else {
            // 雙欄：左欄 finTasks，右欄 SVN tasks
            VBox leftCol = new VBox(2);
            Label leftTitle = new Label("交付給客戶");
            leftTitle.getStyleClass().add("sub-title");
            leftCol.getChildren().add(leftTitle);
            mainTasks.forEach(t -> leftCol.getChildren().add(buildCheckRow(rq, t, false)));

            VBox rightCol = new VBox(2);
            Label rightTitle = new Label("SVN 內部更新");
            rightTitle.getStyleClass().add("sub-title");
            rightCol.getChildren().add(rightTitle);
            if (projectNum != null) {
                Label pathInfo = new Label(
                    appConfig.getSvnRoot() + "\\10_增修維護階段\\變更需求單\\" +
                    projectNum + "_" + PathUtils.winSafeName(rq.getId()) + "\\003_維護服務紀錄單\\");
                pathInfo.getStyleClass().add("path-info");
                pathInfo.setWrapText(true);
                rightCol.getChildren().add(pathInfo);
            }
            extraTasks.forEach(t -> rightCol.getChildren().add(buildCheckRow(rq, t, false)));

            HBox twoCol = new HBox(16, leftCol, rightCol);
            HBox.setHgrow(leftCol, Priority.ALWAYS);
            HBox.setHgrow(rightCol, Priority.ALWAYS);
            body.getChildren().add(twoCol);
        }

        body.setManaged(!collapsed);
        body.setVisible(!collapsed);

        header.setOnMouseClicked(e -> {
            boolean nowCollapsed = !body.isVisible();
            body.setVisible(nowCollapsed);
            body.setManaged(nowCollapsed);
            colIcon.setText(nowCollapsed ? "▸" : "▾");
            saveCollapseState(rqId, collapseKey, !nowCollapsed);
            e.consume();
        });

        VBox card = new VBox(header, body);
        card.getStyleClass().add("section-card");
        return card;
    }

    // ──────────────────────────────────────────────────────────────
    // 版本卡片
    // ──────────────────────────────────────────────────────────────

    private VBox buildVersionCard(RQData rq, int vIdx, String dlRoot, String svnRoot) {
        RQVersion ver = rq.getVersions().get(vIdx);
        String vName = ver.getName();

        List<TaskDef> devTasks = TaskFactory.versionDevTasks(vIdx);
        List<TaskDef> delTasks = TaskFactory.versionDeliverables(vIdx, rq, vName, dlRoot);
        List<TaskDef> svnTasks = TaskFactory.versionSVNTasks(vIdx, vName, rq, svnRoot);

        ProgressCalc.Progress vProg = ProgressCalc.calcVersionProgress(rq, vIdx, dlRoot, svnRoot);
        int vPct = vProg.percent();

        String badge = vIdx < 26 ? String.valueOf((char)('A' + vIdx)) : String.valueOf(vIdx + 1);
        boolean vCollapsed = rq.getCollapseState().getOrDefault("vcard_" + vIdx, false);

        // ── 版本卡片 header ────────────────────────────────────────
        Label badgeLabel = new Label(badge);
        badgeLabel.getStyleClass().add("version-badge");

        Label nameLabel = new Label(vName);
        nameLabel.getStyleClass().add("version-name");

        ProgressBar vBar = new ProgressBar(vPct / 100.0);
        vBar.getStyleClass().add("version-prog-bar");
        if (vPct >= 100) vBar.getStyleClass().add("done");
        HBox.setHgrow(vBar, Priority.ALWAYS);
        vBar.setMaxWidth(Double.MAX_VALUE);
        vBar.setMouseTransparent(true);

        Label vProgText = new Label(vProg.done() + "/" + vProg.total() + "  " + vPct + "%");
        vProgText.getStyleClass().addAll("version-prog-text", vPct >= 100 ? "done" : "");

        Map<String, String> vFiles = rq.getVersionFiles();
        boolean hasFiles = vFiles != null && vFiles.containsKey(String.valueOf(vIdx))
            && !vFiles.get(String.valueOf(vIdx)).isBlank();
        final int vIdxFinal = vIdx;
        Button verFilesBtn = new Button("✎ 改動程式");
        verFilesBtn.getStyleClass().addAll("btn-ver-files", hasFiles ? "has-files" : "");
        verFilesBtn.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        verFilesBtn.setOnAction(e -> {
            e.consume();
            VerFilesDialog dlg = new VerFilesDialog(
                getScene().getWindow(), dataStore, rqId, vIdxFinal);
            dlg.setOnSaved(() -> {
                if (onRefresh != null) onRefresh.accept(rqId);
            });
            dlg.show();
            if (onShowGlass != null) onShowGlass.accept(dlg.getStage());
        });

        Label colIcon = new Label(vCollapsed ? "▸" : "▾");
        colIcon.getStyleClass().add("collapse-icon");

        HBox vHeader = new HBox(8, badgeLabel, nameLabel, vBar, vProgText, verFilesBtn, colIcon);
        vHeader.getStyleClass().add("version-header");
        vHeader.setAlignment(Pos.CENTER_LEFT);
        vHeader.setPadding(new Insets(10, 14, 10, 14));

        // ── 版本內容（三欄） ─────────────────────────────────────
        VBox devSection  = buildSubSection("開發流程", devTasks, rq, true, null);
        VBox svnSection  = buildSubSection("SVN 內部更新", svnTasks, rq, false,
            appConfig.getSvnRoot() + "\\10_增修維護階段\\變更需求單\\" +
            (rq.getProjectNum() != null ? rq.getProjectNum() : "{專案}") + "_" +
            PathUtils.winSafeName(rq.getId()) + "\\");
        VBox delSection  = buildSubSection("交付給客戶", delTasks, rq, false,
            appConfig.getDownloadsRoot() + "\\" + PathUtils.winSafeName(rq.getId()) + "\\");

        // 各欄最小寬度，防止視窗縮小時被過度壓縮
        devSection.setMinWidth(200);
        svnSection.setMinWidth(220);
        delSection.setMinWidth(220);

        SplitPane vBody = new SplitPane(devSection, svnSection, delSection);
        vBody.getStyleClass().add("version-body");
        vBody.setManaged(!vCollapsed);
        vBody.setVisible(!vCollapsed);

        double[] savedPos = appConfig.getSplitPositions();
        vBody.setDividerPositions(savedPos[0], savedPos[1]);

        Runnable onDividerChanged = () -> {
            double[] cur = vBody.getDividerPositions();
            appConfig.setSplitPositions(new double[] { cur[0], cur[1] });
            if (splitResizeDebounce == null) {
                splitResizeDebounce = new PauseTransition(Duration.millis(500));
                splitResizeDebounce.setOnFinished(e -> appConfig.save());
            }
            splitResizeDebounce.playFromStart();
        };
        vBody.getDividers().get(0).positionProperty().addListener((obs, ov, nv) -> onDividerChanged.run());
        vBody.getDividers().get(1).positionProperty().addListener((obs, ov, nv) -> onDividerChanged.run());

        vHeader.setOnMouseClicked(e -> {
            boolean nowCollapsed = !vBody.isVisible();
            vBody.setVisible(nowCollapsed);
            vBody.setManaged(nowCollapsed);
            colIcon.setText(nowCollapsed ? "▸" : "▾");
            saveCollapseState(rqId, "vcard_" + vIdx, !nowCollapsed);
            e.consume();
        });

        VBox card = new VBox(vHeader, vBody);
        card.getStyleClass().add("version-card");
        return card;
    }

    private VBox buildSubSection(String title, List<TaskDef> tasks, RQData rq,
                                 boolean showStep, String pathHint) {
        VBox section = new VBox(4);
        section.getStyleClass().add("sub-section");
        section.setPadding(new Insets(12, 12, 12, 12));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("sub-title");
        section.getChildren().add(titleLabel);

        if (pathHint != null) {
            Label pathLabel = new Label(pathHint);
            pathLabel.getStyleClass().add("path-info");
            pathLabel.setWrapText(true);
            section.getChildren().add(pathLabel);
        }

        tasks.forEach(t -> section.getChildren().add(buildCheckRow(rq, t, showStep)));
        return section;
    }

    // ──────────────────────────────────────────────────────────────
    // CheckItemRow 工廠
    // ──────────────────────────────────────────────────────────────

    private CheckItemRow buildCheckRow(RQData rq, TaskDef t, boolean showStep) {
        boolean checked = Boolean.TRUE.equals(rq.getChecks().get(t.getKey()));
        String  ts      = rq.getTimestamps().get(t.getKey());

        CheckItemRow row = new CheckItemRow(rqId, t, checked, showStep, ts,
            this::onCheckboxClick, this::onRowClick);

        // 套用磁碟掃描狀態圓點
        TaskResult sr = scanResults.get(t.getKey());
        if (sr != null) {
            String cssState = switch (sr.state()) {
                case FILE    -> "fs-file";
                case PARTIAL -> "fs-partial";
                case FOLDER  -> "fs-folder";
                case NONE    -> "fs-none";
                case STALE   -> "fs-stale";
                case SCANNING-> "fs-scan";
                default      -> "fs-unknown";
            };
            row.setScanState(cssState);
        }
        return row;
    }

    private void onCheckboxClick(String rqId, String key) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;
        if (Boolean.TRUE.equals(rq.getChecks().get(key))) {
            // 已勾 → 確認取消
            if (onToast != null) onToast.accept("請在方格上按確認以取消勾選");
            // 使用 ConfirmDialog
            javafx.application.Platform.runLater(() ->
                com.rqtracker.ui.component.ConfirmDialog.show(
                    getScene().getWindow(), "↩", "取消勾選", "確定要取消勾選此項目？",
                    ConfirmDialog.Type.WARNING, () -> {
                        RQData r = dataStore.getRQ(rqId);
                        if (r == null) return;
                        r.getChecks().put(key, false);
                        r.getTimestamps().remove(key);
                        updateSectionTimestamps(r);
                        dataStore.saveRQ(rqId, r);
                        if (onRefresh != null) onRefresh.accept(rqId);
                        if (onTabRefresh != null) onTabRefresh.run();
                    }, onShowGlass));
        } else {
            doCheck(rqId, key);
        }
    }

    private void onRowClick(String rqId, String key) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;
        if (Boolean.TRUE.equals(rq.getChecks().get(key))) return; // 已勾，不動
        doCheck(rqId, key);
    }

    private void doCheck(String rqId, String key) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;
        String ts = DateTimeUtils.nowZhTW();
        rq.getChecks().put(key, true);
        rq.getTimestamps().put(key, ts);
        TaskCascadeService.applyDeliveryToDevelopment(rq, ts);
        updateSectionTimestamps(rq);
        dataStore.saveRQ(rqId, rq);
        if (onRefresh != null) onRefresh.accept(rqId);
        if (onTabRefresh != null) onTabRefresh.run();
    }

    // ──────────────────────────────────────────────────────────────
    // 歸檔橫幅
    // ──────────────────────────────────────────────────────────────

    private HBox buildArchiveBanner(String rqId) {
        Label text = new Label("★ 此需求已全部完成！");
        text.getStyleClass().add("archive-banner-text");

        Button archBtn = new Button("▣ 歸檔");
        archBtn.getStyleClass().add("btn-archive");
        archBtn.setOnAction(e -> {
            if (onArchive != null) onArchive.accept(rqId);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox banner = new HBox(12, text, spacer, archBtn);
        banner.getStyleClass().add("archive-banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 16, 10, 16));
        return banner;
    }

    // ──────────────────────────────────────────────────────────────
    // Section timestamp 更新
    // ──────────────────────────────────────────────────────────────

    private void updateSectionTimestamps(RQData rq) {
        String dlRoot  = appConfig.getDownloadsRoot();
        String svnRoot = appConfig.getSvnRoot();

        SectionTimestamp.update(rq,
            i -> TaskFactory.versionDevTasks(i),
            i -> TaskFactory.versionSVNTasks(i, rq.getVersions().get(i).getName(), rq, svnRoot),
            i -> TaskFactory.versionDeliverables(i, rq, rq.getVersions().get(i).getName(), dlRoot),
            TaskFactory.finalDeliveryTasks(rq, dlRoot),
            TaskFactory.sharedSVNTasks(rq, svnRoot)
        );
    }

    // ──────────────────────────────────────────────────────────────
    // 折疊狀態持久化
    // ──────────────────────────────────────────────────────────────

    private void saveCollapseState(String rqId, String key, boolean isCollapsed) {
        RQData rq = dataStore.getRQ(rqId);
        if (rq == null) return;
        rq.getCollapseState().put(key, isCollapsed);
        dataStore.saveRQ(rqId, rq);
    }
}
