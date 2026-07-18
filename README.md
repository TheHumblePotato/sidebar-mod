# LMSSMP Sidebar Mod

Packet-based, per-player sidebar display layer for the LMSSMP datapack.
Fabric server mod, Minecraft Java Edition 26.2, Java 25.

The mod contains **no game logic**. It only reads scoreboard objectives,
teams, and tagged armor stands that the datapack maintains, and renders
that data into per-player sidebar packets. See the datapack for scoring,
capture logic, and win conditions.

## Requirements

- Java 25 JDK
- A Minecraft 26.2 server environment (for testing)
- Internet access to Fabric's Maven (`maven.fabricmc.net`) and Mojang's
  piston-meta servers, which Loom needs to resolve the Minecraft artifact
  and Fabric Loader/API

## Building

```bash
./gradlew build
```

The output jar is written to `build/libs/lmssmp-sidebar-<version>.jar`.
Minecraft 26.2 is unobfuscated, so there is no `remapJar` step — the
plain `jar` task already produces the deployable artifact.

If you don't already have a `gradle-wrapper.jar` in `gradle/wrapper/`,
generate one once with a system-installed Gradle:

```bash
gradle wrapper --gradle-version 9.6.1
```

## Datapack integration (Milestone 14+)

Two scoreboard objectives are read directly by the mod:

- `sidebar_mode` — per-player, values `0`=Hidden, `1`=Events,
  `2`=Leaderboard, `3`=Mini. Unknown/missing values default to Events.
  Example: `/scoreboard players set <player> sidebar_mode 2`
- `score` — used both for the per-player score line, and (as of
  Milestone 14) for team totals in Leaderboard mode, keyed by team name
  as the scoreboard holder: `Red`, `Yellow`, `Green`, `Blue`. Example:
  `/scoreboard players add Red score 118`

If the datapack stores either of these under different names, update
`ScoreboardSidebarModeProvider.MODE_OBJECTIVE_NAME` or the constants in
`SidebarContentBuilder` accordingly.

## Milestone status

- [x] Milestone 1 — empty mod, compiles and loads (`onInitialize` logs only)
- [x] Milestone 2 — initialization check (`[LMSSMP Sidebar] Mod initialized successfully`)
- [x] Milestone 3 — static test sidebar ("LMSSMP" / "Hello <player name>"),
      sent per-connection via `SidebarManager` on `ServerPlayConnectionEvents.JOIN`.
      No shared scoreboard object is used, so each player only ever
      receives their own packets.
- [x] Milestone 4 — refactored into a reusable renderer:
      `SidebarManager.showSidebar(player, List<String> lines)` replaces the
      single-purpose `showTestSidebar`. Lines are shown using stable fake
      score holders (`line_0`, `line_1`, ...) with their visible text set
      via `ClientboundSetScorePacket`'s display-Component field, not the
      holder name itself, so a line's identity stays stable even if its
      text changes later. `SidebarState` (package-private) tracks each
      online player's current lines as scaffolding for Milestone 10's
      diff-based updates.
- [x] Milestone 5 — first real content reader: `SidebarContentBuilder`
      reads the datapack-owned `"score"` objective for a player via
      `Scoreboard#getObjective` / `Scoreboard#getPlayerScoreInfo` (read-only
      calls to the *real* server scoreboard -- a separate instance from
      `SidebarManager`'s private scratch scoreboard) and returns
      `["Score: <value>"]`, or `["Score: 0"]` if the objective or the
      player's entry doesn't exist yet.
- [x] Milestone 6 — full sidebar layout: `SidebarContentBuilder` now
      returns `Score: <n>`, a blank line, `Your team: <name|None>`
      (via `Entity#getTeam()`, a read-only accessor onto the same team
      assignment `/team` commands manage), a blank line, and a
      `Capture Points:` / `(Not implemented)` placeholder pair.
- [x] Milestone 7 — richer content model + formatted team name. New
      `SidebarContent(Component title, List<Component> lines)` record
      replaces the raw `List<String>` pipeline. `SidebarManager` now
      renders `SidebarContent` and no longer hardcodes the title.
      `SidebarContentBuilder` reads the team's own `getDisplayName()`
      Component and applies its `getColor()` style, so team formatting
      (from `/team modify`) reaches the client unchanged.
- [x] Milestone 8 — capture point section framework. New
      `CapturePointEntry(String name)` record (owner/progress deliberately
      left unmodeled until a milestone actually reads them). `SidebarContentBuilder`
      generates three hard-coded placeholder entries (`Alpha`/`Bravo`/`Charlie`)
      and flattens them into one Component line each under
      `Capture Points:`.
- [x] Milestone 9 — automatic refresh: `SidebarMod` now also registers
      `ServerTickEvents.END_SERVER_TICK`, which rebuilds every online
      player's sidebar every `REFRESH_INTERVAL_TICKS` through the same
      pipeline the join hook uses. Full rebuild every refresh, no
      diffing yet.
- [x] Milestone 10 — change detection: `SidebarState` now stores the
      last-rendered `SidebarContent` itself (value-based equality via its
      generated record `equals()`) instead of just a `List<Component>`.
      `SidebarManager` gained one small read accessor, `getLastContent(player)`.
      `SidebarMod`'s tick loop now builds fresh content every refresh,
      compares it against `getLastContent`, and only calls `showSidebar`
      when they differ; otherwise it sends nothing.
- [x] Milestone 11 — line-level packet diffing. `SidebarManager`'s single
      `showSidebar` is now three methods: `createSidebar` (full initial
      build -- join, or after `removeSidebar`), `updateSidebar` (in-place
      diff against previous content -- no objective remove/recreate, no
      resent display-slot packet), and `removeSidebar` (teardown). Removed
      lines use `ClientboundResetScorePacket(owner, objectiveName)`. A
      changed title uses `ClientboundSetObjectivePacket`'s
      `METHOD_CHANGE` rather than remove+recreate.
- [x] Milestone 12 — capture point data provider abstraction. New
      `CapturePointProvider` interface (`getCapturePoints(ServerPlayer)`)
      and its default `PlaceholderCapturePointProvider` implementation,
      still returning `Alpha`/`Bravo`/`Charlie`. `CapturePointEntry`
      expanded to `(String name, Optional<String> owner, Optional<Integer> progress)`
      -- owner/progress unused, always empty, for now.
      `SidebarContentBuilder` calls a swappable `CapturePointProvider` (via
      `setCapturePointProvider`) and flattens whatever comes back into
      lines.
- [x] Milestone 13 — sidebar mode architecture. New `SidebarMode` enum
      (`HIDDEN`/`EVENTS`/`LEADERBOARD`/`MINI`, with `fromId(int)` mapping
      0-3 and defaulting unknown values to `EVENTS`), `SidebarModeProvider`
      interface, and its `PlaceholderSidebarModeProvider` default (always
      `EVENTS`, preserving prior behavior). `SidebarContentBuilder` now
      dispatches to `buildEvents`/`buildLeaderboard`/`buildMini` based on
      the registered provider's answer, and returns
      `Optional<SidebarContent>` -- empty for `HIDDEN`. `SidebarMod`
      resolves that Optional: present -> create/update as before; empty
      -> `SidebarManager.removeSidebar` if a sidebar was showing,
      otherwise nothing.
- [x] Milestone 14 — polish + first real datapack integration:
      - Refresh interval dropped from 20 ticks to 4 (~5x/sec). Safe
        because `updateSidebar` already no-ops when nothing changed, so
        polling faster just detects real changes sooner, with no extra
        packets and no flicker.
      - Line scores changed from a fixed `SCORE_BASE - index` scheme to
        `totalVisibleLines - index`, so the client now shows a plain
        descending count (e.g. `6, 5, 4 ... 1`) instead of numbers like
        `1000, 999, 998`. Because the number now depends on total line
        count, `updateSidebar` resends every surviving line when the
        count changes (to keep numbers correct), and still skips
        unchanged lines when the count is stable.
      - Every visible layout (`EVENTS`, `LEADERBOARD`, `MINI`) now shows
        a shared `Your Team: <name>` line via one `teamLine()` helper in
        `SidebarContentBuilder`.
      - Fixed the Milestone 13 leaderboard bug: team totals were never
        read from the scoreboard at all (hardcoded to `0`). New
        `readNamedScore` helper reads a plain-string holder (`Red`,
        `Yellow`, `Green`, `Blue`) off the `"score"` objective, the same
        read-only way `readScore` reads a player.
      - New `ScoreboardSidebarModeProvider` replaces
        `PlaceholderSidebarModeProvider` as the default: reads a
        per-player `sidebar_mode` objective (`0`-`3`) so the datapack can
        set each player's mode independently. Missing objective/entry or
        an out-of-range value safely falls back to `EVENTS`.
        `SidebarManager` remains completely unaware any of this exists.
- [ ] Milestone 15 — testing and cleanup

### Testing Milestone 14

1. `./gradlew build` and run a dedicated 26.2 server with the built jar.
2. Set two different players to different modes, e.g.
   `/scoreboard players set PlayerA sidebar_mode 1` (Events) and
   `/scoreboard players set PlayerB sidebar_mode 2` (Leaderboard).
   Confirm each sees a different layout at the same time.
3. `/scoreboard players set PlayerA sidebar_mode 0` -- confirm the
   sidebar disappears entirely for PlayerA only.
4. `/scoreboard players set Red score 118` and refresh a
   Leaderboard-mode player -- confirm the sidebar shows `Red: 118`, not
   `0`.
5. Confirm every mode shows `Your Team: <name>`.
6. Change a player's own `score` value and confirm the sidebar updates
   within roughly 200ms, with no flicker and no full rebuild -- only the
   changed line's packet should be sent.
7. Add/remove a capture point (or otherwise change the number of visible
   lines) and confirm all remaining lines' displayed numbers shift
   correctly (e.g. `5,4,3...` instead of `6,5,4...`).
