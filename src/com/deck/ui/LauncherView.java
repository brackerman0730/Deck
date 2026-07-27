package com.deck.ui;

import com.deck.db.Dao;
import com.deck.db.IconStore;
import com.deck.model.AppEntry;
import com.deck.ui.LaunchService.LaunchResult;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The main grid view — Phase 1 final form.
 *
 * <p>Structure: a {@link StackPane} root that stacks main content (top-bar +
 * either the tile grid or an empty-state welcome, depending on whether the
 * user has any apps yet) and an invisible overlay layer for {@link Toast}s.
 *
 * <p>Drop 6 additions on top of Drop 5:
 * <ul>
 *   <li><b>Empty state</b> — when zero apps exist, the grid is replaced with
 *       a centered welcome + CTA. The {@code +} tile still lives at the end
 *       of the grid too, but new users get a much friendlier landing.</li>
 *   <li><b>Drag-and-drop reorder</b> — grab any tile, drop onto another to
 *       insert-before. The drop target highlights with the accent color.
 *       Drop is transactional (see {@link Dao#setSortOrdersBulk(List)}).</li>
 *   <li><b>Keyboard nav</b> — arrow keys move focus between tiles, Enter
 *       launches (or opens the editor on {@code +}), Delete triggers remove.
 *       Column count is computed live from tile geometry so wrapping works.</li>
 * </ul>
 */
public final class LauncherView {

    /** Gutter between tiles in pixels. */
    private static final double GUTTER = 24.0;

    /** Horizontal + vertical padding around the whole grid. */
    private static final double GRID_PADDING = 32.0;

    /** MIME-ish key for the app id we carry on the dragboard. */
    private static final String DRAG_KEY = "deck-app-id";

    private final Stage owner;
    private final TilePane grid;
    private final ScrollPane scroll;
    private final BorderPane content;
    private final StackPane overlay;

    /** Latest snapshot of apps, in grid order. Used for neighbor lookups. */
    private List<AppEntry> currentApps = List.of();

    // ---- construction ------------------------------------------------------

    private LauncherView(final Stage owner) {
        this.owner = owner;

        this.grid = new TilePane();
        this.grid.setHgap(GUTTER);
        this.grid.setVgap(GUTTER);
        this.grid.setPadding(new Insets(GRID_PADDING));
        this.grid.setAlignment(Pos.TOP_LEFT);
        this.grid.getStyleClass().add("tile-grid");

        this.scroll = new ScrollPane(this.grid);
        this.scroll.setFitToWidth(true);
        this.scroll.setFitToHeight(true);
        this.scroll.getStyleClass().add("grid-scroll");
        this.scroll.setPannable(false);

        this.content = new BorderPane();
        this.content.setTop(buildTopBar());
        this.content.setCenter(this.scroll);

        this.overlay = new StackPane(this.content);
        this.overlay.getStyleClass().add("app-root");

        // Keyboard nav is a scene-level filter — otherwise there's no way
        // to re-enter grid focus after a launch stole it away (browser
        // opens, main window loses focus, tiles are effectively unreachable
        // via keyboard). Scene isn't available during construction, so we
        // hook the listener via a sceneProperty callback.
        this.overlay.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::onSceneKeyPressed);
            }
        });
    }

    /** Builds the launcher view and returns its root node. */
    public static Node build(final Stage owner) throws SQLException {
        final LauncherView view = new LauncherView(owner);
        view.refresh();
        return view.overlay;
    }

    // ---- top bar -----------------------------------------------------------

    private Node buildTopBar() {
        final Text title = new Text("Deck");
        title.getStyleClass().add("app-title");

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final Button gear = new Button("⚙");
        gear.getStyleClass().add("gear-button");
        gear.setFocusTraversable(false);
        gear.setOnAction(e -> SettingsView.open(owner, this::refreshQuietly));

        final HBox bar = new HBox(title, spacer, gear);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(20, 32, 12, 32));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    // ---- grid refresh ------------------------------------------------------

    /** Reloads apps from the DB and rebuilds the grid contents. */
    public void refresh() throws SQLException {
        this.currentApps = Dao.loadApps();

        if (currentApps.isEmpty()) {
            // Zero apps — swap the center for the welcome screen. The grid
            // is retained (not detached) so re-adding it in refresh() after
            // the first tile is created is a single setCenter call.
            content.setCenter(buildEmptyState());
            return;
        }

        content.setCenter(scroll);
        grid.getChildren().clear();

        for (AppEntry app : currentApps) {
            final Node tile = AppTile.build(
                    app,
                    () -> onTileClick(app),
                    e -> showContextMenu(app, e));
            attachDragHandlers(tile, app);
            grid.getChildren().add(tile);
        }

        final Node addTile = AppTile.buildAddTile(this::onAddClick);
        // The + tile is a drop target too — dropping onto it moves the
        // dragged tile to the end of the grid.
        attachEndDropHandlers(addTile);
        grid.getChildren().add(addTile);
    }

    /** Best-effort refresh — turns SQL failures into a toast. */
    private void refreshQuietly() {
        try {
            refresh();
        } catch (SQLException e) {
            Toast.error(overlay, "Refresh failed: " + e.getMessage());
        }
    }

    // ---- empty state -------------------------------------------------------

    private Node buildEmptyState() {
        final Label title = new Label("Welcome to Deck");
        title.getStyleClass().add("empty-title");

        final Label subtitle = new Label(
                "Add your apps, sites, and scripts to launch them with one click.");
        subtitle.getStyleClass().add("empty-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(480);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        final Button add = new Button("Add your first app");
        add.getStyleClass().add("button-primary");
        add.setOnAction(e -> onAddClick());

        final VBox box = new VBox(16, title, subtitle, new Region(), add);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        // Spacer region above the button — VBox children align to center,
        // so a small unmanaged gap keeps the button visually distinct.
        final Region gap = (Region) box.getChildren().get(2);
        gap.setPrefHeight(8);
        gap.setMinHeight(8);

        return box;
    }

    // ---- launch ------------------------------------------------------------

    private void onTileClick(final AppEntry app) {
        LaunchService.launch(app, this::onLaunchDone);
    }

    private void onLaunchDone(final LaunchResult result) {
        if (result.success()) {
            Toast.success(overlay, result.message());
        } else {
            Toast.error(overlay, result.message());
        }
    }

    // ---- add ---------------------------------------------------------------

    private void onAddClick() {
        AppEditorDialog.openCreate(owner, saved -> {
            refreshQuietly();
            Toast.success(overlay, "Added " + saved.name());
        });
    }

    // ---- context menu ------------------------------------------------------

   private void showContextMenu(final AppEntry app, final ContextMenuEvent e) {
        // Right-click also focuses the tile so keyboard shortcuts (Delete,
        // arrow keys) work immediately after dismissing the menu.
        if (e.getSource() instanceof Node node) {
            node.requestFocus();
        }

        final MenuItem edit = new MenuItem("Edit…");
        edit.setOnAction(a -> onEdit(app));

        final MenuItem remove = new MenuItem("Remove");
        remove.setOnAction(a -> onRemove(app));

        final MenuItem left = new MenuItem("Move left");
        left.setOnAction(a -> onMoveByOne(app, -1));
        left.setDisable(indexOf(app) <= 0);

        final MenuItem right = new MenuItem("Move right");
        right.setOnAction(a -> onMoveByOne(app, +1));
        right.setDisable(indexOf(app) >= currentApps.size() - 1);

        final ContextMenu menu = new ContextMenu(
                edit, remove, new SeparatorMenuItem(), left, right);
        menu.getStyleClass().add("deck-menu");
        menu.show((Node) e.getSource(), e.getScreenX(), e.getScreenY());
    }

    private int indexOf(final AppEntry app) {
        for (int i = 0; i < currentApps.size(); i++) {
            if (currentApps.get(i).id() == app.id()) return i;
        }
        return -1;
    }

    // ---- edit --------------------------------------------------------------

    private void onEdit(final AppEntry app) {
        AppEditorDialog.openEdit(owner, app, saved -> {
            refreshQuietly();
            Toast.success(overlay, "Saved " + saved.name());
        });
    }

    // ---- remove ------------------------------------------------------------

    private void onRemove(final AppEntry app) {
        final Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove app");
        confirm.setHeaderText(null);
        confirm.setContentText("Remove \"" + app.name() + "\" from Deck?");
        confirm.initOwner(owner);
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());
        confirm.getDialogPane().getStyleClass().add("app-root");

        final Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            Dao.deleteApp(app.id());
            IconStore.deleteFromStore(app.iconPath());
            refresh();
            Toast.success(overlay, "Removed " + app.name());
        } catch (SQLException e) {
            Toast.error(overlay, "Remove failed: " + e.getMessage());
        }
    }

    // ---- single-step reorder (context menu) --------------------------------

    private void onMoveByOne(final AppEntry app, final int delta) {
        final int i = indexOf(app);
        final int j = i + delta;
        if (i < 0 || j < 0 || j >= currentApps.size()) return;

        final AppEntry a = currentApps.get(i);
        final AppEntry b = currentApps.get(j);
        try {
            Dao.setSortOrder(a.id(), b.sortOrder());
            Dao.setSortOrder(b.id(), a.sortOrder());
            refresh();
        } catch (SQLException e) {
            Toast.error(overlay, "Reorder failed: " + e.getMessage());
        }
    }

    // ---- drag & drop reorder -----------------------------------------------

    private void attachDragHandlers(final Node tile, final AppEntry app) {
        tile.setOnDragDetected(e -> onDragDetected(tile, app, e));
        tile.setOnDragOver(e -> onDragOver(tile, e));
        tile.setOnDragEntered(e -> onDragEntered(tile, e));
        tile.setOnDragExited(e -> onDragExited(tile, e));
        tile.setOnDragDropped(e -> onDragDroppedOnTile(app.id(), e));
        tile.setOnDragDone(e -> tile.getStyleClass().remove("tile-dragging"));
    }

    /** The + tile only accepts drops — dropping there means "move to end." */
    private void attachEndDropHandlers(final Node addTile) {
        addTile.setOnDragOver(e -> onDragOver(addTile, e));
        addTile.setOnDragEntered(e -> onDragEntered(addTile, e));
        addTile.setOnDragExited(e -> onDragExited(addTile, e));
        addTile.setOnDragDropped(e -> onDragDroppedAtEnd(e));
    }

    private void onDragDetected(final Node tile, final AppEntry app, final javafx.scene.input.MouseEvent e) {
        final Dragboard db = tile.startDragAndDrop(TransferMode.MOVE);
        final ClipboardContent content = new ClipboardContent();
        content.putString(DRAG_KEY + ":" + app.id());
        db.setContent(content);

        // Transparent-background snapshot so the drag ghost matches the tile.
        final SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        db.setDragView(tile.snapshot(sp, null));

        tile.getStyleClass().add("tile-dragging");
        e.consume();
    }

    private void onDragOver(final Node target, final DragEvent e) {
        if (e.getGestureSource() != target && isDeckDrag(e.getDragboard())) {
            e.acceptTransferModes(TransferMode.MOVE);
        }
        e.consume();
    }

    private void onDragEntered(final Node target, final DragEvent e) {
        if (e.getGestureSource() != target && isDeckDrag(e.getDragboard())) {
            target.getStyleClass().add("tile-drag-over");
        }
        e.consume();
    }

    private void onDragExited(final Node target, final DragEvent e) {
        target.getStyleClass().remove("tile-drag-over");
        e.consume();
    }

    private void onDragDroppedOnTile(final long targetId, final DragEvent e) {
        boolean ok = false;
        final Long draggedId = extractDragId(e.getDragboard());
        if (draggedId != null && draggedId != targetId) {
            try {
                reorderInsertBefore(draggedId, targetId);
                ok = true;
            } catch (SQLException ex) {
                Toast.error(overlay, "Reorder failed: " + ex.getMessage());
            }
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    private void onDragDroppedAtEnd(final DragEvent e) {
        boolean ok = false;
        final Long draggedId = extractDragId(e.getDragboard());
        if (draggedId != null) {
            try {
                reorderMoveToEnd(draggedId);
                ok = true;
            } catch (SQLException ex) {
                Toast.error(overlay, "Reorder failed: " + ex.getMessage());
            }
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    private static boolean isDeckDrag(final Dragboard db) {
        return db.hasString() && db.getString() != null
                && db.getString().startsWith(DRAG_KEY + ":");
    }

    private static Long extractDragId(final Dragboard db) {
        if (!isDeckDrag(db)) return null;
        try {
            return Long.parseLong(db.getString().substring(DRAG_KEY.length() + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void reorderInsertBefore(final long draggedId, final long targetId) throws SQLException {
        final List<AppEntry> reordered = new ArrayList<>(currentApps);
        final AppEntry dragged = removeById(reordered, draggedId);
        if (dragged == null) return;

        int targetIdx = -1;
        for (int i = 0; i < reordered.size(); i++) {
            if (reordered.get(i).id() == targetId) { targetIdx = i; break; }
        }
        if (targetIdx < 0) return;

        reordered.add(targetIdx, dragged);
        persistOrder(reordered);
    }

    private void reorderMoveToEnd(final long draggedId) throws SQLException {
        final List<AppEntry> reordered = new ArrayList<>(currentApps);
        final AppEntry dragged = removeById(reordered, draggedId);
        if (dragged == null) return;
        reordered.add(dragged);
        persistOrder(reordered);
    }

    private static AppEntry removeById(final List<AppEntry> list, final long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == id) {
                return list.remove(i);
            }
        }
        return null;
    }

    private void persistOrder(final List<AppEntry> ordered) throws SQLException {
        final List<Long> ids = new ArrayList<>(ordered.size());
        for (AppEntry a : ordered) ids.add(a.id());
        Dao.setSortOrdersBulk(ids);
        refresh();
    }

    // ---- keyboard navigation -----------------------------------------------

    /**
     * Scene-level key filter. Two responsibilities:
     * <ol>
     *   <li>If focus is <em>not</em> on a tile and the user presses an arrow
     *       key, jump focus to the first tile — this is how the user
     *       re-enters keyboard nav after a launch stole focus away.</li>
     *   <li>If focus <em>is</em> on a tile, handle Enter / Delete / arrows
     *       exactly as before.</li>
     * </ol>
     *
     * <p>{@code BACK_SPACE} mirrors {@code DELETE} because some Windows laptop
     * layouts hide the real Delete key behind an Fn combination — accepting
     * both keys means Deck works on every keyboard.
     */
    private void onSceneKeyPressed(final KeyEvent e) {
        final javafx.scene.Scene scene = grid.getScene();
        if (scene == null) return;

        final Node focused = scene.getFocusOwner();
        final boolean inGrid = focused != null
                && grid.getChildren().contains(focused);

        // Case 1: focus is elsewhere (or nowhere). Arrow keys jump into grid.
        if (!inGrid) {
            if (isArrowKey(e.getCode()) && !grid.getChildren().isEmpty()) {
                grid.getChildren().get(0).requestFocus();
                e.consume();
            }
            return;
        }

        // Case 2: a tile is focused. Normal nav.
        final int idx = grid.getChildren().indexOf(focused);
        final int lastIdx  = grid.getChildren().size() - 1;   // + tile
        final boolean isAddTile = (idx == lastIdx);

        switch (e.getCode()) {
            case ENTER -> {
                if (isAddTile) onAddClick();
                else           onTileClick(currentApps.get(idx));
                e.consume();
            }
            case DELETE, BACK_SPACE -> {
                if (!isAddTile) {
                    onRemove(currentApps.get(idx));
                    e.consume();
                }
            }
            case LEFT  -> { moveFocus(idx, -1);           e.consume(); }
            case RIGHT -> { moveFocus(idx, +1);           e.consume(); }
            case UP    -> { moveFocus(idx, -columns());   e.consume(); }
            case DOWN  -> { moveFocus(idx, +columns());   e.consume(); }
            default -> { /* let other keys bubble */ }
        }
    }

    private static boolean isArrowKey(final KeyCode c) {
        return c == KeyCode.LEFT  || c == KeyCode.RIGHT
            || c == KeyCode.UP    || c == KeyCode.DOWN;
    }

    /**
     * Computes how many tiles fit per row given the current grid width.
     * Recomputed on every key press because the user can resize freely.
     */
    private int columns() {
        final double usableWidth = grid.getWidth()
                - grid.getPadding().getLeft()
                - grid.getPadding().getRight();
        final double slot = AppTile.TILE_SIZE + GUTTER;
        final int cols = (int) Math.floor((usableWidth + GUTTER) / slot);
        return Math.max(1, cols);
    }

    private void moveFocus(final int fromIdx, final int delta) {
        final int lastIdx = grid.getChildren().size() - 1;
        int target = fromIdx + delta;
        if (target < 0) target = 0;
        if (target > lastIdx) target = lastIdx;
        if (target == fromIdx) return;
        grid.getChildren().get(target).requestFocus();
    }
}