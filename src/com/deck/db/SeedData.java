package com.deck.db;

import com.deck.model.AppEntry;
import com.deck.model.LaunchType;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inserts a handful of example tiles the very first time Deck runs.
 *
 * <p>Purely a developer convenience — real users would add their own via the
 * editor (Drop 4). We detect "first run" by checking whether the {@code apps}
 * table is empty AND a marker key hasn't been set in {@code settings}. That
 * two-condition check means seeding won't come back if the user deletes every
 * tile on purpose.
 */
public final class SeedData {

    /** Settings key that records we've already seeded once. */
    private static final String SEEDED_KEY = "seed.done";

    private SeedData() { }

    /** Seeds the DB with sample tiles if this is the very first run. */
    public static void seedIfEmpty() throws SQLException {
        if (alreadySeeded()) return;
        if (!appsTableEmpty()) {
            markSeeded();
            return;
        }
        insertSamples();
        markSeeded();
    }

    // ---- private helpers ---------------------------------------------------

    private static boolean alreadySeeded() throws SQLException {
        return com.deck.config.Settings.get(SEEDED_KEY) != null;
    }

    private static void markSeeded() throws SQLException {
        com.deck.config.Settings.set(SEEDED_KEY, "1");
    }

    private static boolean appsTableEmpty() throws SQLException {
        try (Statement st = Database.connection().createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM apps")) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static void insertSamples() throws SQLException {
        Dao.insertApp(new AppEntry(
                -1, "Trackoff", LaunchType.JAR,
                "C:\\Users\\ackermanb2\\Desktop\\Personal Projects\\Trackoff\\out\\trackoff.jar",
                null, null, null, 0));

        Dao.insertApp(new AppEntry(
                -1, "Google Doc Planner", LaunchType.URL,
                "https://docs.google.com/",
                null, null, null, 1));

        Dao.insertApp(new AppEntry(
                -1, "Wordle", LaunchType.URL,
                "https://www.nytimes.com/games/wordle/",
                null, null, null, 2));
    }
}