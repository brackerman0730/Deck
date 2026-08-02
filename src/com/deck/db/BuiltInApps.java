package com.deck.db;

import com.deck.model.AppEntry;
import com.deck.model.LaunchType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

/**
 * Keeps tiles for the sibling projects (Trackoff, GPA Calculator, RegiQuiz) in
 * the DB.
 *
 * <p>Runs on every startup, unlike {@link SeedData} which only fires into an
 * empty database. That matters because these tiles were added after the DB
 * already existed — a first-run-only seed would never have reached it.
 *
 * <p>Idempotent in two directions:
 * <ul>
 *   <li>A tile whose name is already present is left alone, so restarting Deck
 *       never duplicates and never stomps edits the user made by hand.</li>
 *   <li><em>Except</em> when the existing tile points at a file that isn't
 *       there — that's a broken tile (the original seed shipped a Trackoff
 *       entry pointing at {@code C:\Users\ackermanb2\...}), so we repair the
 *       launch fields in place while preserving id, sort order and icon.</li>
 * </ul>
 *
 * <p>Tiles whose target doesn't exist on this machine are skipped rather than
 * inserted broken, and a line is written to stderr saying so.
 */
public final class BuiltInApps {

    private BuiltInApps() { }

    /**
     * Where the sibling project folders live — the parent of Deck's own project
     * root. Deck is always started from its root (both {@code run.ps1} and the
     * generated {@code Deck.bat} set the working directory), so this resolves
     * to the shared {@code Projects\} folder.
     */
    private static Path projectsFolder() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** Inserts any missing built-in tile and repairs any broken one. */
    public static void sync() throws SQLException {
        final Path projects = projectsFolder();
        if (projects == null) {
            System.err.println("[BuiltInApps] Can't resolve the projects folder — skipping tile sync.");
            return;
        }

        final Path trackoff = projects.resolve("Trackoff");
        final Path gpa      = projects.resolve("GPA-Calc-GUI");
        final Path regiquiz = projects.resolve("Web-Programming-RegiQuiz-Web-Application")
                                      .resolve("RegiQuiz");

        // Trackoff and the GPA calculator are plain PowerShell launches; their
        // run.ps1 scripts resolve out\ and lib\ relatively, hence workingDir.
        upsert(AppEntry.simple(-1, "Trackoff", LaunchType.SCRIPT,
                        trackoff.resolve("run.ps1").toString(),
                        null, trackoff.toString(), null, 0),
                trackoff.resolve("run.ps1"));

        upsert(AppEntry.simple(-1, "GPA Calculator", LaunchType.SCRIPT,
                        gpa.resolve("run.ps1").toString(),
                        null, gpa.toString(), null, 0),
                gpa.resolve("run.ps1"));

        // RegiQuiz is a Node web app: start the server, give it a moment to
        // bind the port, then open the browser.
        final String regiUrl = "http://localhost:3000";
        upsert(new AppEntry(-1, "RegiQuiz", LaunchType.COMPOSITE,
                        regiUrl, null, regiquiz.toString(), null, 0,
                        "node server.js", 4000, regiUrl),
                regiquiz.resolve("server.js"));
    }

    // ---- internals ---------------------------------------------------------

    /**
     * Inserts {@code desired} if absent, repairs it if present-but-broken, and
     * otherwise leaves it untouched.
     *
     * @param requiredFile file that must exist for this tile to be usable
     */
    private static void upsert(final AppEntry desired, final Path requiredFile) throws SQLException {
        if (!Files.isRegularFile(requiredFile)) {
            System.err.println("[BuiltInApps] Skipping \"" + desired.name()
                    + "\" — not found: " + requiredFile);
            return;
        }

        final AppEntry existing = findByName(desired.name());
        if (existing == null) {
            Dao.insertApp(withSortOrder(desired, Dao.nextSortOrder()));
            return;
        }
        if (targetResolves(existing)) {
            return;   // user's tile works; hands off
        }

        System.err.println("[BuiltInApps] Repairing \"" + existing.name()
                + "\" — its target no longer exists: " + existing.launchTarget());
        Dao.updateApp(new AppEntry(
                existing.id(),
                existing.name(),
                desired.launchType(),
                desired.launchTarget(),
                desired.launchArgs(),
                desired.workingDir(),
                existing.iconPath(),        // keep whatever icon the user set
                existing.sortOrder(),       // and their grid position
                desired.compositeStartup(),
                desired.compositeDelayMs(),
                desired.compositeUrl()));
    }

    /**
     * Whether an existing tile still points at something real. URL and
     * COMPOSITE tiles have no local file to check, so they always count as
     * fine — we never second-guess a URL.
     */
    private static boolean targetResolves(final AppEntry app) {
        if (app.launchType() == LaunchType.URL || app.launchType() == LaunchType.COMPOSITE) {
            return true;
        }
        final String target = app.launchTarget();
        return target != null && !target.isBlank() && Files.exists(Paths.get(target));
    }

    private static AppEntry findByName(final String name) throws SQLException {
        final List<AppEntry> all = Dao.loadApps();
        for (AppEntry a : all) {
            if (a.name() != null && a.name().equalsIgnoreCase(name)) return a;
        }
        return null;
    }

    private static AppEntry withSortOrder(final AppEntry a, final int order) {
        return new AppEntry(a.id(), a.name(), a.launchType(), a.launchTarget(),
                a.launchArgs(), a.workingDir(), a.iconPath(), order,
                a.compositeStartup(), a.compositeDelayMs(), a.compositeUrl());
    }
}
