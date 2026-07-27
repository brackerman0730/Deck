package com.deck.ui;

import com.deck.config.AppPaths;
import com.deck.config.Settings;
import com.deck.db.Dao;
import com.deck.db.IconStore;
import com.deck.model.AppEntry;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Settings modal — reachable from the gear icon in {@link LauncherView}.
 *
 * <p>Phase 1 exposes three actionable settings and a couple of read-only bits:
 * <ul>
 *   <li><b>Startup</b> — toggle "Launch Deck on Windows startup." The toggle
 *       persists to {@code settings.startup.autostart}. Actual wiring
 *       (a {@code shell:startup} shortcut or {@code HKCU\...\Run} registry
 *       key) is Phase 2 — this drop just captures user intent.</li>
 *   <li><b>Data folder</b> — displays {@code %USERPROFILE%\.deck\} with an
 *       "Open in Explorer" button.</li>
 *   <li><b>Reset</b> — nukes every tile and its icon file. Source apps on disk
 *       are untouched.</li>
 * </ul>
 *
 * <p>The modal is deliberately narrow (520px) and non-resizable — settings
 * lists shouldn't need scrolling, and if they do, we've added too many. When
 * something structural changes (e.g. tile wipe), {@code onChanged} fires so
 * the caller can refresh its grid.
 */
public final class SettingsView {

    // ---- persisted setting keys -------------------------------------------

    /** Whether Deck should launch when Windows boots. */
    public static final String KEY_AUTOSTART      = "startup.autostart";

    /** Last main-window width in pixels. */
    public static final String KEY_WINDOW_WIDTH   = "window.width";

    /** Last main-window height in pixels. */
    public static final String KEY_WINDOW_HEIGHT  = "window.height";

    /** Last main-window X screen coordinate. */
    public static final String KEY_WINDOW_X       = "window.x";

    /** Last main-window Y screen coordinate. */
    public static final String KEY_WINDOW_Y       = "window.y";

    /** Whether the main window was maximized on last close. */
    public static final String KEY_WINDOW_MAX     = "window.maximized";

    private final Stage stage;
    private final Runnable onChanged;

    // ---- entry point -------------------------------------------------------

    /**
     * Opens the settings modal.
     *
     * @param owner      parent window (main stage) for modality anchoring
     * @param onChanged  called on the FX thread after a structural change
     *                   (e.g. reset-all). Nullable if the caller doesn't care.
     */
    public static void open(final Window owner, final Runnable onChanged) {
        new SettingsView(owner, onChanged).stage.show();
    }

    // ---- construction ------------------------------------------------------

    private SettingsView(final Window owner, final Runnable onChanged) {
        this.onChanged = onChanged;
        this.stage = new Stage();
        this.stage.initModality(Modality.WINDOW_MODAL);
        this.stage.initOwner(owner);
        this.stage.setTitle("Settings");
        this.stage.setResizable(false);
        buildScene();
    }

    // ---- scene -------------------------------------------------------------

    private void buildScene() {
        final VBox root = new VBox(28,
                buildStartupSection(),
                buildDataSection(),
                buildResetSection(),
                buildFooter());
        root.setPadding(new Insets(32, 32, 24, 32));
        root.getStyleClass().add("app-root");
        root.setMinWidth(520);

        final Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());
        stage.setScene(scene);
    }

    // ---- startup section ---------------------------------------------------

    private VBox buildStartupSection() {
        final Label heading = sectionHeading("STARTUP");

        final CheckBox autostart = new CheckBox("Launch Deck on Windows startup");
        autostart.getStyleClass().add("form-check");
        autostart.setSelected(readBool(KEY_AUTOSTART, false));
        autostart.selectedProperty().addListener((obs, old, val) -> {
            try {
                Settings.setBool(KEY_AUTOSTART, val);
            } catch (SQLException ex) {
                autostart.setSelected(old);
                showBlockingError("Failed to save setting: " + ex.getMessage());
            }
        });

        final Label hint = hint(
                "Actual startup wiring lands in Phase 2 — this toggle "
              + "just remembers your preference for now.");

        return section(heading, autostart, hint);
    }

    // ---- data section ------------------------------------------------------

    private VBox buildDataSection() {
        final Label heading = sectionHeading("DATA FOLDER");

        // Read-only path field — copy-friendly, and the border makes it read
        // like the file-picker fields in AppEditorDialog for consistency.
        final TextField pathField = new TextField(AppPaths.dataFolder().toString());
        pathField.setEditable(false);
        pathField.setFocusTraversable(false);
        pathField.getStyleClass().add("form-field");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        final Button open = new Button("Open");
        open.getStyleClass().add("button-secondary");
        open.setOnAction(e -> openDataFolder());

        final HBox row = new HBox(8, pathField, open);
        row.setAlignment(Pos.CENTER_LEFT);

        return section(heading, row);
    }

    // ---- reset section -----------------------------------------------------

    private VBox buildResetSection() {
        final Label heading = sectionHeading("RESET");

        final Button reset = new Button("Remove all tiles…");
        reset.getStyleClass().add("button-danger");
        reset.setOnAction(e -> onResetAll());

        final Label hint = hint(
                "Deletes every tile and its icon from Deck. "
              + "Source apps on your disk are not touched.");

        return section(heading, reset, hint);
    }

    // ---- footer ------------------------------------------------------------

    private HBox buildFooter() {
        final Label version = new Label("Deck • Phase 1");
        version.getStyleClass().add("form-label-secondary");

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final Button close = new Button("Close");
        close.getStyleClass().add("button-secondary");
        close.setOnAction(e -> stage.close());
        close.setDefaultButton(true);

        final HBox bar = new HBox(12, version, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 0, 0, 0));
        return bar;
    }

    // ---- helpers -----------------------------------------------------------

    private static VBox section(final javafx.scene.Node... children) {
        final VBox box = new VBox(10, children);
        box.setFillWidth(true);
        return box;
    }

    private static Label sectionHeading(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("section-heading");
        return l;
    }

    private static Label hint(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("form-hint");
        l.setWrapText(true);
        l.setMaxWidth(456);
        return l;
    }

    private static boolean readBool(final String key, final boolean fallback) {
        try {
            return Settings.getBool(key, fallback);
        } catch (SQLException e) {
            return fallback;
        }
    }

    // ---- actions -----------------------------------------------------------

    private void openDataFolder() {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                showBlockingError("Opening folders isn't supported on this OS.");
                return;
            }
            Desktop.getDesktop().open(AppPaths.dataFolder().toFile());
        } catch (Exception ex) {
            showBlockingError("Cannot open folder: " + ex.getMessage());
        }
    }

    private void onResetAll() {
        final Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove all tiles");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Remove every tile from Deck?\n\n"
              + "This deletes tile records and their stored icons. "
              + "Source apps on your disk are not affected.");
        confirm.initOwner(stage);
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());
        confirm.getDialogPane().getStyleClass().add("app-root");

        final Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            final List<AppEntry> all = Dao.loadApps();
            for (AppEntry app : all) {
                Dao.deleteApp(app.id());
                IconStore.deleteFromStore(app.iconPath());
            }
            if (onChanged != null) onChanged.run();
            stage.close();
        } catch (SQLException ex) {
            showBlockingError("Reset failed: " + ex.getMessage());
        }
    }

    private void showBlockingError(final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Deck");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(stage);
        alert.showAndWait();
    }
}