package com.deck.model;

/**
 * How a tile's target gets launched.
 *
 * <ul>
 *   <li>{@link #URL}    — opened in the default browser via {@code Desktop.browse}</li>
 *   <li>{@link #EXE}    — executed directly via {@code ProcessBuilder}</li>
 *   <li>{@link #JAR}    — run via {@code java -jar <target>}</li>
 *   <li>{@link #SCRIPT} — {@code .bat} / {@code .cmd} / {@code .ps1} run via
 *       {@code ProcessBuilder} (PowerShell scripts get a {@code powershell -File}
 *       wrapper)</li>
 * </ul>
 */
public enum LaunchType {
    URL,
    EXE,
    JAR,
    SCRIPT
}