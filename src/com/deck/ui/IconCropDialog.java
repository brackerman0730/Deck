package com.deck.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Square-crop picker shown after the user chooses an image for a tile.
 *
 * <p>Tiles are square squircles, so the selection is locked to a square: drag
 * inside it to reposition, drag a corner to resize. That removes the whole
 * class of "why is my icon squashed" problems — the alternative, a free
 * rectangle, can only be resolved later by stretching or cropping anyway.
 *
 * <p>Cropping happens against the <em>original</em> image at full resolution,
 * not the scaled-down view, so a small selection out of a large photo keeps its
 * detail. The result is emitted at {@link #OUTPUT_SIZE}px square.
 *
 * <p>Modal and blocking: {@link #open(Window, Image)} returns the cropped image,
 * or empty if the user cancelled.
 */
public final class IconCropDialog {

    /** Edge length of the emitted icon. 256 is comfortably above tile size. */
    private static final double OUTPUT_SIZE = 256;

    /** Edge length of the square editing viewport. */
    private static final double VIEW = 420;

    /** Live preview size — matches the real tile icon. */
    private static final double PREVIEW = 112;
    private static final double PREVIEW_RADIUS = PREVIEW * 0.2237;

    /** Grab radius, in view pixels, for the corner resize handles. */
    private static final double HANDLE = 18;

    /** Smallest allowed selection, in view pixels. */
    private static final double MIN_SELECTION = 40;

    private final Stage stage;
    private final Image source;
    private final Canvas overlay = new Canvas(VIEW, VIEW);
    private final ImageView previewView = new ImageView();

    /** Where the scaled image actually sits inside the viewport. */
    private final double drawX, drawY, drawW, drawH, scale;

    /** Selection square, in viewport coordinates. */
    private double selX, selY, selSize;

    /** Drag bookkeeping. */
    private int activeCorner = -1;          // 0=NW 1=NE 2=SE 3=SW, -1 = none
    private boolean moving;
    private double grabDX, grabDY;

    private Image result;

    // ---- entry point -------------------------------------------------------

    /**
     * Opens the cropper and blocks until the user confirms or cancels.
     *
     * @param owner  parent window for modality
     * @param source the full-resolution image to crop
     * @return the cropped square image, or empty if cancelled
     */
    public static Optional<Image> open(final Window owner, final Image source) {
        final IconCropDialog d = new IconCropDialog(owner, source);
        d.stage.showAndWait();
        return Optional.ofNullable(d.result);
    }

    // ---- construction ------------------------------------------------------

    private IconCropDialog(final Window owner, final Image source) {
        this.source = source;

        // Fit the image inside the viewport, preserving aspect. Upscaling small
        // images is allowed on purpose — it makes fine positioning possible.
        this.scale = Math.min(VIEW / source.getWidth(), VIEW / source.getHeight());
        this.drawW = source.getWidth()  * scale;
        this.drawH = source.getHeight() * scale;
        this.drawX = (VIEW - drawW) / 2.0;
        this.drawY = (VIEW - drawH) / 2.0;

        // Start with the largest centred square.
        this.selSize = Math.min(drawW, drawH);
        this.selX = drawX + (drawW - selSize) / 2.0;
        this.selY = drawY + (drawH - selSize) / 2.0;

        this.stage = new Stage();
        this.stage.initModality(Modality.WINDOW_MODAL);
        this.stage.initOwner(owner);
        this.stage.setTitle("Crop icon");
        this.stage.setResizable(false);

        buildScene();
        wireMouse();
        redraw();
    }

    private void buildScene() {
        final ImageView base = new ImageView(source);
        base.setFitWidth(drawW);
        base.setFitHeight(drawH);
        base.setPreserveRatio(true);
        base.setSmooth(true);

        final StackPane viewport = new StackPane(base, overlay);
        viewport.setMinSize(VIEW, VIEW);
        viewport.setPrefSize(VIEW, VIEW);
        viewport.setMaxSize(VIEW, VIEW);
        viewport.getStyleClass().add("crop-viewport");

        // Live preview, clipped to the same squircle the tile uses.
        previewView.setFitWidth(PREVIEW);
        previewView.setFitHeight(PREVIEW);
        previewView.setPreserveRatio(true);
        previewView.setSmooth(true);
        final StackPane previewBox = new StackPane(previewView);
        previewBox.setMinSize(PREVIEW, PREVIEW);
        previewBox.setPrefSize(PREVIEW, PREVIEW);
        previewBox.setMaxSize(PREVIEW, PREVIEW);
        final Rectangle clip = new Rectangle(PREVIEW, PREVIEW);
        clip.setArcWidth(PREVIEW_RADIUS * 2);
        clip.setArcHeight(PREVIEW_RADIUS * 2);
        previewBox.setClip(clip);
        previewBox.getStyleClass().add("crop-preview");

        final Label previewLabel = new Label("Preview");
        previewLabel.getStyleClass().add("form-label-secondary");

        final VBox side = new VBox(10, previewLabel, previewBox);
        side.setAlignment(Pos.TOP_CENTER);

        final Label hint = new Label(
                "Drag inside the square to move it, or drag a corner to resize. "
              + "The selection is locked square to match the tile shape.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(VIEW);

        final Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("button-secondary");
        cancel.setOnAction(e -> { result = null; stage.close(); });

        final Button reset = new Button("Reset");
        reset.getStyleClass().add("button-secondary");
        reset.setOnAction(e -> {
            selSize = Math.min(drawW, drawH);
            selX = drawX + (drawW - selSize) / 2.0;
            selY = drawY + (drawH - selSize) / 2.0;
            redraw();
        });

        final Button use = new Button("Use image");
        use.getStyleClass().add("button-primary");
        use.setDefaultButton(true);
        use.setOnAction(e -> { result = renderCrop(); stage.close(); });

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox buttons = new HBox(12, reset, spacer, cancel, use);
        buttons.setAlignment(Pos.CENTER_LEFT);

        final HBox body = new HBox(24, viewport, side);
        body.setAlignment(Pos.TOP_LEFT);

        final VBox root = new VBox(16, body, hint, buttons);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("app-root");

        final Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/com/deck/resources/styles.css").toExternalForm());
        stage.setScene(scene);
    }

    // ---- interaction -------------------------------------------------------

    private void wireMouse() {
        overlay.setOnMouseMoved(e -> overlay.setCursor(cursorFor(e.getX(), e.getY())));

        overlay.setOnMousePressed(e -> {
            activeCorner = cornerAt(e.getX(), e.getY());
            moving = activeCorner < 0 && insideSelection(e.getX(), e.getY());
            grabDX = e.getX() - selX;
            grabDY = e.getY() - selY;
        });

        overlay.setOnMouseDragged(e -> {
            if (activeCorner >= 0) {
                resizeFromCorner(activeCorner, e.getX(), e.getY());
            } else if (moving) {
                selX = clamp(e.getX() - grabDX, drawX, drawX + drawW - selSize);
                selY = clamp(e.getY() - grabDY, drawY, drawY + drawH - selSize);
            }
            redraw();
        });

        overlay.setOnMouseReleased(e -> { activeCorner = -1; moving = false; });
    }

    /**
     * Resizes the square by dragging {@code corner}, anchoring the diagonally
     * opposite corner. The new edge is the larger of the two deltas so the
     * square tracks the pointer on whichever axis moved further, then it's
     * clamped so the selection can't leave the image.
     */
    private void resizeFromCorner(final int corner, final double mx, final double my) {
        final double ax = (corner == 0 || corner == 3) ? selX + selSize : selX;  // anchor x
        final double ay = (corner == 0 || corner == 1) ? selY + selSize : selY;  // anchor y

        double size = Math.max(Math.abs(mx - ax), Math.abs(my - ay));
        size = Math.max(size, MIN_SELECTION);

        // Clamp against the image edges in whichever directions we're growing.
        final boolean growLeft = mx < ax;
        final boolean growUp   = my < ay;
        size = Math.min(size, growLeft ? ax - drawX : drawX + drawW - ax);
        size = Math.min(size, growUp   ? ay - drawY : drawY + drawH - ay);
        size = Math.max(size, MIN_SELECTION);

        selSize = size;
        selX = growLeft ? ax - size : ax;
        selY = growUp   ? ay - size : ay;
    }

    private boolean insideSelection(final double x, final double y) {
        return x >= selX && x <= selX + selSize && y >= selY && y <= selY + selSize;
    }

    /** Index of the corner near (x,y), or -1. */
    private int cornerAt(final double x, final double y) {
        final double[][] pts = {
                {selX, selY}, {selX + selSize, selY},
                {selX + selSize, selY + selSize}, {selX, selY + selSize}};
        for (int i = 0; i < pts.length; i++) {
            if (Math.hypot(x - pts[i][0], y - pts[i][1]) <= HANDLE) return i;
        }
        return -1;
    }

    private Cursor cursorFor(final double x, final double y) {
        return switch (cornerAt(x, y)) {
            case 0 -> Cursor.NW_RESIZE;
            case 1 -> Cursor.NE_RESIZE;
            case 2 -> Cursor.SE_RESIZE;
            case 3 -> Cursor.SW_RESIZE;
            default -> insideSelection(x, y) ? Cursor.MOVE : Cursor.DEFAULT;
        };
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ---- painting ----------------------------------------------------------

    private void redraw() {
        final GraphicsContext g = overlay.getGraphicsContext2D();
        g.clearRect(0, 0, VIEW, VIEW);

        // Dim everything outside the selection, as four bands around it.
        g.setFill(Color.rgb(9, 11, 15, 0.66));
        g.fillRect(0, 0, VIEW, selY);
        g.fillRect(0, selY + selSize, VIEW, VIEW - (selY + selSize));
        g.fillRect(0, selY, selX, selSize);
        g.fillRect(selX + selSize, selY, VIEW - (selX + selSize), selSize);

        // Selection outline.
        g.setStroke(Color.WHITE);
        g.setLineWidth(2);
        g.strokeRect(selX, selY, selSize, selSize);

        // Thirds guides — cheap alignment aid while dragging.
        g.setStroke(Color.rgb(255, 255, 255, 0.25));
        g.setLineWidth(1);
        for (int i = 1; i <= 2; i++) {
            final double o = selSize * i / 3.0;
            g.strokeLine(selX + o, selY, selX + o, selY + selSize);
            g.strokeLine(selX, selY + o, selX + selSize, selY + o);
        }

        // Corner handles.
        g.setFill(Color.web("#4FD1FF"));
        final double h = 10;
        for (double[] p : new double[][]{
                {selX, selY}, {selX + selSize, selY},
                {selX + selSize, selY + selSize}, {selX, selY + selSize}}) {
            g.fillRect(p[0] - h / 2, p[1] - h / 2, h, h);
        }

        updatePreview();
    }

    private void updatePreview() {
        previewView.setImage(source);
        previewView.setViewport(sourceRect());
    }

    // ---- output ------------------------------------------------------------

    /** Selection mapped back onto the original image's pixel grid. */
    private Rectangle2D sourceRect() {
        final double sx = (selX - drawX) / scale;
        final double sy = (selY - drawY) / scale;
        final double ss = selSize / scale;
        // Guard against rounding pushing us a hair outside the source bounds.
        final double w = Math.min(ss, source.getWidth()  - sx);
        final double h = Math.min(ss, source.getHeight() - sy);
        return new Rectangle2D(Math.max(0, sx), Math.max(0, sy),
                Math.max(1, w), Math.max(1, h));
    }

    /**
     * Crops and rescales in one step: an {@link ImageView} with a viewport set
     * to the selection, sized to the output, then snapshotted. Doing it this
     * way gets JavaFX's smooth filtering for free rather than hand-rolling a
     * resampler over the pixel buffer.
     */
    private Image renderCrop() {
        final ImageView iv = new ImageView(source);
        iv.setViewport(sourceRect());
        iv.setFitWidth(OUTPUT_SIZE);
        iv.setFitHeight(OUTPUT_SIZE);
        iv.setPreserveRatio(false);   // viewport is already square
        iv.setSmooth(true);

        final SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return iv.snapshot(params, new WritableImage(
                (int) OUTPUT_SIZE, (int) OUTPUT_SIZE));
    }
}
