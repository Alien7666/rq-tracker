package com.rqtracker.ui.component;

import com.rqtracker.util.DialogHelper;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

public class ConfirmDialog {

    public enum Type { DANGER, WARNING, INFO }

    public static void show(Window owner, String icon, String title, String message,
                            Type type, Runnable onConfirm) {
        show(owner, icon, title, message, type, onConfirm, null);
    }

    public static void show(Window owner, String icon, String title, String message,
                            Type type, Runnable onConfirm, Consumer<Stage> onStageReady) {
        Stage dialog = new Stage();
        DialogHelper.initTransparent(dialog);
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("confirm-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("confirm-title");

        Label msgLabel = new Label(message);
        msgLabel.getStyleClass().add("confirm-message");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(320);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = new Button("確認");
        confirmBtn.getStyleClass().addAll("btn-primary",
            type == Type.DANGER ? "btn-danger" : type == Type.WARNING ? "btn-warn" : "");
        confirmBtn.setDefaultButton(true);
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
        DialogHelper.applyTheme(scene, ConfirmDialog.class);
        dialog.setScene(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close(); e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                dialog.close(); onConfirm.run(); e.consume();
            }
        });

        if (onStageReady != null) onStageReady.accept(dialog);
        dialog.show();
        Platform.runLater(confirmBtn::requestFocus);
    }

    public static void confirmDelete(Window owner, String targetName, Runnable onConfirm) {
        confirmDelete(owner, targetName, onConfirm, null);
    }

    public static void confirmDelete(Window owner, String targetName, Runnable onConfirm,
                                     Consumer<Stage> onStageReady) {
        show(owner, "✕", "刪除確認",
            "確定要刪除「" + targetName + "」嗎？\n此操作無法復原。",
            Type.DANGER, onConfirm, onStageReady);
    }

    public static void confirmArchive(Window owner, String targetName, Runnable onConfirm) {
        confirmArchive(owner, targetName, onConfirm, null);
    }

    public static void confirmArchive(Window owner, String targetName, Runnable onConfirm,
                                      Consumer<Stage> onStageReady) {
        show(owner, "▣", "歸檔確認",
            "確定要歸檔「" + targetName + "」嗎？\n歸檔後可在歷史紀錄中查看。",
            Type.WARNING, onConfirm, onStageReady);
    }
}
