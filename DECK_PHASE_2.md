# Deck — Phase 2

Phase 1 shipped a working launcher. Phase 2 turns it into a daily driver:
Deck launches itself on Windows boot, handles multi-step composite launches
(the Regiquiz case), and gains a fuzzy Ctrl+K search bar.

## Goals

1. **Boot habit** — Deck starts with Windows so it's the first thing on-screen.
2. **Composite launches** — one tile can start a background service, wait,
   then open a URL. Solves Regiquiz (start Node, wait, open localhost:3000)
   and anything else with a "warm-up" pattern.
3. **Keyboard-first** — Ctrl+K opens a fuzzy launcher. Type three letters,
   press Enter, done.

## Non-goals (Phase 2)

- Global system hotkey (Ctrl+Alt+Space anywhere in Windows) — deferred
- Process supervision / child-tracking — deferred forever unless demanded
- Status indicators (URL ping, "is Mongo up?" green dot) — Phase 3
- Categories / groups / folders — Phase 3
- Sequence editor with more than 2 steps — Phase 3 if ever needed

## Constraints (locked, same as Phase 1)

- Java 25 + JavaFX 26, SQLite via `sqlite-jdbc`
- No Maven / Gradle, no new third-party libs
- Hand-rolled everything (fuzzy match included)
- Data folder stays `%USERPROFILE%\.deck\`

## Design decisions

### A) Autostart mechanism — Startup-folder shortcut

Create `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Deck.lnk`
pointing to a `Deck.bat` wrapper in the project root. Wrapper invokes
`run.ps1` with the working directory set correctly.

Chosen over a `HKCU\...\Run` registry key because:

- Visible in Task Manager → Startup tab, so the user can disable via GUI
- No admin rights needed
- Delete the `.lnk` and it's gone — no orphan registry state

`.lnk` files are OLE compound documents — annoying to write from pure Java.
We shell out to a one-liner PowerShell using `WScript.Shell.CreateShortcut`.

### B) Composite launch UX — two fields + delay

New launch type `COMPOSITE` with three DB columns beyond the existing ones:

- `composite_startup` (TEXT) — command to run in the background first
- `composite_delay_ms` (INTEGER) — millis to wait after startup succeeds
- `composite_url` (TEXT) — URL to open after the delay

Editor UI reveals these three fields when type = COMPOSITE. Launch service
spawns the startup command (fire-and-forget), sleeps on a daemon thread,
then browses to the URL.

Chosen over a full sequence editor because:

- Solves Regiquiz-shaped launches without over-engineering
- Migration path to N-step sequences is clean if ever needed

### C) Search launcher — in-window Ctrl+K

A modal overlay in the launcher scene. Ctrl+K shows it, Escape/click-away
hides it. TextField at the top, ListView of matches below. Enter launches
the top match; ↑/↓ navigates.

Chosen over a global system hotkey because:

- Zero native dependencies
- Global hotkey is purely additive later if daily use demands it

### D) Match algorithm — hand-rolled fuzzy

VS Code / Sublime style:

- Iterate the query characters; each must appear in the target in order
- Score bonuses for consecutive matches, first-letter matches, and
  word-boundary matches (after space/underscore/case-transition)
- Rank by score descending, ties broken by shorter target

`gdp` matches "Google Doc Planner" (high score), "Grandpa" (lower),
"go deep" (medium).

### E) Child processes — unmanaged

Deck starts them, then walks away. Closing Deck does not kill children.
User is responsible for cleanup of long-running services they started.

## Data model additions

Migration `V2__composite_columns.sql`:

    ALTER TABLE apps ADD COLUMN composite_startup   TEXT;
    ALTER TABLE apps ADD COLUMN composite_delay_ms  INTEGER;
    ALTER TABLE apps ADD COLUMN composite_url       TEXT;

`AppEntry` record grows three fields. `LaunchType` enum gains `COMPOSITE`.

## Drops

1. **Autostart** — new file `WindowsAutostart.java`, new script
   `deck-autostart-shim.ps1`, wire the settings toggle. Wrapper `Deck.bat`
   at project root for the shortcut target.
2. **Composite launches** — `V2__composite_columns.sql`, extend `AppEntry`
   and `AppEntry.launchType`, extend `AppEditorDialog` with reveal-on-type,
   extend `LaunchService` with the three-step flow.
3. **Search launcher** — new files `SearchLauncher.java`, `FuzzyMatcher.java`.
   Wire Ctrl+K in `LauncherView`. Escape / click-away dismisses.

## Definition of Done

- Toggling "Launch on startup" in Settings creates/removes a real
  `Deck.lnk` shortcut on disk. Restarting Windows launches Deck.
- Composite tiles work end-to-end (verified with a real Regiquiz-style
  setup: PowerShell one-liner + delay + URL).
- Ctrl+K anywhere in the main window opens the launcher overlay. Typing
  fuzzy queries filters. Enter launches the top match.
- No regressions in Phase 1 behavior.
- File count target: 20 Java files total (Phase 1: 16 + 4 new: WindowsAutostart,
  FuzzyMatcher, SearchLauncher, + one growth).
- LOC target: ~2200 (Phase 1: ~1600 + ~600).

## Phase 3 preview (out of scope)

- Status indicators (ping URL, check process)
- Categories / groups with collapsible headers
- Off-screen restore clamp
- Global system hotkey
- Import/export config
- Light theme + custom accent colors
- Auto-scrape favicon for URL tiles