package com.deck.ui;

import com.deck.config.AppPaths;
import com.deck.db.Dao;
import com.deck.db.IconStore;
import com.deck.model.AppEntry;
import com.deck.model.LaunchType;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Modal form for creating and editing app tiles.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #openCreate(Window, Consumer)} — blank form, inserts a new row
 *       on save with {@code sort_order} = current max + 1.</li>
 *   <li>{@link #openEdit(Window, AppEntry, Consumer)} — form pre-filled from
 *       the given entry; on save, updates the row in place (preserving
 *       {@code sort_order}).</li>
 * </ul>
 *
 * <p>Icon handling: choosing a PNG or JPEG opens {@link IconCropDialog} for a
 * square crop. The cropped image is held in memory and <em>not</em> written
 * until Save, at which point {@link IconStore#savePng(java.awt.image.BufferedImage)}
 * encodes it under a UUID filename in {@code .deck\icons\}. Everything is
 * normalised to PNG on disk regardless of the source format. If editing and a
 * new icon replaces an old one, the old one is deleted after the DB write
 * succeeds.
 *
 * <p>The URL type disables the target Browse button, args, and working-dir
 * inputs — they aren't meaningful for browser launches. Users can still type
 * in those fields, but they'll be saved as empty.
 */
public final class AppEditorDialog {

    private final Stage stage;
    private final Consumer<AppEntry> onSaved;
    private final Long editingId;
    private final int existingSortOrder;
    private final String existingIconFilename;

    private final TextField nameField        = new TextField();
    private final ChoiceBox<LaunchType> typeBox = new ChoiceBox<>();
    private final TextField targetField      = new TextField();
    private final Button    targetBrowse     = new Button("Browse…");
    private final TextField argsField        = new TextField();
    private final TextField workingDirField  = new TextField();
    private final Button    workingDirBrowse = new Button("Browse…");
    // COMPOSITE-only inputs. Hidden (and un-managed, so they take no space)
    // for every other launch type — see applyTypeState.
    private final TextField compositeStartupField = new TextField();
    private final TextField compositeDelayField   = new TextField();
    private final TextField compositeUrlField     = new TextField();
    private final Label     compositeStartupLabel = formLabel("Startup cmd");
    private final Label     compositeDelayLabel   = formLabel("Wait (ms)");
    private final Label     compositeUrlLabel     = formLabel("Then open");

    private final Button    chooseIconButton = new Button("Choose image…");
    private final Button    clearIconButton  = new Button("×");
    private final ImageView iconPreview      = new ImageView();
    private final Label     iconStatus       = new Label("No icon");
    private final Label     errorLabel       = new Label();

    /**
     * Non-null iff the user picked and cropped a new icon in this dialog
     * session. Held as a decoded image rather than a source path because the
     * crop has no file behind it — it's written out on save.
     */
    private Image pendingIconImage;

    /** True if the user explicitly cleared the icon during this session. */
    private boolean iconCleared;

    // ---- entry points ------------------------------------------------------

    /** Opens the dialog in "create" mode. */
    public static void openCreate(final Window owner, final Consumer<AppEntry> onSaved) {
        new AppEditorDialog(owner, null, onSaved).stage.show();
    }

    /** Opens the dialog in "edit" mode, pre-filled from {@code existing}. */
    public static void openEdit(final Window owner,
                                final AppEntry existing,
                                final Consumer<AppEntry> onSaved) {
        new AppEditorDialog(owner, existing, onSaved).stage.show();
    }

    // ---- construction ------------------------------------------------------

    private AppEditorDialog(final Window owner,
                            final AppEntry existing,
                            final Consumer<AppEntry> onSaved) {
        this.onSaved = onSaved;
        this.editingId = existing != null ? existing.id() : null;
        this.existingSortOrder = existing != null ? existing.sortOrder() : 0;
        this.existingIconFilename = existing != null ? existing.iconPath() : null;

        this.stage = new Stage();
        this.stage.initModality(Modality.WINDOW_MODAL);
        this.stage.initOwner(owner);
        this.stage.setTitle(existing == null ? "Add app" : "Edit " + existing.name());
        this.stage.setResizable(false);

        buildScene();
        wireHandlers();
        populate(existing);
    }

    // ---- scene graph -------------------------------------------------------

    private void buildScene() {
        // Text fields
        nameField.setPromptText("e.g. Trackoff");
        nameField.getStyleClass().add("form-field");

        targetField.setPromptText("URL, or file path");
        targetField.getStyleClass().add("form-field");
        targetBrowse.getStyleClass().add("button-secondary");

        argsField.setPromptText("optional");
        argsField.getStyleClass().add("form-field");

        workingDirField.setPromptText("optional");
        workingDirField.getStyleClass().add("form-field");
        workingDirBrowse.getStyleClass().add("button-secondary");

        typeBox.getItems().addAll(LaunchType.values());
        typeBox.getStyleClass().add("form-choice");

        compositeStartupField.setPromptText("e.g. node server.js");
        compositeStartupField.getStyleClass().add("form-field");
        compositeDelayField.setPromptText("e.g. 4000");
        compositeDelayField.getStyleClass().add("form-field");
        compositeUrlField.setPromptText("http://localhost:3000");
        compositeUrlField.getStyleClass().add("form-field");

        chooseIconButton.getStyleClass().add("button-secondary");
        clearIconButton.getStyleClass().add("button-secondary");
        clearIconButton.setVisible(false);
        clearIconButton.setManaged(false);

        iconPreview.setFitWidth(48);
        iconPreview.setFitHeight(48);
        iconPreview.setPreserveRatio(true);
        iconPreview.setSmooth(true);

        final StackPane iconBox = new StackPane(iconPreview);
        iconBox.setPrefSize(64, 64);
        iconBox.setMinSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.getStyleClass().add("icon-preview-box");

        iconStatus.getStyleClass().add("form-label-secondary");

        errorLabel.getStyleClass().add("form-error");

        // Grid
        final GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(14);
        form.setPadding(new Insets(28, 28, 8, 28));

        form.add(formLabel("Name"),        0, 0);
        form.add(nameField,                1, 0);

        form.add(formLabel("Type"),        0, 1);
        form.add(typeBox,                  1, 1);

        form.add(formLabel("Target"),      0, 2);
        form.add(hstack(targetField, targetBrowse, true), 1, 2);

        // Composite rows sit directly under Target so the three steps read in
        // execution order. They collapse to zero height when un-managed.
        form.add(compositeStartupLabel,    0, 3);
        form.add(compositeStartupField,    1, 3);

        form.add(compositeDelayLabel,      0, 4);
        form.add(compositeDelayField,      1, 4);

        form.add(compositeUrlLabel,        0, 5);
        form.add(compositeUrlField,        1, 5);

        form.add(formLabel("Args"),        0, 6);
        form.add(argsField,                1, 6);

        form.add(formLabel("Working dir"), 0, 7);
        form.add(hstack(workingDirField, workingDirBrowse, true), 1, 7);

        form.add(formLabel("Icon"),        0, 8);
        final HBox iconRow = new HBox(12, chooseIconButton, clearIconButton, iconBox, iconStatus);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        form.add(iconRow, 1, 8);

        final ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(110);
        c1.setPrefWidth(110);
        final ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        c2.setFillWidth(true);
        form.getColumnConstraints().addAll(c1, c2);

        // Bottom bar
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("button-secondary");
        cancel.setOnAction(e -> stage.close());

        final Button save = new Button("Save");
        save.getStyleClass().add("button-primary");
        save.setDefaultButton(true);
        save.setOnAction(e -> onSave());

        final HBox bottom = new HBox(12, errorLabel, spacer, cancel, save);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 28, 24, 28));

        final VBox root = new VBox(form, bottom);
        root.getStyleClass().add("app-root");
        root.setMinWidth(560);

        final Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());
        stage.setScene(scene);
    }

    private static Label formLabel(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("form-label");
        return l;
    }

    private HBox hstack(final TextField field, final Button browse, final boolean grow) {
        final HBox h = new HBox(8, field, browse);
        h.setAlignment(Pos.CENTER_LEFT);
        if (grow) HBox.setHgrow(field, Priority.ALWAYS);
        return h;
    }

    // ---- initial values ----------------------------------------------------

    private void populate(final AppEntry existing) {
        if (existing == null) {
            typeBox.setValue(LaunchType.URL);
            applyTypeState(LaunchType.URL);
            return;
        }
        nameField.setText(nullSafe(existing.name()));
        typeBox.setValue(existing.launchType());
        targetField.setText(nullSafe(existing.launchTarget()));
        argsField.setText(nullSafe(existing.launchArgs()));
        workingDirField.setText(nullSafe(existing.workingDir()));
        compositeStartupField.setText(nullSafe(existing.compositeStartup()));
        compositeDelayField.setText(
                existing.compositeDelayMs() > 0 ? Integer.toString(existing.compositeDelayMs()) : "");
        compositeUrlField.setText(nullSafe(existing.compositeUrl()));
        applyTypeState(existing.launchType());
        loadExistingIconPreview(existing.iconPath());
    }

    private void loadExistingIconPreview(final String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            final Path p = AppPaths.iconsFolder().resolve(filename);
            if (Files.exists(p)) {
                iconPreview.setImage(new Image(p.toUri().toString(), 48, 48, true, true));
                iconStatus.setText("current icon");
                showClearButton(true);
            }
        } catch (Exception ignored) {
            // fall through — preview stays empty
        }
    }

    private static String nullSafe(final String s) {
        return s == null ? "" : s;
    }

    // ---- event wiring ------------------------------------------------------

    private void wireHandlers() {
        typeBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> applyTypeState(val));

        targetBrowse.setOnAction(e -> pickTargetFile());
        workingDirBrowse.setOnAction(e -> pickWorkingDir());
        chooseIconButton.setOnAction(e -> pickIcon());
        clearIconButton.setOnAction(e -> clearIcon());
    }

    private void applyTypeState(final LaunchType type) {
        final boolean isUrl       = type == LaunchType.URL;
        final boolean isComposite = type == LaunchType.COMPOSITE;

        setRowVisible(isComposite,
                compositeStartupLabel, compositeStartupField,
                compositeDelayLabel,   compositeDelayField,
                compositeUrlLabel,     compositeUrlField);

        // Only COMPOSITE disables the target field — it drives everything from
        // its own three fields. URL very much needs it: that's where the URL is
        // typed. Browse is the part that's meaningless for a URL, not the field.
        targetField.setDisable(isComposite);
        targetBrowse.setDisable(isUrl || isComposite);

        argsField.setDisable(isUrl || isComposite);
        // Working dir still matters for composite — it's where the startup
        // command runs (e.g. the folder holding server.js).
        workingDirField.setDisable(isUrl);
        workingDirBrowse.setDisable(isUrl);

        if (isComposite) {
            targetField.setPromptText("(not used — set 'Then open' below)");
        } else {
            targetField.setPromptText(isUrl ? "https://example.com" : "path to file");
        }
    }

    private static void setRowVisible(final boolean visible, final javafx.scene.Node... nodes) {
        for (javafx.scene.Node n : nodes) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    private void pickTargetFile() {
        final LaunchType type = typeBox.getValue();
        if (type == LaunchType.URL) return;
        final FileChooser fc = new FileChooser();
        fc.setTitle("Choose target");
        switch (type) {
            case EXE    -> fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Executables", "*.exe"));
            case JAR    -> fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Java archives", "*.jar"));
            case SCRIPT -> fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Scripts", "*.bat", "*.cmd", "*.ps1"));
            default -> { }
        }
        final File chosen = fc.showOpenDialog(stage);
        if (chosen != null) {
            targetField.setText(chosen.getAbsolutePath());
        }
    }

    private void pickWorkingDir() {
        final DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose working directory");
        final File chosen = dc.showDialog(stage);
        if (chosen != null) {
            workingDirField.setText(chosen.getAbsolutePath());
        }
    }

    private void pickIcon() {
        final FileChooser fc = new FileChooser();
        fc.setTitle("Choose icon image");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG images", "*.png"),
                new FileChooser.ExtensionFilter("JPEG images", "*.jpg", "*.jpeg"));
        final File chosen = fc.showOpenDialog(stage);
        if (chosen == null) return;

        // Load at full resolution so the crop keeps the original detail.
        final Image full;
        try {
            full = new Image(chosen.toURI().toString());
            if (full.isError() || full.getWidth() <= 0) {
                iconStatus.setText("Couldn't read that image");
                return;
            }
        } catch (Exception ex) {
            iconStatus.setText("Couldn't read that image");
            return;
        }

        // Cancelling the cropper leaves any previously chosen icon untouched.
        IconCropDialog.open(stage, full).ifPresent(cropped -> {
            pendingIconImage = cropped;
            iconCleared = false;
            iconPreview.setImage(cropped);
            iconStatus.setText(chosen.getName());
            showClearButton(true);
        });
    }

    private void clearIcon() {
        pendingIconImage = null;
        iconCleared = true;
        iconPreview.setImage(null);
        iconStatus.setText("No icon");
        showClearButton(false);
    }

    private void showClearButton(final boolean visible) {
        clearIconButton.setVisible(visible);
        clearIconButton.setManaged(visible);
    }

    // ---- save --------------------------------------------------------------

    private void onSave() {
        errorLabel.setText("");
        final String name       = nameField.getText() == null ? "" : nameField.getText().trim();
        final LaunchType type   = typeBox.getValue();
        String       target     = targetField.getText() == null ? "" : targetField.getText().trim();
        final String args       = argsField.getText() == null ? "" : argsField.getText().trim();
        final String workingDir = workingDirField.getText() == null ? "" : workingDirField.getText().trim();

        final String cStartup = trimmed(compositeStartupField);
        final String cUrl     = trimmed(compositeUrlField);
        final String cDelayIn = trimmed(compositeDelayField);
        int cDelay = 0;

        if (name.isEmpty()) {
            errorLabel.setText("Name is required");
            return;
        }

        if (type == LaunchType.COMPOSITE) {
            if (cUrl.isEmpty()) {
                errorLabel.setText("'Then open' URL is required for composite tiles");
                return;
            }
            if (!cDelayIn.isEmpty()) {
                try {
                    cDelay = Integer.parseInt(cDelayIn);
                } catch (NumberFormatException ex) {
                    errorLabel.setText("Wait must be a whole number of milliseconds");
                    return;
                }
                if (cDelay < 0) {
                    errorLabel.setText("Wait cannot be negative");
                    return;
                }
            }
            // launch_target is NOT NULL in the schema and composite doesn't use
            // it, so mirror the URL in to keep the row valid and readable.
            target = cUrl;
        } else if (target.isEmpty()) {
            errorLabel.setText("Target is required");
            return;
        }

        String finalIconFilename = existingIconFilename;
        if (iconCleared) {
            finalIconFilename = null;
        }

        try {
            if (pendingIconImage != null) {
                final String newFilename = IconStore.savePng(
                        SwingFXUtils.fromFXImage(pendingIconImage, null));
                if (existingIconFilename != null && !existingIconFilename.isBlank()) {
                    IconStore.deleteFromStore(existingIconFilename);
                }
                finalIconFilename = newFilename;
            } else if (iconCleared && existingIconFilename != null) {
                IconStore.deleteFromStore(existingIconFilename);
            }

            final boolean composite = type == LaunchType.COMPOSITE;
            final AppEntry saved = persist(name, type, target,
                    args.isEmpty() ? null : args,
                    workingDir.isEmpty() ? null : workingDir,
                    finalIconFilename,
                    composite && !cStartup.isEmpty() ? cStartup : null,
                    composite ? cDelay : 0,
                    composite ? cUrl : null);

            if (onSaved != null) onSaved.accept(saved);
            stage.close();
        } catch (Exception ex) {
            errorLabel.setText("Save failed: " + ex.getMessage());
        }
    }

    private AppEntry persist(final String name, final LaunchType type, final String target,
                             final String args, final String workingDir,
                             final String iconFilename, final String compositeStartup,
                             final int compositeDelayMs, final String compositeUrl)
            throws Exception {
        if (editingId != null) {
            final AppEntry entry = new AppEntry(
                    editingId, name, type, target, args, workingDir,
                    iconFilename, existingSortOrder,
                    compositeStartup, compositeDelayMs, compositeUrl);
            Dao.updateApp(entry);
            return entry;
        }
        final int order = Dao.nextSortOrder();
        final AppEntry draft = new AppEntry(
                -1L, name, type, target, args, workingDir, iconFilename, order,
                compositeStartup, compositeDelayMs, compositeUrl);
        final long id = Dao.insertApp(draft);
        return new AppEntry(id, name, type, target, args, workingDir, iconFilename, order,
                compositeStartup, compositeDelayMs, compositeUrl);
    }

    private static String trimmed(final TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }
}