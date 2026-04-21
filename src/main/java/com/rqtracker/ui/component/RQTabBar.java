package com.rqtracker.ui.component;

import com.rqtracker.service.ProgressCalc;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 可水平滾動的 RQ 標籤列（對應 HTML 的 rq-tabs-wrap）。
 */
public class RQTabBar extends StackPane {

    public record TabEntry(String rqId, int percent) {}

    private final HBox tabsBox;
    private final ScrollPane scroll;
    private String activeId;

    /** 選取標籤時的回呼：(rqId) */
    private Consumer<String> onSelect;
    /** 刪除標籤時的回呼：(rqId) */
    private Consumer<String> onDelete;

    public RQTabBar() {
        getStyleClass().add("rq-tabs-wrap");

        tabsBox = new HBox(4);
        tabsBox.getStyleClass().add("rq-tabs");
        tabsBox.setAlignment(Pos.CENTER_LEFT);

        scroll = new ScrollPane(tabsBox);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("rq-tabs-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        Button leftBtn  = scrollBtn("‹", -200);
        Button rightBtn = scrollBtn("›", 200);

        HBox row = new HBox(leftBtn, scroll, rightBtn);
        row.setAlignment(Pos.CENTER);
        getChildren().add(row);
    }

    /** 重新渲染所有標籤。 */
    public void setTabs(List<TabEntry> entries, String activeId) {
        this.activeId = activeId;
        tabsBox.getChildren().clear();

        for (TabEntry e : entries) {
            tabsBox.getChildren().add(buildTab(e));
        }
    }

    public void setOnSelect(Consumer<String> handler) { this.onSelect = handler; }
    public void setOnDelete(Consumer<String> handler) { this.onDelete = handler; }

    // ── 私有 ──────────────────────────────────────────────────────

    private HBox buildTab(TabEntry entry) {
        Label nameLabel = new Label(entry.rqId());
        nameLabel.getStyleClass().add("tab-label");
        nameLabel.setMaxWidth(180);

        Label pctLabel = new Label(entry.percent() + "%");
        pctLabel.getStyleClass().add("tab-progress");

        Button delBtn = new Button("✕");
        delBtn.getStyleClass().add("tab-del");
        delBtn.setOnAction(ev -> {
            ev.consume();
            if (onDelete != null) onDelete.accept(entry.rqId());
        });

        HBox tab = new HBox(4, nameLabel, pctLabel, delBtn);
        tab.getStyleClass().add("rq-tab");
        if (entry.rqId().equals(activeId)) tab.getStyleClass().add("active");
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setOnMouseClicked(ev -> {
            if (onSelect != null) onSelect.accept(entry.rqId());
        });

        return tab;
    }

    private Button scrollBtn(String text, double delta) {
        Button btn = new Button(text);
        btn.getStyleClass().add("rq-tab-scroll-btn");
        btn.setOnAction(e -> scroll.setHvalue(
            Math.max(0, Math.min(1, scroll.getHvalue() + delta / scroll.getContent().getBoundsInLocal().getWidth()))
        ));
        return btn;
    }
}
