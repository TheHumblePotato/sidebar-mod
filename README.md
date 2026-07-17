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
- [ ] Milestone 4 — per-player sidebar (two players, different text)
- [ ] Milestone 5 — read player scoreboard objectives
- [ ] Milestone 6 — read teams
- [ ] Milestone 7 — leaderboard
- [ ] Milestone 8 — dynamic control points (tagged armor stands)
- [ ] Milestone 9 — diff-based caching / update optimization
- [ ] Milestone 10 — `config/lmssmp-sidebar.json`
- [ ] Milestone 11 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 3 has been
implemented yet.

### Testing Milestone 3

1. `./gradlew build` and run a dedicated 26.2 server with the built jar.
2. Join with two different accounts (or a second test account on a
   second client).
3. Confirm each player sees `LMSSMP` as the sidebar title and
   `Hello <their own name>` as the single line, and that neither player
   sees the other's line.

**Note:** the packet constructor signatures in `SidebarManager.java`
were written from the last verified 1.20.3+ field layout of
`ClientboundSetObjectivePacket` / `ClientboundSetDisplayObjectivePacket` /
`ClientboundSetScorePacket`. They have not been compiled against the
actual 26.2 jars yet — if `./gradlew build` reports a constructor
mismatch, check the real signature in your IDE (Loom will have pulled
the real Mojang-mapped sources) and adjust the call site accordingly.
