package com.deck.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates and removes the "launch Deck when Windows starts" shortcut.
 *
 * <p>Mechanism: a {@code Deck.lnk} in the per-user Startup folder
 * ({@code %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\}). Chosen
 * over a {@code HKCU\...\Run} registry key because it needs no admin rights,
 * shows up in Task Manager's Startup tab so the user can disable it there too,
 * and deleting the file is a complete uninstall with no orphaned state.
 *
 * <p>The shortcut itself is built by {@link WindowsShortcuts} — the same code
 * that builds the Desktop shortcut, so both launch Deck identically (via
 * {@code javaw}, no console window).
 *
 * <p><b>Disk is the source of truth.</b> {@link #isEnabled()} checks for the
 * actual file rather than trusting the settings row, so a shortcut the user
 * deleted by hand (or via Task Manager) is reported accurately.
 */
public final class WindowsAutostart {

    private static final String SHORTCUT_NAME = "Deck.lnk";

    private WindowsAutostart() { }

    /** The Startup-folder path we install into. */
    public static Path shortcutPath() {
        final String appData = System.getenv("APPDATA");
        final Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData)
                : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
        return base.resolve(Paths.get(
                "Microsoft", "Windows", "Start Menu", "Programs", "Startup", SHORTCUT_NAME));
    }

    /** True when the Startup shortcut currently exists on disk. */
    public static boolean isEnabled() {
        return Files.isRegularFile(shortcutPath());
    }

    /** Turns autostart on or off. Safe to call when already in that state. */
    public static void setEnabled(final boolean enabled) throws IOException {
        if (enabled) {
            // Minimised: nothing should steal focus while you're still logging in.
            WindowsShortcuts.createShortcut(shortcutPath(),
                    WindowsShortcuts.WINDOW_MINIMIZED, "Deck — starts with Windows");
        } else {
            Files.deleteIfExists(shortcutPath());
        }
    }
}
