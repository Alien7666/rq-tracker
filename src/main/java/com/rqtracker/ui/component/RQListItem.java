package com.rqtracker.ui.component;

import com.rqtracker.ui.component.RQTabBar.TabEntry;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 側邊欄 RQ 列表中的單一項目。
 * 顯示 RQ ID（可換行）、百分比、迷你進度條、刪除按鈕。
 */
public class RQListItem extends HBox {

    private final Button delBtn;

    public RQListItem(TabEntry entry, boolean active,
                      Consumer<String> onSelect, Consumer<String> onDelete) {
        getStyleClass().add("rq-list-item");
        if (active) getStyleClass().add("active");

        // RQ ID 標籤（多行）
        Label idLabel = new Label(entry.rqId());
        idLabel.getStyleClass().add("rq-item-id");
        idLabel.setWrapText(true);
        idLabel.setMaxWidth(Double.MAX_VALUE);

        // 百分比
        Label pctLabel = new Label(entry.percent() + "%");
        pctLabel.getStyleClass().add("rq-item-pct");

        // ID + 百分比 行
        HBox topRow = new HBox(4, idLabel, pctLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(idLabel, Priority.ALWAYS);
        idLabel.setMaxWidth(Double.MAX_VALUE);

        // 迷你進度條（3px 高）
        ProgressBar bar = new ProgressBar(entry.percent() / 100.0);
        bar.getStyleClass().add("rq-item-bar");
        if (entry.percent() >= 100) bar.getStyleClass().add("done");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(3);

        // 左側資訊 VBox
        VBox infoBox = new VBox(4, topRow, bar);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        infoBox.setMaxWidth(Double.MAX_VALUE);

        // 刪除按鈕（平時透明，hover 才顯示）
        delBtn = new Button("✕");
        delBtn.getStyleClass().add("rq-item-del");
        delBtn.setOpacity(0.0);
        delBtn.setOnAction(ev -> {
            ev.consume();
            if (onDelete != null) onDelete.accept(entry.rqId());
        });

        getChildren().addAll(infoBox, delBtn);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(this, new Tooltip(entry.rqId() + "\n進度：" + entry.percent() + "%"));

        // hover 顯示/隱藏刪除按鈕
        setOnMouseEntered(e -> delBtn.setOpacity(0.7));
        setOnMouseExited(e -> delBtn.setOpacity(0.0));

        // 點擊整列（非刪除按鈕）觸發選取
        setOnMouseClicked(e -> {
            if (onSelect != null) onSelect.accept(entry.rqId());
        });
    }
}
