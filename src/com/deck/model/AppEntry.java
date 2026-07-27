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
 *   <li>{@code launchTarget} — the URL for {@code URL}, the file path otherwise.</li>
 *   <li>{@code launchArgs} — optional space-separated args, {@code null} if none.</li>
 *   <li>{@code workingDir} — optional cwd for the launched process, {@code null}
 *       to inherit Deck's own cwd.</li>
 *   <li>{@code iconPath} — absolute path to a PNG under {@code .deck\icons\},
 *       or {@code null} for a generated default tile.</li>
 *   <li>{@code sortOrder} — grid ordering. Lower values render first.</li>
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
        int sortOrder) {
}