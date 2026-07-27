package com.deck.ui;

import com.deck.config.AppPaths;
import com.deck.model.AppEntry;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A single 180×180 launcher tile: icon on top, name below.
 *
 * <p>Tile visuals are 100% CSS-driven — this class only wires the DOM. Hover
 * and focus states come from {@code styles.css}. Click and context-menu
 * handlers are passed in by the caller, keeping this class free of any
 * launch-service or dialog coupling.
 *
 * <p>When {@link AppEntry#iconPath()} is null or the file doesn't exist, we
 * render a deterministic colored square with the app's initial — the hue is
 * derived from a stable hash of the name so "Trackoff" always looks the same.
 */
public final class AppTile {

    /** Grid tile width/height in pixels — matches design tokens. */
    public static final double TILE_SIZE = 180.0;

    /** Icon area size in pixels — leaves room for the name label below. */
    private static final double ICON_SIZE = 96.0;

    private AppTile() { }

    /**
     * Builds a tile node for the given app entry.
     *
     * @param app        the app to render
     * @param onClick    fired on primary-button click (or {@code null} to disable)
     * @param onContext  fired on right-click / keyboard context request. Receives
     *                   the raw {@link ContextMenuEvent} so callers can show a
     *                   {@link javafx.scene.control.ContextMenu} at the event's
     *                   screen coordinates. {@code null} to disable.
     */
    public static Node build(final AppEntry app,
                             final Runnable onClick,
                             final Consumer<ContextMenuEvent> onContext) {
        final Node iconNode = buildIcon(app);

        final Label nameLabel = new Label(app.name());
        nameLabel.getStyleClass().add("tile-name");
        nameLabel.setMaxWidth(TILE_SIZE - 24);
        nameLabel.setWrapText(false);
        nameLabel.setEllipsisString("…");

        final VBox stack = new VBox(12, iconNode, nameLabel);
        stack.setAlignment(Pos.CENTER);
        stack.setPadding(new Insets(20));
        stack.setPrefSize(TILE_SIZE, TILE_SIZE);
        stack.setMinSize(TILE_SIZE, TILE_SIZE);
        stack.setMaxSize(TILE_SIZE, TILE_SIZE);
        stack.getStyleClass().add("tile");
        stack.setFocusTraversable(true);

        stack.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onClick != null) {
                onClick.run();
            }
        });

        stack.setOnContextMenuRequested(e -> {
            if (onContext != null) {
                onContext.accept(e);
            }
        });

        return stack;
    }

    /**
     * Builds the trailing "+" tile. Uses a {@link Label} rather than a
     * {@link javafx.scene.text.Text} node so the "+" centers by content box
     * instead of glyph baseline — {@code Text} anchors to the
     * descender-inclusive baseline, which sinks the glyph below geometric
     * center in a StackPane.
     */
    public static Node buildAddTile(final Runnable onClick) {
        final Label plus = new Label("+");
        plus.getStyleClass().add("add-tile-plus");
        plus.setPrefSize(TILE_SIZE, TILE_SIZE);
        plus.setAlignment(Pos.CENTER);

        final StackPane pane = new StackPane(plus);
        pane.setPrefSize(TILE_SIZE, TILE_SIZE);
        pane.setMinSize(TILE_SIZE, TILE_SIZE);
        pane.setMaxSize(TILE_SIZE, TILE_SIZE);
        pane.getStyleClass().addAll("tile", "add-tile");
        pane.setFocusTraversable(true);

        pane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onClick != null) {
                onClick.run();
            }
        });

        return pane;
    }

    // ---- icon rendering ----------------------------------------------------

    private static Node buildIcon(final AppEntry app) {
        final Path iconPath = resolveIconPath(app);
        if (iconPath != null) {
            try {
                final Image img = new Image(iconPath.toUri().toString(),
                        ICON_SIZE, ICON_SIZE, true, true);
                if (!img.isError()) {
                    final ImageView iv = new ImageView(img);
                    iv.setFitWidth(ICON_SIZE);
                    iv.setFitHeight(ICON_SIZE);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    return iv;
                }
            } catch (Exception ignored) {
                // fall through to letter fallback
            }
        }
        return buildLetterFallback(app.name());
    }

    private static Path resolveIconPath(final AppEntry app) {
        final String raw = app.iconPath();
        if (raw == null || raw.isBlank()) return null;
        final Path candidate = Path.of(raw).isAbsolute()
                ? Path.of(raw)
                : AppPaths.iconsFolder().resolve(raw);
        return Files.exists(candidate) ? candidate : null;
    }

    private static Node buildLetterFallback(final String name) {
        final String initial = (name == null || name.isEmpty())
                ? "?"
                : name.substring(0, 1).toUpperCase();

        final Rectangle bg = new Rectangle(ICON_SIZE, ICON_SIZE);
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        bg.setFill(colorForName(name));

        final Label letter = new Label(initial);
        letter.getStyleClass().add("tile-letter");
        letter.setPrefSize(ICON_SIZE, ICON_SIZE);
        letter.setAlignment(Pos.CENTER);

        final StackPane pane = new StackPane(bg, letter);
        pane.setPrefSize(ICON_SIZE, ICON_SIZE);
        pane.setMinSize(ICON_SIZE, ICON_SIZE);
        pane.setMaxSize(ICON_SIZE, ICON_SIZE);
        return pane;
    }

    private static Color colorForName(final String name) {
        if (name == null || name.isEmpty()) {
            return Color.web("#2A2E38");
        }
        final int h = Math.abs(name.hashCode());
        final double hue = (h % 360);
        final double sat = 0.45;
        final double bri = 0.75;
        return Color.hsb(hue, sat, bri);
    }
}