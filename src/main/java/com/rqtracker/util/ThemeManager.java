package com.rqtracker.util;

import com.rqtracker.service.AppConfig;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 集中管理 dark/light 主題切換。
 * - 註冊所有開啟的 Scene 弱參考
 * - 切換時將 theme-light/theme-dark CSS class 加到 Scene root
 * - 與 AppConfig.themeMode 同步
 */
public final class ThemeManager {

    private static final String CLASS_LIGHT = "theme-light";
    private static final String CLASS_DARK = "theme-dark";

    private static final List<WeakReference<Scene>> SCENES = new ArrayList<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private ThemeManager() {}

    public static synchronized void registerScene(Scene scene) {
        if (scene == null) return;
        SCENES.removeIf(ref -> ref.get() == null || ref.get() == scene);
        SCENES.add(new WeakReference<>(scene));
        applyToScene(scene, currentMode());
    }

    public static synchronized void unregisterScene(Scene scene) {
        if (scene == null) return;
        SCENES.removeIf(ref -> ref.get() == null || ref.get() == scene);
    }

    public static void addListener(Runnable listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static String currentMode() {
        return AppConfig.getInstance().getThemeMode();
    }

    public static boolean isLight() {
        return "light".equals(currentMode());
    }

    public static synchronized void toggle() {
        setMode(isLight() ? "dark" : "light");
    }

    public static synchronized void setMode(String mode) {
        AppConfig.getInstance().setThemeMode(mode);
        applyToAll();
        for (Runnable l : new ArrayList<>(LISTENERS)) {
            try { l.run(); } catch (Exception ignored) {}
        }
    }

    private static void applyToAll() {
        String mode = currentMode();
        Iterator<WeakReference<Scene>> it = SCENES.iterator();
        while (it.hasNext()) {
            Scene s = it.next().get();
            if (s == null) { it.remove(); continue; }
            applyToScene(s, mode);
        }
    }

    private static void applyToScene(Scene scene, String mode) {
        Parent root = scene.getRoot();
        if (root == null) return;
        var classes = root.getStyleClass();
        classes.removeAll(CLASS_LIGHT, CLASS_DARK);
        classes.add("light".equals(mode) ? CLASS_LIGHT : CLASS_DARK);
    }
}
