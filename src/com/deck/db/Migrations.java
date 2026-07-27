package com.deck.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies versioned SQL migration files bundled on the classpath.
 *
 * <p>Migration files live at {@code /com/deck/resources/sql/} and follow the
 * naming convention {@code V<n>__<description>.sql} — mirroring Flyway, but
 * hand-rolled because we don't want the dependency.
 *
 * <p>A {@code schema_version} table tracks which migrations have run. On each
 * app startup, {@link #applyAll(Connection)} looks at what's already applied
 * and runs anything newer, in numeric order.
 *
 * <p>Migrations are executed as one big multi-statement string per file. Each
 * file must therefore be safely runnable in a single transaction.
 */
public final class Migrations {

    /** Migration files that ship with the app, in numeric order. */
    private static final String[] FILES = {
            "V1__initial_schema.sql"
    };

    private Migrations() { }

    /** Applies all pending migrations against the given connection. */
    public static void applyAll(final Connection conn) throws SQLException {
        ensureVersionTable(conn);
        final List<Integer> applied = alreadyApplied(conn);

        for (String file : FILES) {
            final int version = parseVersion(file);
            if (applied.contains(version)) {
                continue;
            }
            final String sql = loadResource("/com/deck/resources/sql/" + file);
            runMigration(conn, version, sql);
        }
    }

    // ---- private helpers ---------------------------------------------------

    private static void ensureVersionTable(final Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INTEGER PRIMARY KEY,
                        applied_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private static List<Integer> alreadyApplied(final Connection conn) throws SQLException {
        final List<Integer> versions = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        }
        return versions;
    }

    private static void runMigration(final Connection conn, final int version, final String sql)
            throws SQLException {
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            // SQLite JDBC lets us execute multi-statement SQL via a single
            // execute() call when the driver's ";"-splitting is on. To be safe
            // we split ourselves on lone-line ";" markers.
            for (String stmt : splitStatements(sql)) {
                final String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                ps.setInt(1, version);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static List<String> splitStatements(final String sql) {
        final List<String> out = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\\R")) {
            cur.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static int parseVersion(final String filename) {
        // e.g., "V1__initial_schema.sql" -> 1
        final int underscore = filename.indexOf("__");
        return Integer.parseInt(filename.substring(1, underscore));
    }

    private static String loadResource(final String path) throws SQLException {
        try (InputStream in = Migrations.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new SQLException("Migration resource not found: " + path);
            }
            final StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to read migration " + path, e);
        }
    }
}