package com.deck.config;

import com.deck.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Key-value settings store backed by the {@code settings} table.
 *
 * <p>Everything is stored as text for simplicity. Callers coerce to their
 * expected type (via {@link #getBool(String, boolean)}, etc.) with sensible
 * fallbacks when the key is missing or malformed.
 *
 * <p>Keys are namespaced by dot notation, e.g. {@code "window.width"} or
 * {@code "startup.autostart"}. No enforcement — just a convention.
 */
public final class Settings {

    private Settings() { }

    // ---- read --------------------------------------------------------------

    /** Returns the raw string, or {@code null} if the key doesn't exist. */
    public static String get(final String key) throws SQLException {
        try (PreparedStatement ps = Database.connection().prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Returns the string value or {@code fallback} if missing. */
    public static String getString(final String key, final String fallback) throws SQLException {
        final String v = get(key);
        return v != null ? v : fallback;
    }

    /** Parses the value as a boolean; {@code fallback} on missing/unparseable. */
    public static boolean getBool(final String key, final boolean fallback) throws SQLException {
        final String v = get(key);
        if (v == null) return fallback;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    /** Parses the value as an int; {@code fallback} on missing/unparseable. */
    public static int getInt(final String key, final int fallback) throws SQLException {
        final String v = get(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- write -------------------------------------------------------------

    /** Upserts a key/value pair. Passing {@code null} value deletes the key. */
    public static void set(final String key, final String value) throws SQLException {
        if (value == null) {
            try (PreparedStatement ps = Database.connection().prepareStatement(
                    "DELETE FROM settings WHERE key = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = Database.connection().prepareStatement("""
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public static void setBool(final String key, final boolean value) throws SQLException {
        set(key, value ? "true" : "false");
    }

    public static void setInt(final String key, final int value) throws SQLException {
        set(key, Integer.toString(value));
    }
}