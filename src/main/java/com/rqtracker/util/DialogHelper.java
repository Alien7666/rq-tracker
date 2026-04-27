package com.rqtracker.util;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 無邊框透明視窗工具：消除雙叉叉與圓角白邊。
 * 所有自訂 Dialog 在 new Stage() 後、initOwner/initModality 前，
 * 須呼叫 initTransparent()；場景建立後呼叫 applyTheme()。
 */
public final class DialogHelper {

    private static final int RESIZE_MARGIN = 8;

    private DialogHelper() {}

    /**
     * 設定 Stage 為透明無邊框，必須在 initOwner / initModality 前呼叫。
     */
    public static void initTransparent(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
    }

    /**
     * 套用 CSS 主題並將 Scene 背景設為透明，消除圓角白邊。
     */
    public static void applyTheme(Scene scene, Class<?> caller) {
        scene.setFill(Color.TRANSPARENT);
        try {
            var css = caller.getResource("/css/rq-theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}
    }

    /**
     * 讓 header Region 可拖曳移動 Stage（取代原生標題列的移動功能）。
     */
    public static void makeMovable(Stage stage, Region header) {
        final double[] off = {0, 0};
        header.setOnMousePressed(e -> {
            off[0] = e.getSceneX();
            off[1] = e.getSceneY();
        });
        header.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - off[0]);
            stage.setY(e.getScreenY() - off[1]);
        });
    }

    /**
     * 讓 root Region 四個邊緣可拖曳調整 Stage 大小（8 方向）。
     * 傳入的 root 通常是 Scene 的根節點（modal BorderPane / VBox）。
     */
    public static void makeResizable(Stage stage, Region root) {
        final double[] sX = {0}, sY = {0}, sW = {0}, sH = {0}, stX = {0}, stY = {0};
        final String[] dir = {""};

        root.setOnMouseMoved(e -> {
            double x = e.getX(), y = e.getY(), w = root.getWidth(), h = root.getHeight();
            boolean left  = x < RESIZE_MARGIN;
            boolean right = x > w - RESIZE_MARGIN;
            boolean top   = y < RESIZE_MARGIN;
            boolean bot   = y > h - RESIZE_MARGIN;
            String d = (top ? "N" : bot ? "S" : "") + (left ? "W" : right ? "E" : "");
            dir[0] = d;
            root.setCursor(d.isEmpty() ? Cursor.DEFAULT : switch (d) {
                case "N", "S"   -> Cursor.V_RESIZE;
                case "E", "W"   -> Cursor.H_RESIZE;
                case "NW", "SE" -> Cursor.NW_RESIZE;
                case "NE", "SW" -> Cursor.NE_RESIZE;
                default -> Cursor.DEFAULT;
            });
        });

        root.setOnMouseExited(e -> root.setCursor(Cursor.DEFAULT));

        root.setOnMousePressed(e -> {
            if (dir[0].isEmpty()) return;
            sX[0]  = e.getScreenX(); sY[0]  = e.getScreenY();
            sW[0]  = stage.getWidth();  sH[0]  = stage.getHeight();
            stX[0] = stage.getX();      stY[0] = stage.getY();
        });

        root.setOnMouseDragged(e -> {
            if (dir[0].isEmpty()) return;
            double dx  = e.getScreenX() - sX[0];
            double dy  = e.getScreenY() - sY[0];
            double nW  = sW[0], nH = sH[0], nX = stX[0], nY = stY[0];
            double minW = stage.getMinWidth()  > 0 ? stage.getMinWidth()  : 300;
            double minH = stage.getMinHeight() > 0 ? stage.getMinHeight() : 200;
            if (dir[0].contains("E")) nW = Math.max(minW, sW[0] + dx);
            if (dir[0].contains("S")) nH = Math.max(minH, sH[0] + dy);
            if (dir[0].contains("W")) { nW = Math.max(minW, sW[0] - dx); nX = stX[0] + sW[0] - nW; }
            if (dir[0].contains("N")) { nH = Math.max(minH, sH[0] - dy); nY = stY[0] + sH[0] - nH; }
            stage.setX(nX); stage.setY(nY);
            stage.setWidth(nW); stage.setHeight(nH);
        });
    }
}
