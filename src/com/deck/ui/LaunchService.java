package com.deck.ui;

import com.deck.model.AppEntry;

import javafx.application.Platform;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dispatches an {@link AppEntry} launch to the right OS-level mechanism.
 *
 * <p>Each launch type routes differently:
 * <ul>
 *   <li>{@code URL}    — {@link Desktop#browse(URI)} in the default browser</li>
 *   <li>{@code EXE}    — {@link ProcessBuilder} with the target as the program</li>
 *   <li>{@code JAR}    — {@code javaw -jar <target> [args]} (no console window)</li>
 *   <li>{@code SCRIPT} — {@code .ps1} runs under PowerShell with
 *       {@code -ExecutionPolicy Bypass}; {@code .bat}/{@code .cmd} runs via
 *       {@code cmd /c}</li>
 *   <li>{@code COMPOSITE} — startup command via {@code cmd /c}, then a fixed
 *       delay, then {@link Desktop#browse(URI)} on the configured URL</li>
 * </ul>
 *
 * <p>All launches run on a daemon background thread so the FX thread never
 * blocks. The optional {@code onDone} callback is dispatched back onto the FX
 * thread via {@link Platform#runLater(Runnable)} — safe to call UI code from it.
 *
 * <p>Args are split on whitespace only. Phase 1 doesn't attempt quoted-string
 * parsing; if you need spaces inside a single arg, that's a Phase 2 concern.
 */
public final class LaunchService {

    private LaunchService() { }

    /**
     * Fires a launch asynchronously.
     *
     * @param app     the app to launch
     * @param onDone  callback receiving success/failure detail; runs on FX thread.
     *                Nullable if the caller doesn't care about feedback.
     */
    public static void launch(final AppEntry app, final Consumer<LaunchResult> onDone) {
        final Thread t = new Thread(() -> {
            final LaunchResult result = launchSync(app);
            if (onDone != null) {
                Platform.runLater(() -> onDone.accept(result));
            }
        }, "deck-launch-" + app.id());
        t.setDaemon(true);
        t.start();
    }

    // ---- synchronous dispatch (background thread) --------------------------

    private static LaunchResult launchSync(final AppEntry app) {
        try {
            switch (app.launchType()) {
                case URL       -> launchUrl(app);
                case EXE       -> launchExe(app);
                case JAR       -> launchJar(app);
                case SCRIPT    -> launchScript(app);
                case COMPOSITE -> launchComposite(app);
            }
            return LaunchResult.ok("Launched " + app.name());
        } catch (Exception e) {
            return LaunchResult.error(app.name() + " — " + e.getMessage());
        }
    }

    // ---- per-type handlers -------------------------------------------------

    private static void launchUrl(final AppEntry app) throws IOException {
        browse(app.launchTarget());
    }

    /**
     * Composite: fire the startup command, wait, then open the URL.
     *
     * <p>Already running on the daemon launch thread, so sleeping here blocks
     * nothing the user can see. The startup command goes through {@code cmd /c}
     * rather than our whitespace arg splitter so Windows parses the command
     * line itself — quoted paths with spaces survive.
     *
     * <p>The startup process is fire-and-forget: Deck doesn't check whether it
     * actually came up, it just waits the configured delay and browses. A dead
     * server surfaces as a browser error, not a Deck error.
     */
    private static void launchComposite(final AppEntry app) throws IOException {
        final String url = app.compositeUrl();
        if (url == null || url.isBlank()) {
            throw new IOException("No URL configured for this composite tile");
        }

        final String startup = app.compositeStartup();
        if (startup != null && !startup.isBlank()) {
            spawn(List.of("cmd", "/c", startup), app.workingDir());
        }

        final int delayMs = app.compositeDelayMs();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                // Preserve the flag for anything up the stack, then bail out
                // rather than opening a browser against a half-started server.
                Thread.currentThread().interrupt();
                throw new IOException("Launch interrupted while waiting for startup");
            }
        }

        browse(url);
    }

    private static void browse(final String url) throws IOException {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Desktop browsing not supported on this OS");
        }
        Desktop.getDesktop().browse(URI.create(url));
    }

    private static void launchExe(final AppEntry app) throws IOException {
        requireExistingFile(app.launchTarget());
        final List<String> cmd = new ArrayList<>();
        cmd.add(app.launchTarget());
        cmd.addAll(splitArgs(app.launchArgs()));
        spawn(cmd, app.workingDir());
    }

    private static void launchJar(final AppEntry app) throws IOException {
        requireExistingFile(app.launchTarget());
        final List<String> cmd = new ArrayList<>();
        cmd.add("javaw");  // console-less java for GUI JARs
        cmd.add("-jar");
        cmd.add(app.launchTarget());
        cmd.addAll(splitArgs(app.launchArgs()));
        spawn(cmd, app.workingDir());
    }

    private static void launchScript(final AppEntry app) throws IOException {
        requireExistingFile(app.launchTarget());
        final String target = app.launchTarget();
        final String lower  = target.toLowerCase();
        final List<String> cmd = new ArrayList<>();
        if (lower.endsWith(".ps1")) {
            cmd.add("powershell");
            cmd.add("-ExecutionPolicy");
            cmd.add("Bypass");
            cmd.add("-File");
            cmd.add(target);
        } else if (lower.endsWith(".bat") || lower.endsWith(".cmd")) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(target);
        } else {
            throw new IOException("Unsupported script extension: " + target);
        }
        cmd.addAll(splitArgs(app.launchArgs()));
        spawn(cmd, app.workingDir());
    }

    // ---- helpers -----------------------------------------------------------

    private static void spawn(final List<String> cmd, final String workingDir) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDir != null && !workingDir.isBlank()) {
            final File dir = new File(workingDir);
            if (dir.isDirectory()) {
                pb.directory(dir);
            }
        }
        // We intentionally don't inherit IO — Deck doesn't care about the
        // child's stdout/stderr, and inheriting can keep zombie handles.
        pb.start();
    }

    private static void requireExistingFile(final String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("No target path configured");
        }
        if (!new File(path).exists()) {
            throw new IOException("File not found: " + path);
        }
    }

    private static List<String> splitArgs(final String args) {
        if (args == null || args.isBlank()) return List.of();
        return Arrays.asList(args.trim().split("\\s+"));
    }

    // ---- result type -------------------------------------------------------

    /**
     * Outcome of a launch attempt, delivered to the caller's callback.
     * {@code message} is always human-friendly and safe to display in a toast.
     */
    public record LaunchResult(boolean success, String message) {
        public static LaunchResult ok(String m)    { return new LaunchResult(true, m); }
        public static LaunchResult error(String m) { return new LaunchResult(false, m); }
    }
}