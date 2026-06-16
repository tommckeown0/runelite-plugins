# PacketUtils custom plugins — context for Claude

Personal fork of RuneLite external plugins (EthanApi/PacketUtils) with ~20 custom plugins.
Repo: `github.com/tommckeown0/runelite-plugins`, working branch `custom-plugins`.

## Build / run
- **Use JDK 11** (`C:\Users\tommc\.jdks\temurin-11.0.29`). Lombok is pinned to 1.18.20, which fails on JDK 21/22 with `NoSuchFieldError ... JCImport.qualid`.
- Run via IntelliJ: `ExamplePluginTest.main` (loads the plugins into a dev client). There is **no** gradle `run`/`runPluginTest` task.
- To compile-check from CLI: `./gradlew compileJava --no-daemon -Dorg.gradle.java.home="C:/Users/tommc/.jdks/temurin-11.0.29"`.

## Current strategy: prefer client.menuAction over obfuscated packet names
Game client is on **rev 238**. The obfuscated packet path (`Packets/PacketReflection` + `PacketUtils/ObfuscatedNames`) breaks on every game update and the mappings are deep/fragile to reverse-engineer — the rev-238 `IF_BUTTONX` mapping (`jb.ae`) was found to be outright the *wrong packet*. So we are migrating interactions to RuneLite's revision-independent public API `client.menuAction(...)` instead of re-deriving names.

**Default: when a plugin breaks after a revision bump, migrate its shared interaction helper to `menuAction` — do not re-derive obfuscated names** (only raw packets with no menu-action equivalent, e.g. resume-dialogs, justify touching ObfuscatedNames).

## Migration status
- **Done (works):** all widget-button actions. `WidgetPackets.queueWidgetActionPacket` routes to
  `client.menuAction(childId, widgetId, op<=5?CC_OP:CC_OP_LOW_PRIORITY, op, itemId, "", "")`.
  Covers gear equip, prayers (`PrayerInteraction`), bank, GE, trade, sailing, agility buttons.
- **Done (works):** NPC / world-object / ground-item interactions, via `InteractionApi/MenuActionInteractions.java` (`interactNpc`, `interactObject`, `takeGroundItem` — resolve the op index from the composition, don't hardcode FIRST_OPTION). New plugins should call these, not `TileObjectInteraction`/`NPCInteraction` (which still use the dead `PacketReflection.sendPacket` path).
- `MousePackets.queueClickPacket` is a guarded no-op.
- **Walking has no menuAction equivalent** — `MenuAction.WALK` (id 23) ignores param0/param1. So walking uses the *raw* move packet, which is now fixed on rev238 (see below). `MenuActionInteractions.walkTo(WorldPoint)` → `MousePackets.queueClickPacket()` + `MovementPackets.queueMovement(wp)`.

## The raw packet layer was REVIVED on rev238 (movement works)
The `PacketReflection`/`ObfuscatedNames` layer was fully broken but is now functional. Three stale values were fixed (they chained — each crash hid the next bug):
1. `packetWriterFieldName` `"cg"` → `"aq"` (rev238 `client.cg` is a `static long`; the `df` packet writer is `client.aq`).
2. `MOVE_GAMECLICK` opcode `"ca"` → `"eo"` (`jb.ca` was an interaction packet). `jb.eo` payload (WORLD coords, no +128): `[5][worldY LE][worldX BE][ctrl]`. Writes reordered accordingly.
3. `offsetMultiplier`/`indexMultiplier` `1741769013`/`2108391709` → `228932457`/`-661977895` (from `xi.ea`: `au += 228932457; index = au*-661977895 - 1`). Wrong values → `ArrayIndexOutOfBoundsException` in `BufferMethods`.

These were found with the **`A Packet Sniffer (diagnostic)`** plugin (`src/main/java/com/example/PacketSniffer/`) — reads the client's outgoing packets reflectively (queue `df.ak` of `jm` nodes, each with `jm.ah`=its `jb` def for naming + `jm.ay.al`=bytes; and output buffer `df.az`). Keep it for future packet RE, or delete once movement is confirmed. So raw packets ARE viable again when there's no menuAction equivalent — but menuAction is still preferred for interactions.

## Recipe to fix a broken interaction
Replace the `sendPacket(...)` call in the helper with `client.menuAction(...)`; the existing packet args already hold the right values, just map them across:
- NPC: `menuAction(0, 0, MenuAction.NPC_FIRST_OPTION(/SECOND..), npcIndex, -1, option, "")`
- Object: `menuAction(sceneX, sceneY, MenuAction.GAME_OBJECT_FIRST_OPTION(..), objectId, -1, option, "")`
- Ground item: `menuAction(sceneX, sceneY, MenuAction.GROUND_ITEM_FIRST_OPTION(..), itemId, -1, option, "")`
Pick FIRST/SECOND/THIRD by the op index the helper resolves; verify param0/param1 against the helper's existing worldPoint→scene handling.

## Reverse-engineering tooling (only if a menu action ever needs verifying)
Injected client jar: `~/.runelite/PacketUtils/injected-1.12.28.jar`; disassemble with JDK-11 `javap -c -p`. menuAction dispatcher is `client.uo(int x6, String x2, int x2)`. Packet builder is `gi.ak(jb, yk, byte)`; addNode is `df.az`; buffer write methods live on `xi` (et=4-byte middle-endian int, ba/ca=+128 shorts, cx=byte); buffer offset/array fields `xi.au`/`xi.al`; offsetMultiplier 228932457 / indexMultiplier -661977895.

Once NPC/object/ground-item interactions are migrated, the whole `Packets`/`PacketReflection`/`ObfuscatedNames` layer can be deleted.

## Some other notes
I name the plugins with 'A' at the start of the name so they appear at the top of the plugin list in Runelite