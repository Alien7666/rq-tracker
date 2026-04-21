package com.rqtracker.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * 通用確認對話框（對應 HTML 的 showConfirm()）。
 * WINDOW_MODAL，阻斷父視窗操作。
 */
public class ConfirmDialog {

    public enum Type { DANGER, WARNING, INFO }

    /**
     * 顯示確認對話框。
     *
     * @param owner     父視窗
     * @param icon      顯示圖示（例："🗑️"）
     * @param title     標題文字
     * @param message   說明文字
     * @param type      對話框類型（影響確認按鈕顏色）
     * @param onConfirm 使用者按確認後的回呼
     */
    public static void show(Window owner, String icon, String title, String message,
                            Type type, Runnable onConfirm) {
        Stage dialog = new Stage(StageStyle.UNDECORATED);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);

        // 圖示
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("confirm-icon");

        // 標題
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("confirm-title");

        // 說明
        Label msgLabel = new Label(message);
        msgLabel.getStyleClass().add("confirm-message");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(320);

        // 按鈕列
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = new Button("確認");
        confirmBtn.getStyleClass().addAll("btn-primary",
            type == Type.DANGER ? "btn-danger" : type == Type.WARNING ? "btn-warn" : "");
        confirmBtn.setOnAction(e -> {
            dialog.close();
            onConfirm.run();
        });

        HBox btnRow = new HBox(12, cancelBtn, confirmBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(12, iconLabel, titleLabel, msgLabel, btnRow);
        box.getStyleClass().add("confirm-box");
        box.setPadding(new Insets(24));
        box.setPrefWidth(360);

        Scene scene = new Scene(box);
        applyTheme(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** 快捷方法：刪除確認（紅色按鈕） */
    public static void confirmDelete(Window owner, String targetName, Runnable onConfirm) {
        show(owner, "✕", "刪除確認",
            "確定要刪除「" + targetName + "」嗎？\n此操作無法復原。",
            Type.DANGER, onConfirm);
    }

    /** 快捷方法：歸檔確認（警告色） */
    public static void confirmArchive(Window owner, String targetName, Runnable onConfirm) {
        show(owner, "▣", "歸檔確認",
            "確定要歸檔「" + targetName + "」嗎？\n歸檔後可在歷史紀錄中查看。",
            Type.WARNING, onConfirm);
    }

    private static void applyTheme(Scene scene) {
        try {
            var css = ConfirmDialog.class.getResource("/css/rq-theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}
    }
}
