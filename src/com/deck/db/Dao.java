package com.deck.db;

import com.deck.model.AppEntry;
import com.deck.model.LaunchType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-access layer for Deck. One class, one table (for now) — kept flat and
 * boring on purpose. All methods use the shared {@link Database#connection()}.
 */
public final class Dao {

    private Dao() { }

    // ---- apps table --------------------------------------------------------

    /**
     * Loads every app tile from the DB, sorted by {@code sort_order} ascending
     * (ties broken by id).
     */
    public static List<AppEntry> loadApps() throws SQLException {
        final String sql = """
                SELECT id, name, launch_type, launch_target, launch_args,
                       working_dir, icon_path, sort_order,
                       composite_startup, composite_delay_ms, composite_url
                FROM apps
                ORDER BY sort_order ASC, id ASC
                """;
        final List<AppEntry> out = new ArrayList<>();
        try (Statement st = Database.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(fromRow(rs));
            }
        }
        return out;
    }

    /** Inserts a new app row. Returns the generated id. */
    public static long insertApp(final AppEntry app) throws SQLException {
        final String sql = """
                INSERT INTO apps
                    (name, launch_type, launch_target, launch_args,
                     working_dir, icon_path, sort_order, created_at, updated_at,
                     composite_startup, composite_delay_ms, composite_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        final long now = System.currentTimeMillis();
        try (PreparedStatement ps = Database.connection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, app.name());
            ps.setString(2, app.launchType().name());
            ps.setString(3, app.launchTarget());
            ps.setString(4, app.launchArgs());
            ps.setString(5, app.workingDir());
            ps.setString(6, app.iconPath());
            ps.setInt(7, app.sortOrder());
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.setString(10, app.compositeStartup());
            ps.setInt(11, app.compositeDelayMs());
            ps.setString(12, app.compositeUrl());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /** Updates all mutable fields on an existing app. */
    public static void updateApp(final AppEntry app) throws SQLException {
        final String sql = """
                UPDATE apps SET
                    name = ?, launch_type = ?, launch_target = ?,
                    launch_args = ?, working_dir = ?, icon_path = ?,
                    sort_order = ?, updated_at = ?,
                    composite_startup = ?, composite_delay_ms = ?,
                    composite_url = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = Database.connection().prepareStatement(sql)) {
            ps.setString(1, app.name());
            ps.setString(2, app.launchType().name());
            ps.setString(3, app.launchTarget());
            ps.setString(4, app.launchArgs());
            ps.setString(5, app.workingDir());
            ps.setString(6, app.iconPath());
            ps.setInt(7, app.sortOrder());
            ps.setLong(8, System.currentTimeMillis());
            ps.setString(9, app.compositeStartup());
            ps.setInt(10, app.compositeDelayMs());
            ps.setString(11, app.compositeUrl());
            ps.setLong(12, app.id());
            ps.executeUpdate();
        }
    }

    /** Deletes an app by id. */
    public static void deleteApp(final long id) throws SQLException {
        try (PreparedStatement ps = Database.connection().prepareStatement(
                "DELETE FROM apps WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
/** Returns the next {@code sort_order} value to use for a new app row. */
    public static int nextSortOrder() throws SQLException {
        try (Statement st = Database.connection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM apps")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
/**
     * Bulk-assigns {@code sort_order} = index for every id in the given list.
     * Runs inside a single transaction so a partial failure doesn't leave the
     * grid in a mixed state. Used by drag-and-drop reorder in
     * {@link com.deck.ui.LauncherView}.
     */
    public static void setSortOrdersBulk(final List<Long> idsInOrder) throws SQLException {
        final Connection conn = Database.connection();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE apps SET sort_order = ?, updated_at = ? WHERE id = ?")) {
            final long now = System.currentTimeMillis();
            for (int i = 0; i < idsInOrder.size(); i++) {
                ps.setInt(1, i);
                ps.setLong(2, now);
                ps.setLong(3, idsInOrder.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    /** Sets just the {@code sort_order} field for the given app id. */
    public static void setSortOrder(final long id, final int order) throws SQLException {
        try (PreparedStatement ps = Database.connection().prepareStatement(
                "UPDATE apps SET sort_order = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, order);
            ps.setLong(2, System.currentTimeMillis());
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    // ---- row mapping -------------------------------------------------------
    private static AppEntry fromRow(final ResultSet rs) throws SQLException {
        return new AppEntry(
                rs.getLong("id"),
                rs.getString("name"),
                LaunchType.valueOf(rs.getString("launch_type")),
                rs.getString("launch_target"),
                rs.getString("launch_args"),
                rs.getString("working_dir"),
                rs.getString("icon_path"),
                rs.getInt("sort_order"),
                rs.getString("composite_startup"),
                // getInt maps SQL NULL to 0, which is exactly "no delay".
                rs.getInt("composite_delay_ms"),
                rs.getString("composite_url"));
    }
}