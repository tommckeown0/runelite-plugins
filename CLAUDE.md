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
- **Walking has no menuAction equivalent** — `MenuAction.WALK` (id 23) ignores param0/param1. So walking uses the *raw* move packet (see below). `MenuActionInteractions.walkTo(WorldPoint)` → `MousePackets.queueClickPacket()` + `MovementPackets.queueMovement(wp)`.

## The raw packet layer (movement only) — obfuscated names rename on every RuneLite bump
The `PacketReflection`/`ObfuscatedNames` layer drives `MOVE_GAMECLICK` (walking) only. **The obfuscated names rename on every RuneLite jar bump even at the same game rev**, breaking walking with `NoSuchFieldException`/`IndexOutOfBoundsException`. Re-derive them statically by `javap`-ing the injected jar — see the memory `rev238-packet-layer-fixes` for the full step-by-step recipe (find clientPacketClass by self-typed-field count, getPacketBufferNode by signature, multipliers from the writeByte bytecode, MOVE field by the call site that writes constant 5, etc.).

**Current mapping (RuneLite 1.12.31.1 / rev238):** writer `client.ad` (type `dw`); isaac `dw.aa` (`xs`); addNode `dw.ae(jr,int)`; clientPacketClass `jf` (move = `jf.ea`); getPacketBufferNode `xt.ag(jf,xs,int)` — **the int param is validated and must be `-2111588182`**; node `jr`; buffer `jr.al` (`xv` extends `xm`); offset `xm.ab`, array `xm.ak`; `offsetMultiplier=-1278253407`, `indexMultiplier=769523041`. Move wire format `[5][worldX LE][worldY LE][128-ctrl]` → `MOVE_GAMECLICK_WRITES = {{"v"},{"v","r 8"},{"v","r 8"},{"s 128"}}`, write order 5/worldPointX/worldPointY/ctrlDown. (The previous 1.12.28 layout had Y-before-X with worldX big-endian and ctrl plain — that was wrong and never verified in-game.)

To skip the startup vanilla-jar download, `~/.runelite/PacketUtils/<rlVersion>-<rev>.txt` must exist with two lines `true` and `<writer>.<addNode>` (currently `1.12.31.1-238.txt` = `true` / `dw.ae`). `usingClientAddNode` defaults true so the reflective addNode path is used regardless. The **`A Packet Sniffer (diagnostic)`** plugin (`src/main/java/com/example/PacketSniffer/`) reads the live outgoing writer reflectively for runtime verification, but the static recipe above is sufficient and needs no dev client.

## Recipe to fix a broken interaction
Replace the `sendPacket(...)` call in the helper with `client.menuAction(...)`; the existing packet args already hold the right values, just map them across:
- NPC: `menuAction(0, 0, MenuAction.NPC_FIRST_OPTION(/SECOND..), npcIndex, -1, option, "")`
- Object: `menuAction(sceneX, sceneY, MenuAction.GAME_OBJECT_FIRST_OPTION(..), objectId, -1, option, "")`
- Ground item: `menuAction(sceneX, sceneY, MenuAction.GROUND_ITEM_FIRST_OPTION(..), itemId, -1, option, "")`
Pick FIRST/SECOND/THIRD by the op index the helper resolves; verify param0/param1 against the helper's existing worldPoint→scene handling.

## Reverse-engineering tooling (only if a menu action ever needs verifying)
Injected client jar lives in the gradle cache: `~/.gradle/caches/modules-2/files-2.1/net.runelite/injected-client/<ver>/.../injected-client-<ver>.jar`. Disassemble with JDK-11 `javap -c -p` (extract with `jar xf` first; `javap -p *.class` for a fast signature dump). For 1.12.31.1: packet builder is `xt.ag(jf, xs, int)`; addNode is `dw.ae`; buffer write methods live on `xm` (el=LE short, bw=byte, dz=`128-v` byte); buffer offset/array fields `xm.ab`/`xm.ak`; offsetMultiplier -1278253407 / indexMultiplier 769523041.

Once NPC/object/ground-item interactions are migrated, the whole `Packets`/`PacketReflection`/`ObfuscatedNames` layer can be deleted.

## Some other notes
I name the plugins with 'A' at the start of the name so they appear at the top of the plugin list in Runelite