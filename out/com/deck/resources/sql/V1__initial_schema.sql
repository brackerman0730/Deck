-- ============================================================================
-- Deck — V1 initial schema
-- ----------------------------------------------------------------------------
-- Two tables: `apps` for user-configured tiles, `settings` for KV state.
-- Kept intentionally flat. Phase 2 may add: categories, groups, tags.
-- ============================================================================

CREATE TABLE apps (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    launch_type   TEXT NOT NULL,
    launch_target TEXT NOT NULL,
    launch_args   TEXT,
    working_dir   TEXT,
    icon_path     TEXT,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

CREATE INDEX idx_apps_sort_order ON apps(sort_order);

CREATE TABLE settings (
    key   TEXT PRIMARY KEY,
    value TEXT
);