package com.rqtracker.ui.dialog;

import com.rqtracker.model.RQData;
import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DataStore;
import com.rqtracker.service.ProgressCalc;
import com.rqtracker.ui.component.ConfirmDialog;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.DialogHelper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * 歷史紀錄對話框（對應 HTML 的 showHistoryModal）。
 * 列出所有已歸檔的 RQ，提供復原或永久刪除操作。
 */
public class HistoryDialog {

    private final Stage    dialog;
    private final DataStore dataStore;
    private final AppConfig appConfig;

    /** 復原回呼：傳入被復原的 rqId */
    private final Consumer<String> onRestore;

    /** 列表容器，復原/刪除後動態重建 */
    private VBox listContainer;

    public HistoryDialog(Window owner, DataStore dataStore, AppConfig appConfig,
                         Consumer<String> onRestore) {
        this.dataStore = dataStore;
        this.appConfig = appConfig;
        this.onRestore = onRestore;

        dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("歷史紀錄（已歸檔）");
        dialog.setMinWidth(580);
        dialog.setMinHeight(300);

        // ── Modal 標題 ────────────────────────────────────────────────
        Label titleLabel = new Label("📜 歷史紀錄");
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

        // ── 列表容器 ─────────────────────────────────────────────────
        listContainer = new VBox(8);
        listContainer.setPadding(new Insets(16, 24, 16, 24));
        buildList();

        ScrollPane scroll = new ScrollPane(listContainer);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox modal = new VBox(modalHeader, new Separator(), scroll);
        modal.getStyleClass().add("modal");
        modal.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        modal.setPrefWidth(600);
        modal.setPrefHeight(480);

        Scene scene = new Scene(modal);
        DialogHelper.applyTheme(scene, getClass());
        dialog.setScene(scene);
        DialogHelper.makeMovable(dialog, modalHeader);
        DialogHelper.makeResizable(dialog, modal);
    }

    public void show() { dialog.show(); }
    public Stage getStage() { return dialog; }

    // ──────────────────────────────────────────────────────────────

    private void buildList() {
        listContainer.getChildren().clear();
        List<RQData> history = dataStore.getHistory();

        if (history.isEmpty()) {
            Label empty = new Label("尚無已歸檔的 RQ。");
            empty.getStyleClass().add("empty-state-desc");
            empty.setPadding(new Insets(20, 0, 20, 0));
            listContainer.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < history.size(); i++) {
            listContainer.getChildren().add(buildHistoryRow(history.get(i), i));
        }
    }

    private HBox buildHistoryRow(RQData rq, int idx) {
        // 進度計算
        int pct = ProgressCalc.calcProgress(rq,
            appConfig.getDownloadsRoot(), appConfig.getSvnRoot()).percent();

        Label idLabel = new Label(rq.getId());
        idLabel.getStyleClass().add("history-item-id");
        idLabel.setWrapText(true);

        String archivedStr = rq.getArchivedAt() != null
            ? "歸檔於 " + DateTimeUtils.isoToZhTW(rq.getArchivedAt())
            : "";
        Label metaLabel = new Label(archivedStr + "　完成度 " + pct + "%");
        metaLabel.getStyleClass().add("history-item-meta");

        VBox info = new VBox(2, idLabel, metaLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button restoreBtn = new Button("↩ 復原");
        restoreBtn.getStyleClass().add("btn-restore");
        restoreBtn.setOnAction(e -> handleRestore(rq.getId(), idx));

        Button deleteBtn = new Button("🗑 永久刪除");
        deleteBtn.getStyleClass().add("btn-del-arc");
        deleteBtn.setOnAction(e -> handleDeleteHistory(rq.getId(), idx));

        HBox row = new HBox(12, info, restoreBtn, deleteBtn);
        row.getStyleClass().add("history-item");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void handleRestore(String rqId, int idx) {
        dataStore.restoreFromHistory(idx);
        if (onRestore != null) onRestore.accept(rqId);
        buildList();           // 重建列表（index 已改變）
    }

    private void handleDeleteHistory(String rqId, int idx) {
        ConfirmDialog.show(dialog, "🗑", "永久刪除歷史紀錄",
            "確定要永久刪除「" + rqId + "」？此操作無法復原。",
            ConfirmDialog.Type.DANGER, () -> {
                dataStore.deleteHistory(idx);
                buildList();
            });
    }

}
