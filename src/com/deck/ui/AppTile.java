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
 * A single launcher tile, styled after an iOS home-screen app: a rounded
 * "squircle" icon with the app name on its own line underneath.
 *
 * <p>The tile itself draws nothing — no card, no panel. Only the icon has a
 * background, so the artwork <em>is</em> the tile. The supplied PNG is scaled
 * to <em>cover</em> the squircle and centre-cropped, exactly like iOS: an image
 * of any aspect ratio fills the shape edge to edge with no letterboxing, at the
 * cost of trimming the long side.
 *
 * <p>Structure is a three-level nest, and each level earns its place:
 * <pre>
 *   VBox        .tile        transparent; owns hover/focus/click
 *    ├ StackPane .tile-icon  border + drop shadow (outside the clip)
 *    │   └ StackPane         rounded clip; holds the image or letter
 *    └ Label     .tile-name  caption underneath
 * </pre>
 * The frame and the clip can't be the same node: a clip would cut off the very
 * border and shadow that make the icon read as a physical tile.
 *
 * <p>When {@link AppEntry#iconPath()} is null or missing we fill the squircle
 * with a colour derived from a stable hash of the name and centre the app's
 * initial — so "Trackoff" always looks the same.
 */
public final class AppTile {

    /** Grid cell width/height in pixels. */
    public static final double TILE_SIZE = 180.0;

    /** Icon squircle size — the label sits below it. */
    private static final double ICON_SIZE = 112.0;

    /**
     * Corner radius as a fraction of icon size. 22.37% is the ratio Apple's
     * icon grid uses; it reads as a squircle rather than a rounded square.
     */
    private static final double CORNER_RATIO = 0.2237;

    private static final double CORNER_RADIUS = ICON_SIZE * CORNER_RATIO;

    /** Cap on decoded image size — icons are never displayed above ICON_SIZE. */
    private static final double LOAD_SIZE = ICON_SIZE * 3;

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
        nameLabel.setMaxWidth(TILE_SIZE - 12);
        nameLabel.setWrapText(false);
        nameLabel.setEllipsisString("…");
        // maxWidth makes the Label node itself full-cell-width, and a Label's
        // text is left-aligned within its box by default — without this the
        // caption hangs off to the left of the icon it belongs to.
        nameLabel.setAlignment(Pos.CENTER);

        final VBox stack = shell(iconNode, nameLabel);
        stack.getStyleClass().add("tile");

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
     * Builds the trailing "+" tile. Uses the same icon-over-label shell as a
     * real tile so its squircle lines up with the row of app icons instead of
     * floating at a different height.
     */
    public static Node buildAddTile(final Runnable onClick) {
        final Label plus = new Label("+");
        plus.getStyleClass().add("add-tile-plus");
        plus.setAlignment(Pos.CENTER);

        final StackPane frame = iconFrame();
        frame.getStyleClass().add("add-tile");
        frame.getChildren().add(plus);

        final Label nameLabel = new Label("Add");
        nameLabel.getStyleClass().addAll("tile-name", "add-tile-name");

        final VBox stack = shell(frame, nameLabel);
        stack.getStyleClass().addAll("tile", "add-tile-shell");

        stack.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onClick != null) {
                onClick.run();
            }
        });

        return stack;
    }

    // ---- shared layout -----------------------------------------------------

    /** Icon above, caption below, pinned to the grid cell size. */
    private static VBox shell(final Node icon, final Label caption) {
        final VBox stack = new VBox(10, icon, caption);
        stack.setAlignment(Pos.TOP_CENTER);
        // Top padding centres the icon+label pair within the cell; the gap
        // below is what separates rows of captions from the next row's icons.
        stack.setPadding(new Insets(14, 4, 4, 4));
        stack.setPrefSize(TILE_SIZE, TILE_SIZE);
        stack.setMinSize(TILE_SIZE, TILE_SIZE);
        stack.setMaxSize(TILE_SIZE, TILE_SIZE);
        stack.setFocusTraversable(true);
        return stack;
    }

    /**
     * The bordered, shadowed squircle frame. Radius is set inline rather than in
     * CSS so it can never drift out of sync with {@link #CORNER_RADIUS}, which
     * also drives the clip geometry.
     */
    private static StackPane iconFrame() {
        final StackPane frame = new StackPane();
        frame.getStyleClass().add("tile-icon");
        frame.setMinSize(ICON_SIZE, ICON_SIZE);
        frame.setPrefSize(ICON_SIZE, ICON_SIZE);
        frame.setMaxSize(ICON_SIZE, ICON_SIZE);
        frame.setStyle("-fx-background-radius: " + CORNER_RADIUS + "px;"
                     + "-fx-border-radius: "     + CORNER_RADIUS + "px;");
        return frame;
    }

    // ---- icon rendering ----------------------------------------------------

    private static Node buildIcon(final AppEntry app) {
        final StackPane clipped = new StackPane();
        clipped.setMinSize(ICON_SIZE, ICON_SIZE);
        clipped.setPrefSize(ICON_SIZE, ICON_SIZE);
        clipped.setMaxSize(ICON_SIZE, ICON_SIZE);
        clipped.setClip(squircleClip());

        final Image img = loadIcon(app);
        if (img != null) {
            clipped.getChildren().add(coverView(img));
        } else {
            clipped.setStyle("-fx-background-color: " + toWebColor(colorForName(app.name())) + ";");
            clipped.getChildren().add(letterLabel(app.name()));
        }

        final StackPane frame = iconFrame();
        frame.getChildren().add(clipped);
        return frame;
    }

    private static Image loadIcon(final AppEntry app) {
        final Path iconPath = resolveIconPath(app);
        if (iconPath == null) return null;
        try {
            final Image img = new Image(iconPath.toUri().toString(),
                    LOAD_SIZE, LOAD_SIZE, true, true);
            return (img.isError() || img.getWidth() <= 0) ? null : img;
        } catch (Exception ignored) {
            return null;   // fall through to the letter fallback
        }
    }

    /**
     * Scales an image to cover the squircle, overflowing on the long axis so
     * the crop happens at the clip.
     *
     * <p>Only one fit dimension is set: with {@code preserveRatio}, JavaFX
     * derives the other from the aspect ratio. Constraining the <em>short</em>
     * side to the icon size is what guarantees full coverage — constraining the
     * long side (or setting both) would letterbox instead.
     */
    private static ImageView coverView(final Image img) {
        final ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        if (img.getWidth() >= img.getHeight()) {
            iv.setFitHeight(ICON_SIZE);   // landscape: height fits, width spills
        } else {
            iv.setFitWidth(ICON_SIZE);    // portrait: width fits, height spills
        }
        return iv;
    }

    private static Rectangle squircleClip() {
        final Rectangle r = new Rectangle(ICON_SIZE, ICON_SIZE);
        // arc* is the full diameter of the corner, hence 2x the radius.
        r.setArcWidth(CORNER_RADIUS * 2);
        r.setArcHeight(CORNER_RADIUS * 2);
        return r;
    }

    private static Path resolveIconPath(final AppEntry app) {
        final String raw = app.iconPath();
        if (raw == null || raw.isBlank()) return null;
        final Path candidate = Path.of(raw).isAbsolute()
                ? Path.of(raw)
                : AppPaths.iconsFolder().resolve(raw);
        return Files.exists(candidate) ? candidate : null;
    }

    private static Label letterLabel(final String name) {
        final String initial = (name == null || name.isEmpty())
                ? "?"
                : name.substring(0, 1).toUpperCase();
        final Label letter = new Label(initial);
        letter.getStyleClass().add("tile-letter");
        letter.setPrefSize(ICON_SIZE, ICON_SIZE);
        letter.setAlignment(Pos.CENTER);
        return letter;
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

    /** {@code #rrggbb} for use in an inline {@code -fx-background-color}. */
    private static String toWebColor(final Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed()   * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue()  * 255));
    }
}
