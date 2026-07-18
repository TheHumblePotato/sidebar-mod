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
- [x] Milestone 5 — first real content reader: `SidebarContentBuilder`
      reads the datapack-owned `"score"` objective for a player via
      `Scoreboard#getObjective` / `Scoreboard#getPlayerScoreInfo` (read-only
      calls to the *real* server scoreboard -- a separate instance from
      `SidebarManager`'s private scratch scoreboard) and returns
      `["Score: <value>"]`, or `["Score: 0"]` if the objective or the
      player's entry doesn't exist yet. `SidebarMod`'s join hook now
      chains `SidebarContentBuilder.buildSidebarLines(player)` into
      `SidebarManager.showSidebar(player, lines)`.
- [ ] Milestone 6 — read teams
- [ ] Milestone 7 — leaderboard
- [ ] Milestone 8 — dynamic control points (tagged armor stands)
- [ ] Milestone 9 — diff-based caching / update optimization
- [ ] Milestone 10 — `config/lmssmp-sidebar.json`
- [ ] Milestone 11 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 5 has been
implemented yet.

### Testing Milestone 5

1. Have the datapack (or a manual `/scoreboard objectives add score dummy`
   for testing) create an objective named `score`.
2. `./gradlew build` and run a dedicated 26.2 server with the built jar.
3. Set a test score: `/scoreboard players set <name> score 1234`, then have
   that player join (or rejoin).
4. Confirm the sidebar shows `LMSSMP` as the title and `Score: 1234` as
   the line.
5. Join with a player who has no `score` entry yet and confirm the line
   reads `Score: 0` instead of erroring.

**Note:** the packet constructor signatures in `SidebarManager.java`
have been checked against the real 26.2 decompiled sources (`Objective`,
`ClientboundSetObjectivePacket`, `ClientboundSetDisplayObjectivePacket`,
`Scoreboard`). `SidebarContentBuilder.java`'s reading code
(`Scoreboard#getPlayerScoreInfo`, `ReadOnlyScoreInfo#value()`) has only
been checked against public javadoc for 1.20.6–1.21.11, not decompiled
26.2 sources directly -- see the API-uncertainties note in chat for what
to check first if `./gradlew build` errors here.
