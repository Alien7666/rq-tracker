package com.rqtracker.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * 系統剪貼板工具（對應 HTML 的 copyFilename / navigator.clipboard.writeText）。
 */
public final class ClipboardUtils {

    private ClipboardUtils() {}

    /**
     * 複製純文字到系統剪貼板。
     * 必須在 JavaFX Application Thread 上呼叫。
     */
    public static void copyText(String text) {
        if (text == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
