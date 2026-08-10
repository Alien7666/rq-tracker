package com.rqtracker.ui.component;

import javafx.event.Event;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DefaultStringConverter;

/**
 * 可編輯文字儲存格：按 Enter 或將焦點移到其他儲存格時都會提交目前文字。
 * JavaFX 內建 TextFieldTableCell 在失焦時可能只取消編輯，造成尚未提交的輸入消失。
 */
public final class CommitOnFocusLostTextFieldTableCell<S>
        extends TextFieldTableCell<S, String> {

    private TextField observedEditor;
    private boolean explicitCancel;

    public CommitOnFocusLostTextFieldTableCell() {
        super(new DefaultStringConverter());
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing() || !(getGraphic() instanceof TextField editor)) return;

        if (editor != observedEditor) {
            observedEditor = editor;
            editor.focusedProperty().addListener((observable, hadFocus, hasFocus) -> {
                if (!hasFocus && isEditing()) {
                    commitEdit(editor.getText());
                }
            });
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    explicitCancel = true;
                }
            });
        }
    }

    @Override
    public void cancelEdit() {
        if (!explicitCancel && isEditing() && observedEditor != null) {
            commitEdit(observedEditor.getText());
            return;
        }
        explicitCancel = false;
        super.cancelEdit();
    }

    /**
     * JavaFX 有時會在焦點 listener 執行前先取消 cell 的 editing 狀態；
     * 此時仍補送 edit commit event，確保資料模型收到最後輸入。
     */
    @Override
    public void commitEdit(String newValue) {
        if (!isEditing()) {
            TableView<S> table = getTableView();
            TableColumn<S, String> column = getTableColumn();
            if (table != null && column != null && getIndex() >= 0) {
                TablePosition<S, String> position = new TablePosition<>(table, getIndex(), column);
                TableColumn.CellEditEvent<S, String> event = new TableColumn.CellEditEvent<>(
                    table, position, TableColumn.editCommitEvent(), newValue);
                Event.fireEvent(column, event);
            }
        }
        super.commitEdit(newValue);
    }
}
