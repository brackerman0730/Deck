package com.deck.db;

import com.deck.config.AppPaths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite connection management for Deck.
 *
 * <p>Single-connection model: we open one long-lived connection at startup
 * with WAL mode and foreign keys enabled. Deck's workload (a handful of
 * tile reads/writes) fits comfortably in one connection, so we skip pooling.
 *
 * <p>Call {@link #init()} once at app startup. Everything else grabs the
 * connection via {@link #connection()}.
 */
public final class Database {

    private static Connection connection;

    private Database() { }

    /**
     * Opens the connection, enables WAL + FK, and runs pending migrations.
     * Idempotent — calling twice is a no-op after the first success.
     */
    public static synchronized void init() throws SQLException {
        if (connection != null) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not on classpath", e);
        }

        connection = DriverManager.getConnection(AppPaths.databaseUrl());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA foreign_keys = ON");
        }

        Migrations.applyAll(connection);
    }

    /** Returns the shared connection. Throws if {@link #init()} wasn't called. */
    public static Connection connection() {
        if (connection == null) {
            throw new IllegalStateException("Database.init() has not been called");
        }
        return connection;
    }
}