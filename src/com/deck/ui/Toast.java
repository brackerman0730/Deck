package com.deck.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Bottom-of-window transient notification.
 *
 * <p>Renders a rounded pill 40px above the bottom edge, fades in, holds
 * briefly, fades out, and detaches from the container. Non-blocking — any
 * number of toasts can stack because they don't interact with each other,
 * though in practice we only ever show one at a time.
 *
 * <p>The overlay container must be a {@link StackPane} so we can attach with
 * {@link StackPane#setAlignment(javafx.scene.Node, Pos)}. All toast labels are
 * mouse-transparent, so they never intercept clicks on tiles below.
 */
public final class Toast {

    private static final Duration FADE_IN  = Duration.millis(180);
    private static final Duration VISIBLE  = Duration.millis(2200);
    private static final Duration FADE_OUT = Duration.millis(280);

    private Toast() { }

    /** Shows a success (accent-cyan) toast. */
    public static void success(final StackPane container, final String message) {
        show(container, message, false);
    }

    /** Shows an error (accent-red) toast. */
    public static void error(final StackPane container, final String message) {
        show(container, message, true);
    }

    // ---- private -----------------------------------------------------------

    private static void show(final StackPane container,
                             final String message,
                             final boolean isError) {
        final Label label = new Label(message);
        label.getStyleClass().addAll("toast", isError ? "toast-error" : "toast-success");
        label.setMouseTransparent(true);
        label.setOpacity(0);

        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
        StackPane.setMargin(label, new Insets(0, 0, 40, 0));

        container.getChildren().add(label);

        final FadeTransition in = new FadeTransition(FADE_IN, label);
        in.setFromValue(0);
        in.setToValue(1);

        final PauseTransition pause = new PauseTransition(VISIBLE);

        final FadeTransition out = new FadeTransition(FADE_OUT, label);
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(e -> container.getChildren().remove(label));

        new SequentialTransition(in, pause, out).play();
    }
}