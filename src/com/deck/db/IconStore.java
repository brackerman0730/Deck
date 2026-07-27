package com.deck.db;

import com.deck.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Thin filesystem helper for the icons folder.
 *
 * <p>Every tile that has an icon gets a copy of the user's chosen PNG under
 * {@code %USERPROFILE%\.deck\icons\<uuid>.png}. Storing our own copy means
 * the original source can be moved, renamed, or deleted without breaking
 * the tile.
 *
 * <p>The DB persists only the filename (not the absolute path), so this class
 * and {@link com.deck.ui.AppTile} agree on that convention. Absolute paths
 * still work for backward compatibility (see {@code AppTile.resolveIconPath}).
 */
public final class IconStore {

    private IconStore() { }

    /**
     * Copies the given PNG into the icons folder under a new UUID filename.
     *
     * @return the generated filename (not an absolute path) — persist this
     *         value in {@code AppEntry.iconPath()}.
     */
    public static String copyIntoStore(final Path source) throws IOException {
        if (source == null || !Files.exists(source)) {
            throw new IOException("Icon source does not exist: " + source);
        }
        final String filename = UUID.randomUUID() + ".png";
        final Path dest = AppPaths.iconsFolder().resolve(filename);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    /**
     * Best-effort delete of a stored icon. Silently ignores missing files
     * and IO errors — orphan icons are harmless and can be GC'd later.
     */
    public static void deleteFromStore(final String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Files.deleteIfExists(AppPaths.iconsFolder().resolve(filename));
        } catch (IOException ignored) {
            // Orphan icon files are harmless.
        }
    }
}