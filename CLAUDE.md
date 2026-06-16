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
- `MousePackets.queueClickPacket` is now a guarded no-op (its rev-specific reflection can't break callers; menuAction doesn't need the synthetic click).
- **TODO (breaks when used):** NPC / world-object / ground-item interactions still call
  `PacketReflection.sendPacket(OPNPC/OPLOC/OPOBJ...)` in `InteractionApi/NPCInteraction`,
  `TileObjectInteraction`, and the tile-item helpers.

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