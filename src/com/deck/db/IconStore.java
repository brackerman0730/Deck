package com.deck.db;

import com.deck.config.AppPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Thin filesystem helper for the icons folder.
 *
 * <p>Every tile that has an icon gets its own copy under
 * {@code %USERPROFILE%\.deck\icons\<uuid>.png}, so the original source can be
 * moved, renamed, or deleted without breaking the tile. Icons are always
 * re-encoded to PNG on the way in — the editor accepts PNG and JPEG, and the
 * user's crop has no source file to copy in any case.
 *
 * <p>The DB persists only the filename (not the absolute path), so this class
 * and {@link com.deck.ui.AppTile} agree on that convention. Absolute paths
 * still work for backward compatibility (see {@code AppTile.resolveIconPath}).
 */
public final class IconStore {

    private IconStore() { }

    /**
     * Encodes an image as PNG into the icons folder under a new UUID filename.
     *
     * <p>Re-encoding rather than copying is what lets the editor accept JPEG
     * (and anything else JavaFX can decode) while keeping exactly one format on
     * disk — and it's required anyway, since a cropped image has no source file
     * to copy. PNG keeps the alpha channel that a cropped, rounded icon needs.
     *
     * @return the generated filename (not an absolute path).
     */
    public static String savePng(final BufferedImage image) throws IOException {
        if (image == null) {
            throw new IOException("No image to save");
        }
        final String filename = UUID.randomUUID() + ".png";
        final Path dest = AppPaths.iconsFolder().resolve(filename);
        if (!ImageIO.write(image, "png", dest.toFile())) {
            throw new IOException("No PNG encoder available");
        }
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