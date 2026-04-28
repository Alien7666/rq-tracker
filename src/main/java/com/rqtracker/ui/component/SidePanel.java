package com.rqtracker.ui.component;

import com.rqtracker.ui.component.RQTabBar.TabEntry;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * 左側邊欄：取代水平 RQTabBar。
 * 包含 App 商標、新增按鈕、工具選單、RQ 列表、狀態列。
 */
public class SidePanel extends VBox {

    private final VBox  rqList;
    private final Label backupLabel;
    private final Label scanLabel;
    private final Label updateBadge;

    private Consumer<String> onSelect;
    private Consumer<String> onDelete;

    // ── ContextMenu items（由 MainController 設定） ──────────────────────────
    public final MenuItem menuSettings    = new MenuItem("⚙  路徑設定");
    public final MenuItem menuCheckUpdate = new MenuItem("⬆  檢查更新");
    public final MenuItem menuHistory     = new MenuItem("◷  已歸檔的 RQ");
    public final MenuItem menuImport      = new MenuItem("▼  匯入 RQ 資料");
    public final MenuItem menuExport      = new MenuItem("▲  匯出 RQ 資料");
    public final MenuItem menuCreateDirs  = new MenuItem("▣  建立 RQ 資料夾");
    public final MenuItem menuFileList    = new MenuItem("≡  生成程式碼清單");

    public SidePanel(Runnable onNewRQ) {
        getStyleClass().add("side-panel");
        setMaxHeight(Double.MAX_VALUE);

        // ── 商標區 ──────────────────────────────────────────────────────────
        Label iconLabel  = new Label("≡");
        iconLabel.getStyleClass().add("brand-icon");
        Label titleLabel = new Label("RQ ");
        titleLabel.getStyleClass().add("brand-title");
        Label subLabel   = new Label("Tracker");
        subLabel.getStyleClass().add("brand-sub");

        HBox brand = new HBox(0, iconLabel, titleLabel, subLabel);
        brand.getStyleClass().add("side-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMaxWidth(Double.MAX_VALUE);

        // ── 工具列（新增按鈕 + 齒輪） ────────────────────────────────────────
        Button newBtn = new Button("＋  新增 RQ");
        newBtn.getStyleClass().add("btn-side-new");
        newBtn.setTooltip(new Tooltip("新增一筆 RQ"));
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> { if (onNewRQ != null) onNewRQ.run(); });
        HBox.setHgrow(newBtn, Priority.ALWAYS);

        Button toolBtn = new Button("⚙");
        toolBtn.getStyleClass().add("btn-side-tool");
        toolBtn.setTooltip(new Tooltip("開啟工具選單"));

        ContextMenu contextMenu = new ContextMenu(
            menuSettings,
            new SeparatorMenuItem(),
            menuCheckUpdate,
            new SeparatorMenuItem(),
            menuHistory,
            new SeparatorMenuItem(),
            menuImport,
            menuExport,
            new SeparatorMenuItem(),
            menuCreateDirs,
            menuFileList
        );
        contextMenu.getStyleClass().add("dropdown-menu");
        toolBtn.setOnAction(e ->
            contextMenu.show(toolBtn,
                toolBtn.localToScreen(0, toolBtn.getHeight()).getX(),
                toolBtn.localToScreen(0, toolBtn.getHeight()).getY())
        );

        HBox toolbar = new HBox(6, newBtn, toolBtn);
        toolbar.getStyleClass().add("side-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setMaxWidth(Double.MAX_VALUE);

        // ── RQ 列表（可捲動） ─────────────────────────────────────────────────
        rqList = new VBox(0);
        rqList.getStyleClass().add("rq-list");
        rqList.setMaxWidth(Double.MAX_VALUE);

        ScrollPane listScroll = new ScrollPane(rqList);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScroll.setFitToWidth(true);
        listScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        // ── 狀態列（備份狀態 + 掃描指示）────────────────────────────────────
        backupLabel = new Label("備份：未設定");
        backupLabel.getStyleClass().addAll("backup-status");

        scanLabel = new Label("●");
        scanLabel.getStyleClass().add("scan-indicator");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(6, backupLabel, spacer, scanLabel);
        statusBar.getStyleClass().add("side-status");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setMaxWidth(Double.MAX_VALUE);

        updateBadge = new Label();
        updateBadge.getStyleClass().add("update-badge");
        updateBadge.setMaxWidth(Double.MAX_VALUE);
        updateBadge.setVisible(false);
        updateBadge.setManaged(false);

        getChildren().addAll(brand, toolbar, listScroll, updateBadge, statusBar);
    }

    // ── 公開 API ─────────────────────────────────────────────────────────────

    public void setOnSelect(Consumer<String> handler) { this.onSelect = handler; }
    public void setOnDelete(Consumer<String> handler) { this.onDelete = handler; }

    /** 重新渲染 RQ 列表。 */
    public void setRQList(List<TabEntry> entries, String activeId) {
        rqList.getChildren().clear();
        for (TabEntry e : entries) {
            rqList.getChildren().add(
                new RQListItem(e, e.rqId().equals(activeId), onSelect, onDelete)
            );
        }
    }

    /** 更新備份狀態文字。 */
    public void setBackupStatus(String text, boolean warn) {
        backupLabel.setText(text);
        backupLabel.getStyleClass().removeAll("ok", "warn");
        backupLabel.getStyleClass().add(warn ? "warn" : "ok");
    }

    /** 掃描進行中時顯示 accent 色；結束後恢復暗色。 */
    public void setScanIndicator(boolean scanning) {
        scanLabel.getStyleClass().removeAll("active");
        if (scanning) scanLabel.getStyleClass().add("active");
    }

    /** 顯示有新版本的提示徽章，並在選單項目加圓點。 */
    public void setUpdateAvailable(String version) {
        updateBadge.setText("⬆ 有新版本 v" + version + " 可更新，點此安裝");
        updateBadge.setVisible(true);
        updateBadge.setManaged(true);
        menuCheckUpdate.setText("⬆  檢查更新  ●");
    }

    /** 取得 updateBadge，讓 MainController 可以設定點擊事件。 */
    public Label getUpdateBadge() { return updateBadge; }
}
