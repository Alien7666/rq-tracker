package com.rqtracker.ui.component;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * 淡入淡出 Toast 通知（對應 HTML 的 showSaveToast()）。
 * 用法：Toast 放在場景最外層 StackPane 底部，呼叫 show(message) 即可。
 */
public class ToastNotification extends StackPane {

    private final Label label;
    private FadeTransition currentAnim;

    public ToastNotification() {
        label = new Label();
        label.getStyleClass().add("toast");
        label.setMouseTransparent(true);
        getChildren().add(label);
        setAlignment(Pos.BOTTOM_CENTER);
        setPickOnBounds(false);
        setMouseTransparent(true);
        setOpacity(0);
    }

    public void show(String message) {
        label.setText(message);
        if (currentAnim != null) currentAnim.stop();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), this);
        fadeIn.setToValue(1.0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), this);
        fadeOut.setDelay(Duration.millis(1800));
        fadeOut.setToValue(0.0);

        fadeIn.setOnFinished(e -> fadeOut.play());
        currentAnim = fadeOut;
        setOpacity(0);
        fadeIn.play();
    }
}
