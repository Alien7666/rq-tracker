package com.rqtracker.ui.component;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 可折疊 Section Card（對應 HTML 的 section-card / toggleSectionCard()）。
 * 點擊標題列即可展開/折疊內容區域。
 */
public class CollapsibleCard extends VBox {

    private final Label arrowLabel;
    private final VBox  contentBox;
    private boolean expanded;

    /**
     * @param title    區塊標題
     * @param content  內容節點
     * @param initExpanded 初始展開狀態
     */
    public CollapsibleCard(String title, Node content, boolean initExpanded) {
        getStyleClass().add("section-card");
        this.expanded = initExpanded;

        // ── 標題列 ──────────────────────────────────────
        arrowLabel = new Label(expanded ? "▲" : "▼");
        arrowLabel.getStyleClass().add("section-arrow");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleLabel, spacer, arrowLabel);
        header.getStyleClass().add("section-header");
        header.setPadding(new Insets(10, 14, 10, 14));
        header.setOnMouseClicked(e -> toggle());

        // ── 內容區域 ──────────────────────────────────────
        contentBox = new VBox(content);
        contentBox.getStyleClass().add("section-body");
        contentBox.setManaged(expanded);
        contentBox.setVisible(expanded);

        getChildren().addAll(header, contentBox);
    }

    /** 切換展開/折疊狀態。 */
    public void toggle() {
        setExpanded(!expanded);
    }

    public void setExpanded(boolean expand) {
        if (this.expanded == expand) return;
        this.expanded = expand;
        arrowLabel.setText(expand ? "▲" : "▼");

        if (expand) {
            contentBox.setManaged(true);
            contentBox.setVisible(true);
            contentBox.setOpacity(0);
            Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.millis(150), new KeyValue(contentBox.opacityProperty(), 1.0))
            );
            fadeIn.play();
        } else {
            Timeline fadeOut = new Timeline(
                new KeyFrame(Duration.millis(100), new KeyValue(contentBox.opacityProperty(), 0.0))
            );
            fadeOut.setOnFinished(e -> {
                contentBox.setManaged(false);
                contentBox.setVisible(false);
            });
            fadeOut.play();
        }
    }

    public boolean isExpanded() { return expanded; }
    public VBox getContentBox()  { return contentBox; }
}
