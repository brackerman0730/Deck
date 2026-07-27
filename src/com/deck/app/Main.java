package com.deck.app;

import com.deck.config.AppPaths;
import com.deck.db.Database;
import com.deck.config.Settings;
import com.deck.ui.LauncherView;
import com.deck.ui.SettingsView;
import com.deck.db.SeedData;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.SQLException;

/**
 * Deck — entry point.
 *
 * <p>Boots the SQLite database (creating {@code %USERPROFILE%\.deck\} and
 * running migrations on first run), then hands control to {@link LauncherView}.
 * Drop 1 renders a placeholder centered text so we can verify the window,
 * theme, and DB init end-to-end before wiring the tile grid.
 */
public final class Main extends Application {

    @Override
    public void start(final Stage stage) throws Exception {
        // -- boot storage -----------------------------------------------------
        AppPaths.ensureDataFolder();
        Database.init();
        // -- first-run sample tiles (Drop 2) ---------------------------------
        SeedData.seedIfEmpty();
        // -- placeholder scene (Drop 1) --------------------------------------
        // Drop 2 replaces this with LauncherView.
        // -- main scene (Drop 2) ---------------------------------------------
        final Scene scene = new Scene(
                (javafx.scene.Parent) LauncherView.build(stage), 1000, 700);
        scene.getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());

        stage.setTitle("Deck");
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(400);

        // -- window state (Drop 5) -------------------------------------------
        // Restore size/position from the last session, and register a save
        // handler so we persist geometry on graceful close.
        restoreWindowState(stage);
        stage.setOnCloseRequest(e -> saveWindowState(stage));

        stage.show();
    }

// ---- window state persistence -----------------------------------------

    /**
     * Applies saved geometry to the stage before it's shown. Silently falls
     * back to the Scene's default size if anything is missing or malformed —
     * a corrupted settings row shouldn't prevent the app from starting.
     */
    private static void restoreWindowState(final Stage stage) {
        try {
            stage.setWidth(Settings.getInt(SettingsView.KEY_WINDOW_WIDTH, 1000));
            stage.setHeight(Settings.getInt(SettingsView.KEY_WINDOW_HEIGHT, 700));

            final String xStr = Settings.get(SettingsView.KEY_WINDOW_X);
            final String yStr = Settings.get(SettingsView.KEY_WINDOW_Y);
            if (xStr != null && yStr != null) {
                stage.setX(Integer.parseInt(xStr));
                stage.setY(Integer.parseInt(yStr));
            }
            if (Settings.getBool(SettingsView.KEY_WINDOW_MAX, false)) {
                stage.setMaximized(true);
            }
        } catch (SQLException | NumberFormatException ignored) {
            // Best-effort — defaults are fine.
        }
    }

    /**
     * Persists current geometry on close. We only save width/height/x/y when
     * the window is <em>not</em> maximized — otherwise "restore" on next
     * launch would use the maximized-frame numbers, which don't match the
     * user's preferred windowed size.
     */
    private static void saveWindowState(final Stage stage) {
        try {
            Settings.setBool(SettingsView.KEY_WINDOW_MAX, stage.isMaximized());
            if (!stage.isMaximized()) {
                Settings.setInt(SettingsView.KEY_WINDOW_WIDTH,  (int) stage.getWidth());
                Settings.setInt(SettingsView.KEY_WINDOW_HEIGHT, (int) stage.getHeight());
                Settings.setInt(SettingsView.KEY_WINDOW_X,      (int) stage.getX());
                Settings.setInt(SettingsView.KEY_WINDOW_Y,      (int) stage.getY());
            }
        } catch (SQLException ignored) {
            // Best-effort — geometry isn't critical.
        }
    }

    public static void main(final String[] args) {
        launch(args);
    }
}
