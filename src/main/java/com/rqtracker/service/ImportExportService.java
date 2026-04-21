package com.rqtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rqtracker.model.AppData;
import com.rqtracker.util.DateTimeUtils;
import com.rqtracker.util.PathUtils;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * JSON 匯入 / 匯出（對應 HTML 的 exportData / importData）。
 * 使用 JavaFX FileChooser 讓使用者選擇儲存位置或來源檔案。
 */
public final class ImportExportService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private ImportExportService() {}

    // ──────────────────────────────────────────────────────────────
    // 匯出
    // ──────────────────────────────────────────────────────────────

    /**
     * 將全部資料匯出為 JSON 檔案。
     *
     * @param owner   父視窗（用於 FileChooser）
     * @param dataStore 資料來源
     * @param onToast 操作結果回呼（Toast 訊息）
     */
    public static void exportToFile(Window owner, DataStore dataStore, Consumer<String> onToast) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("匯出 JSON");
        chooser.setInitialFileName("RQ_backup_" + PathUtils.todayDate() + ".json");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON 檔案", "*.json"));

        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        AppData snapshot = dataStore.exportAll();
        snapshot.setSavedAt(DateTimeUtils.nowIso());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            MAPPER.writeValue(writer, snapshot);
            onToast.accept("✓ 已匯出至 " + file.getName());
        } catch (IOException e) {
            onToast.accept("✗ 匯出失敗：" + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 匯入
    // ──────────────────────────────────────────────────────────────

    /**
     * 從 JSON 檔案匯入資料（覆蓋現有資料）。
     *
     * @param owner      父視窗
     * @param dataStore  資料目標
     * @param onToast    操作結果回呼
     * @param onImported 匯入成功後的 UI 刷新回呼
     */
    public static void importFromFile(Window owner, DataStore dataStore,
                                      Consumer<String> onToast, Runnable onImported) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("匯入 JSON（將覆蓋現有資料）");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON 檔案", "*.json"));

        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        AppData data;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            data = MAPPER.readValue(reader, AppData.class);
        } catch (IOException e) {
            onToast.accept("✗ 讀取失敗，檔案可能已損毀：" + e.getMessage());
            return;
        }

        if (data.getIndex() == null) {
            onToast.accept("✗ 格式錯誤，請選擇正確的備份檔案");
            return;
        }

        // 確認覆蓋
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION,
            "確定要匯入「" + file.getName() + "」？\n這將覆蓋目前所有進度，此動作無法復原。",
            javafx.scene.control.ButtonType.YES,
            javafx.scene.control.ButtonType.NO
        );
        confirm.setHeaderText(null);
        confirm.setTitle("確認匯入");
        confirm.initOwner(owner);
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.YES) return;

        dataStore.importAll(data);
        onToast.accept("✓ 匯入成功（" + data.getIndex().size() + " 筆 RQ）");
        if (onImported != null) onImported.run();
    }
}
