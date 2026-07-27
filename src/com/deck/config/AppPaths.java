package com.deck.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem locations Deck uses at runtime.
 *
 * <p>Everything lives under {@code %USERPROFILE%\.deck\}. The folder is created
 * on first run by {@link #ensureDataFolder()}. All paths are absolute so the
 * app doesn't care about the working directory it was launched from.
 */
public final class AppPaths {

    private AppPaths() { }

    /** Root data folder — {@code %USERPROFILE%\.deck\}. */
    public static Path dataFolder() {
        return Paths.get(System.getProperty("user.home"), ".deck");
    }

    /** SQLite database file — {@code %USERPROFILE%\.deck\deck.db}. */
    public static Path databaseFile() {
        return dataFolder().resolve("deck.db");
    }

    /** JDBC URL for the SQLite database. */
    public static String databaseUrl() {
        return "jdbc:sqlite:" + databaseFile().toAbsolutePath();
    }

    /** Folder holding copied icon PNGs for each app tile. */
    public static Path iconsFolder() {
        return dataFolder().resolve("icons");
    }

    /**
     * Creates the data folder and icons subfolder if they don't exist.
     * Safe to call every startup.
     */
    public static void ensureDataFolder() throws IOException {
        Files.createDirectories(dataFolder());
        Files.createDirectories(iconsFolder());
    }
}