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
      online player's current lines as scaffolding for Milestone 9's
      diff-based updates. Still only tested with the same content as
      Milestone 3.
- [x] Milestone 6 — full sidebar layout: `SidebarContentBuilder` now
      returns `Score: <n>`, a blank line, `Your team: <name|None>`
      (via `Entity#getTeam()`, a read-only accessor onto the same team
      assignment `/team` commands manage), a blank line, and a
      `Capture Points:` / `(Not implemented)` placeholder pair.
      `SidebarManager` untouched.
- [ ] Milestone 7 — leaderboard
- [ ] Milestone 8 — dynamic control points (tagged armor stands)
- [ ] Milestone 9 — diff-based caching / update optimization
- [ ] Milestone 10 — `config/lmssmp-sidebar.json`
- [ ] Milestone 11 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 6 has been
implemented yet.

### Testing Milestone 6

1. `./gradlew build` and run a dedicated 26.2 server with the built jar.
2. With no team and no score set, join and confirm:
   ```
   LMSSMP
   Score: 0

   Your team: None

   Capture Points:
   (Not implemented)
   ```
3. Run `/team add red`, `/team join red <name>`, set a score, then
   rejoin (join hook only fires on connect) and confirm `Your team: red`
   and the real score both show.

**Note:** `readTeamName`'s use of `Entity#getTeam()` has not been checked
against 26.2 decompiled sources directly -- it's a long-stable accessor
across many past versions, but if `./gradlew build` errors here, it's
the first thing to check with F12.
