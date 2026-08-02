package com.deck.model;

/**
 * One user-configured app tile.
 *
 * <p>Immutable record. Field semantics mirror the {@code apps} table columns
 * one-for-one:
 *
 * <ul>
 *   <li>{@code id} — DB primary key. Use {@code -1} for unsaved new entries.</li>
 *   <li>{@code name} — display label under the tile.</li>
 *   <li>{@code launchType} — dispatch strategy (see {@link LaunchType}).</li>
 *   <li>{@code launchTarget} — the URL for {@code URL}, the file path otherwise.
 *       For {@code COMPOSITE} it's unused but kept non-empty (the DB column is
 *       {@code NOT NULL}); the editor mirrors the composite URL into it.</li>
 *   <li>{@code launchArgs} — optional space-separated args, {@code null} if none.</li>
 *   <li>{@code workingDir} — optional cwd for the launched process, {@code null}
 *       to inherit Deck's own cwd.</li>
 *   <li>{@code iconPath} — absolute path to a PNG under {@code .deck\icons\},
 *       or {@code null} for a generated default tile.</li>
 *   <li>{@code sortOrder} — grid ordering. Lower values render first.</li>
 *   <li>{@code compositeStartup} — {@code COMPOSITE} only: command line run in
 *       the background before the URL opens. {@code null} for other types.</li>
 *   <li>{@code compositeDelayMs} — {@code COMPOSITE} only: millis to wait after
 *       the startup command before browsing. {@code 0} means don't wait; a SQL
 *       {@code NULL} reads back as {@code 0}.</li>
 *   <li>{@code compositeUrl} — {@code COMPOSITE} only: URL opened after the
 *       delay. {@code null} for other types.</li>
 * </ul>
 */
public record AppEntry(
        long id,
        String name,
        LaunchType launchType,
        String launchTarget,
        String launchArgs,
        String workingDir,
        String iconPath,
        int sortOrder,
        String compositeStartup,
        int compositeDelayMs,
        String compositeUrl) {

    /**
     * Builds a non-composite entry — the three composite fields are left empty.
     * Keeps the common URL/EXE/JAR/SCRIPT call sites readable.
     */
    public static AppEntry simple(final long id,
                                  final String name,
                                  final LaunchType launchType,
                                  final String launchTarget,
                                  final String launchArgs,
                                  final String workingDir,
                                  final String iconPath,
                                  final int sortOrder) {
        return new AppEntry(id, name, launchType, launchTarget, launchArgs,
                workingDir, iconPath, sortOrder, null, 0, null);
    }
}
