# Deck — Phase 1

A personal app launcher / dashboard for Windows. Tile grid of user-configured
apps (JavaFX projects, executables, scripts, web URLs). Boots to a window;
click a tile to launch. Add/edit/remove via UI dialogs. Local-first, no
network dependencies, no telemetry.

## Constraints (locked)

- **Stack:** Java 25 + JavaFX 26, SQLite via `sqlite-jdbc`
- **Toolchain:** no Maven / Gradle. `compile.ps1` / `run.ps1` with direct
  `javac` / `java`. Same pattern as Trackoff.
- **No JSON library.** Deck is offline-only — nothing to parse.
- **Package:** `com.deck.*`
- **Data folder:** `%USERPROFILE%\.deck\`
- **DB file:** `%USERPROFILE%\.deck\deck.db`
- **Icons folder:** `%USERPROFILE%\.deck\icons\`
- **Project root:** `C:\Users\ackermanb2\Desktop\Personal Projects\Deck\`

## Design language

Restrained, dark, generous whitespace. Think Raycast / Arc mini-launcher —
not Windows Start Menu.

- **Background:** `#0F1115` (near-black charcoal)
- **Surface (tiles):** `#1A1D24` (one step lighter)
- **Surface hover:** `#22262F`
- **Accent:** `#4FD1FF` (electric cyan) — distinct from Trackoff's green
- **Text primary:** `#E6E9EF`
- **Text secondary:** `#8B93A7`
- **Border subtle:** `#2A2E38`
- **Tile size:** 180×180 px, 16px rounded corners
- **Icon:** 96×96 px centered
- **Grid gutter:** 24 px
- **Typography:** Segoe UI, 14pt tile names, 24pt window title (if shown)
- **Motion:** 150ms ease-out hover, 200ms fade for feedback
- **No menu bar, no toolbar.** Just: tile grid, `+` tile, gear icon top-right.

## Data model

```sql
CREATE TABLE apps (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    launch_type   TEXT NOT NULL,      -- URL | EXE | JAR | SCRIPT
    launch_target TEXT NOT NULL,
    launch_args   TEXT,
    working_dir   TEXT,
    icon_path     TEXT,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

CREATE TABLE settings (
    key   TEXT PRIMARY KEY,
    value TEXT
);

Icons are copied into %USERPROFILE%\.deck\icons\<uuid>.png on add, so
moving/deleting the source PNG never breaks a tile.

Drops
Skeleton — folder tree, build scripts, AppPaths, Database, Migrations, Dao, Settings, empty dark window titled "Deck".
Tile grid + hardcoded samples — AppTile, LauncherView, gear button (no-op), + tile (no-op).
Launch service — LaunchService dispatches URL / EXE / JAR / SCRIPT. Wire tile clicks. Toast on launch.
Add/edit dialog — AppEditorDialog, + tile opens it, right-click context menu on tiles (Edit / Remove / Move Left / Move Right).
Settings view — SettingsView. Autostart toggle (UI only, no registry wiring yet). Open data folder button. Reset button.
Polish — empty state, drag-and-drop reorder, keyboard nav, remember window position.
Definition of Done
Grid of user-added tiles renders on launch.
Add / edit / remove / reorder all work via UI.
URL / EXE / JAR / SCRIPT launches all work.
Icons persist across restarts.
Autostart toggle exists in settings (implementation deferred to Phase 2).
Window size/position persists.
Total: 15 Java files, 1500 LOC.
Phase 2 preview (out of scope for Phase 1)
Windows autostart implementation (shell:startup shortcut or HKCU registry Run key)
Search / keyboard launcher (Ctrl+K)
Status indicators (URL ping, process check)
Composite launches (start Mongo → wait → open URL)
Categories / groups / tags
Favicon auto-scrape for URLs
Light theme, custom accent colors
Import/export config JSON