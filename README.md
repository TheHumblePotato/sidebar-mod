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
- [ ] Milestone 9 — diff-based caching / update optimization
- [ ] Milestone 10 — `config/lmssmp-sidebar.json`
- [ ] Milestone 11 — testing and cleanup

Each milestone is a separate change; nothing after Milestone 8 has been
implemented yet.

### Testing Milestone 8

1. `./gradlew build` and run a dedicated 26.2 server with the built jar.
2. Join and confirm the sidebar shows:
   ```
   LMSSMP
   Score: <n>

   Your team: <name|None>

   Capture Points:
   Alpha
   Bravo
   Charlie
   ```
3. `Alpha`/`Bravo`/`Charlie` are hard-coded placeholders -- they don't
   change based on any world state yet.
