package com.rqtracker;

import com.rqtracker.model.VersionPreset;
import com.rqtracker.service.AppConfig;
import com.rqtracker.ui.dialog.SettingsDialog;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SettingsDialogVersionEditingTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit 啟動逾時");
        Platform.setImplicitExit(false);
    }

    @Test
    void switchingToAnotherCellCommitsCurrentVersionInput() throws Exception {
        runOnFxThread(() -> {
            SettingsDialog dialog = new SettingsDialog(null, new AppConfig());
            Stage stage = dialog.getStage();
            stage.setOpacity(0);
            stage.show();

            try {

            TableView<VersionPreset> table = versionPresetTable(dialog);
            VersionPreset preset = new VersionPreset("", "", "");
            table.getItems().add(preset);
            int row = table.getItems().size() - 1;

            @SuppressWarnings("unchecked")
            TableColumn<VersionPreset, String> vidColumn =
                (TableColumn<VersionPreset, String>) table.getColumns().get(0);
            @SuppressWarnings("unchecked")
            TableColumn<VersionPreset, String> nameColumn =
                (TableColumn<VersionPreset, String>) table.getColumns().get(1);
            @SuppressWarnings("unchecked")
            TableColumn<VersionPreset, String> sbomColumn =
                (TableColumn<VersionPreset, String>) table.getColumns().get(2);

            table.scrollTo(row);
            TableCell<VersionPreset, String> editingCell = startEditing(table, row, vidColumn);
            assertInstanceOf(TextField.class, editingCell.getGraphic());
            TextField editor = (TextField) editingCell.getGraphic();
            editor.requestFocus();
            editor.setText("customVid");

            TableCell<VersionPreset, String> nameCell = startEditing(table, row, nameColumn);

            assertEquals("customVid", preset.getVid(),
                "切換到其他欄位時，原欄位輸入必須寫回 VersionPreset");

            ((TextField) nameCell.getGraphic()).setText("自訂版本");
            TableCell<VersionPreset, String> sbomCell = startEditing(table, row, sbomColumn);

            ((TextField) sbomCell.getGraphic()).setText("9_自訂版本");
            startEditing(table, row, vidColumn);

            assertAll(
                () -> assertEquals("customVid", preset.getVid()),
                () -> assertEquals("自訂版本", preset.getDisplayName()),
                () -> assertEquals("9_自訂版本", preset.getSbomFolder())
            );
            } finally {
                stage.close();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<VersionPreset> versionPresetTable(SettingsDialog dialog) throws Exception {
        Field field = SettingsDialog.class.getDeclaredField("versionPresetTable");
        field.setAccessible(true);
        return (TableView<VersionPreset>) field.get(dialog);
    }

    @SuppressWarnings("unchecked")
    private static TableCell<VersionPreset, String> findCell(
            TableView<VersionPreset> table,
            int row,
            TableColumn<VersionPreset, String> column) {
        for (Node node : table.lookupAll(".table-cell")) {
            if (node instanceof TableCell<?, ?> cell
                    && cell.getIndex() == row
                    && cell.getTableColumn() == column) {
                return (TableCell<VersionPreset, String>) cell;
            }
        }
        fail("找不到正在編輯的版本欄位");
        return null;
    }

    private static TableCell<VersionPreset, String> startEditing(
            TableView<VersionPreset> table,
            int row,
            TableColumn<VersionPreset, String> column) {
        table.getFocusModel().focus(row, column);
        table.edit(row, column);
        table.applyCss();
        table.layout();

        TableCell<VersionPreset, String> cell = findCell(table, row, column);
        if (!cell.isEditing()) {
            cell.startEdit();
            table.applyCss();
            table.layout();
        }
        assertInstanceOf(TextField.class, cell.getGraphic());
        return cell;
    }

    private static void runOnFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX 測試執行逾時");
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) throw exception;
            if (failure.get() instanceof Error error) throw error;
            throw new RuntimeException(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
