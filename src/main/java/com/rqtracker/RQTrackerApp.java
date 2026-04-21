package com.rqtracker;

import com.rqtracker.service.AppConfig;
import com.rqtracker.service.DataStore;
import com.rqtracker.ui.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * RQ Tracker 應用程式入口。
 * 負責初始化服務層，建立主視窗，並在結束時儲存設定。
 */
public class RQTrackerApp extends Application {

    private static final Logger LOG = Logger.getLogger(RQTrackerApp.class.getName());

    private DataStore dataStore;

    @Override
    public void start(Stage primaryStage) {
        Path appDataDir = resolveAppDataDir();
        LOG.info("資料目錄：" + appDataDir);

        AppConfig config = AppConfig.load(appDataDir);
        dataStore = new DataStore(appDataDir);

        MainController mainController = new MainController(dataStore, config);
        Scene scene = mainController.buildScene(primaryStage);

        primaryStage.setTitle("RQ 進度追蹤");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(640);

        // 還原視窗狀態（首次啟動預設最大化）
        if (config.isWindowMaximized()) {
            primaryStage.setMaximized(true);
        } else {
            double w = config.getWindowWidth() > 0 ? config.getWindowWidth() : 1280;
            double h = config.getWindowHeight() > 0 ? config.getWindowHeight() : 800;
            primaryStage.setWidth(w);
            primaryStage.setHeight(h);
        }

        primaryStage.setOnCloseRequest(e -> {
            // 儲存視窗狀態
            config.setWindowMaximized(primaryStage.isMaximized());
            if (!primaryStage.isMaximized()) {
                config.setWindowWidth((int) primaryStage.getWidth());
                config.setWindowHeight((int) primaryStage.getHeight());
            }
            mainController.shutdown();
            config.save();
        });

        primaryStage.show();
        mainController.onAppStarted();
    }

    private static Path resolveAppDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, "RQTracker");
        }
        return Paths.get(System.getProperty("user.home"), ".rqtracker");
    }

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
            "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        launch(args);
    }
}
