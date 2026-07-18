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
      diff-based updates. Still only tested with the same content as
      Milestone 3.
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
      `SidebarManager` untouched.
- [x] Milestone 7 — richer content model + formatted team name. New
      `SidebarContent(Component title, List<Component> lines)` record
      replaces the raw `List<String>` pipeline. `SidebarManager` now
      renders `SidebarContent` and no longer hardcodes the title.
      `SidebarContentBuilder` reads the team's own `getDisplayName()`
      Component and applies its `getColor()` style, so team formatting
      (from `/team modify`) reaches the client unchanged. Blank spacer
      lines were already collision-safe from Milestone 4's holder design
      (`line_<index>`, never based on content) -- no change needed there.
- [x] Milestone 8 — capture point section framework. New
      `CapturePointEntry(String name)` record (owner/progress deliberately
      left unmodeled until a milestone actually reads them). `SidebarContentBuilder`
      generates three hard-coded placeholder entries (`Alpha`/`Bravo`/`Charlie`)
      and flattens them into one Component line each under
      `Capture Points:`. `SidebarContent`/`SidebarManager` untouched --
      both only ever see the final flattened `List<Component>`.
- [x] Milestone 9 — automatic refresh: `SidebarMod` now also registers
      `ServerTickEvents.END_SERVER_TICK`, which rebuilds every online
      player's sidebar every `REFRESH_INTERVAL_TICKS` (20, ~1 second)
      through the same pipeline the join hook uses. Full rebuild every
      refresh, no diffing yet -- `SidebarManager`/`SidebarContentBuilder`
      untouched.
- [x] Milestone 10 — change detection: `SidebarState` now stores the
      last-rendered `SidebarContent` itself (value-based equality via its
      generated record `equals()`) instead of just a `List<Component>`.
      `SidebarManager` gained one small read accessor, `getLastContent(player)`
      -- otherwise unchanged, still rebuilds unconditionally whenever asked.
      `SidebarMod`'s tick loop now builds fresh content every second, compares
      it against `getLastContent`, and only calls `showSidebar` (and logs
      `[Sidebar] Updated <name>`) when they differ; otherwise it logs
      `[Sidebar] No changes for <name>` and sends nothing.
- [x] Milestone 11 — line-level packet diffing. `SidebarManager`'s single
      `showSidebar` is now three methods: `createSidebar` (full initial
      build -- join, or after `removeSidebar`), `updateSidebar` (in-place
      diff against previous content -- no objective remove/recreate, no
      resent display-slot packet), and `removeSidebar` (teardown). Line
      scores changed from `lines.size() - index` to a fixed
      `SCORE_BASE - index` so a line's packet never depends on the total
      line count, only on whether its own text changed. Removed lines use
      the newly-verified `ClientboundResetScorePacket(owner, objectiveName)`.
      A changed title uses `ClientboundSetObjectivePacket`'s
      `METHOD_CHANGE` rather than remove+recreate. `SidebarMod`'s tick
      loop now picks `createSidebar` vs `updateSidebar` based on whether
      `getLastContent` returns anything.
- [x] Milestone 12 — capture point data provider abstraction. New
      `CapturePointProvider` interface (`getCapturePoints(ServerPlayer)`)
      and its default `PlaceholderCapturePointProvider` implementation,
      still returning `Alpha`/`Bravo`/`Charlie`. `CapturePointEntry`
      expanded to `(String name, Optional<String> owner, Optional<Integer> progress)`
      -- owner/progress unused, always empty, for now.
      `SidebarContentBuilder` no longer builds placeholder entries itself;
      it calls a swappable `CapturePointProvider` (via
      `setCapturePointProvider`) and flattens whatever comes back into
      lines. `SidebarManager` untouched -- still only ever sees `Component`s.
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
      otherwise nothing. `SidebarManager` untouched -- still has no idea
      modes exist.
- [ ] Milestone 14 — `config/lmssmp-sidebar.json`
- [ ] Milestone 15 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 13 has been
implemented yet.

### Testing Milestone 13

1. `./gradlew build` and run a dedicated 26.2 server with the built jar.
2. Join and confirm the sidebar looks identical to Milestone 12's --
   `PlaceholderSidebarModeProvider` always returns `EVENTS`, so this
   should be a no-visible-change milestone by default.
3. Temporarily change `PlaceholderSidebarModeProvider#getMode` to return
   `SidebarMode.LEADERBOARD`, rebuild, rejoin, and confirm the sidebar
   shows the `Leaderboard:` layout with `Red`/`Yellow`/`Green`/`Blue: 0`.
4. Change it to `SidebarMode.MINI` and confirm the sidebar shrinks to
   just `LMSSMP` / `Score: <n>`.
5. Change it to `SidebarMode.HIDDEN` and confirm the sidebar disappears
   from the client entirely (not an empty box -- gone).
6. Revert `PlaceholderSidebarModeProvider` back to `EVENTS` when done
   testing.
