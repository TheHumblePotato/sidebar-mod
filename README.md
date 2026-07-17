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
- [ ] Milestone 3 — static test sidebar ("LMSSMP" / "Testing")
- [ ] Milestone 4 — per-player sidebar (two players, different text)
- [ ] Milestone 5 — read player scoreboard objectives
- [ ] Milestone 6 — read teams
- [ ] Milestone 7 — leaderboard
- [ ] Milestone 8 — dynamic control points (tagged armor stands)
- [ ] Milestone 9 — diff-based caching / update optimization
- [ ] Milestone 10 — `config/lmssmp-sidebar.json`
- [ ] Milestone 11 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 2 has been
implemented yet.
