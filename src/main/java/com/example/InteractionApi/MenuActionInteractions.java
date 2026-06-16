package com.example.InteractionApi;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.query.NPCQuery;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.Packets.MousePackets;
import com.example.Packets.MovementPackets;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

/**
 * Revision-independent interaction helpers built on {@code client.menuAction(...)}.
 *
 * <p>These are the patterns verified working on the rev-238 client during the SlayerCombat
 * work. Prefer these over the obfuscated {@code PacketReflection.sendPacket(OPNPC/OPOBJ...)}
 * path, which is stale/broken (e.g. the {@code OPNPC1} name {@code "ao"} is actually a
 * coordinate packet on rev 238). See CLAUDE.md "prefer client.menuAction" strategy.
 *
 * <p>Key gotcha: never hardcode {@code NPC_FIRST_OPTION}. The option index for an action
 * (e.g. "Attack") varies per NPC — Kurask has actions {@code [null, Attack]}, so Attack is
 * the <em>second</em> option. Always resolve it from the NPC composition.
 */
public final class MenuActionInteractions {

    private MenuActionInteractions() {
    }

    private static Client client() {
        return RuneLite.getInjector().getInstance(Client.class);
    }

    /**
     * Returns the 1-based menu option index of {@code action} on this NPC (matching the
     * game's own op order), or -1 if the NPC does not offer that action. Reads the
     * transformed composition so it is correct for NPCs that change form.
     */
    public static int npcOptionIndex(NPC npc, String action) {
        if (npc == null || action == null) {
            return -1;
        }
        NPCComposition comp = NPCQuery.getNPCComposition(npc);
        String[] actions = comp != null ? comp.getActions() : null;
        if (actions == null) {
            return -1;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].equalsIgnoreCase(action)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Maps a 1-based NPC option index to the matching NPC_*_OPTION MenuAction. */
    public static MenuAction npcOptionMenuAction(int oneBasedIndex) {
        switch (oneBasedIndex) {
            case 1: return MenuAction.NPC_FIRST_OPTION;
            case 2: return MenuAction.NPC_SECOND_OPTION;
            case 3: return MenuAction.NPC_THIRD_OPTION;
            case 4: return MenuAction.NPC_FOURTH_OPTION;
            default: return MenuAction.NPC_FIFTH_OPTION;
        }
    }

    /**
     * Performs an NPC menu action (e.g. "Attack", "Talk-to") via menuAction, resolving the
     * correct option index automatically. Returns false if the NPC does not offer it.
     */
    public static boolean interactNpc(NPC npc, String action) {
        int index = npcOptionIndex(npc, action);
        if (index < 1 || index > 5) {
            return false;
        }
        String name = npc.getName();
        // For NPC options: param0/param1 = 0, id = npc index.
        client().menuAction(0, 0, npcOptionMenuAction(index), npc.getIndex(), -1,
                action, name != null ? name : "");
        return true;
    }

    /**
     * Performs the first of the given {@code actions} that this NPC actually offers, via
     * menuAction. Mirrors the varargs contract of the old {@code NPCPackets.queueNPCAction}:
     * the requested actions are tried in order and the first that resolves to a real option
     * on the NPC is dispatched. Returns false if the NPC offers none of them.
     */
    public static boolean interactNpc(NPC npc, String... actions) {
        if (npc == null || actions == null) {
            return false;
        }
        for (String action : actions) {
            if (interactNpc(npc, action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes a ground item via menuAction ("Take" = GROUND_ITEM_THIRD_OPTION, matching the
     * repo's old {@code queueTileItemAction(3, ...)}). param0/param1 are the scene X/Y of the
     * item's tile, id is the item id. Returns false if the tile is off the loaded scene.
     */
    public static boolean takeGroundItem(ETileItem item) {
        if (item == null) {
            return false;
        }
        Client client = client();
        LocalPoint lp = LocalPoint.fromWorld(client, item.getLocation());
        if (lp == null) {
            return false;
        }
        TileItem tileItem = item.getTileItem();
        client.menuAction(lp.getSceneX(), lp.getSceneY(), MenuAction.GROUND_ITEM_THIRD_OPTION,
                tileItem.getId(), -1, "Take", "");
        return true;
    }

    /**
     * Returns the 1-based menu option index of {@code action} on this object (matching the
     * game's own op order), or -1 if the object does not offer that action. Reads the
     * transformed composition (impostor) so it is correct for objects that change form.
     */
    public static int objectOptionIndex(TileObject obj, String action) {
        if (obj == null || action == null) {
            return -1;
        }
        ObjectComposition comp = TileObjectQuery.getObjectComposition(obj);
        String[] actions = comp != null ? comp.getActions() : null;
        if (actions == null) {
            return -1;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].equalsIgnoreCase(action)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Maps a 1-based object option index to the matching GAME_OBJECT_*_OPTION MenuAction. */
    public static MenuAction objectOptionMenuAction(int oneBasedIndex) {
        switch (oneBasedIndex) {
            case 1: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 2: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 3: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            default: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
        }
    }

    /**
     * Performs a world-object menu action (e.g. "Fire", "Set-up") via menuAction, resolving the
     * correct option index automatically. param0/param1 are the scene X/Y of the object's
     * (south-west) world tile — mirroring the world-point the old {@code ObjectPackets} path
     * used to identify the object. Returns false if the object does not offer the action or its
     * tile is off the loaded scene.
     */
    public static boolean interactObject(TileObject obj, String action) {
        int index = objectOptionIndex(obj, action);
        if (index < 1 || index > 5) {
            return false;
        }
        int[] scene = objectSceneCoords(obj);
        if (scene == null) {
            return false;
        }
        ObjectComposition comp = TileObjectQuery.getObjectComposition(obj);
        String name = comp != null && comp.getName() != null ? comp.getName() : "";
        client().menuAction(scene[0], scene[1], objectOptionMenuAction(index),
                obj.getId(), -1, action, name);
        return true;
    }

    /**
     * Scene (x,y) tile the engine indexes this object by, for menuAction param0/param1. For a
     * multi-tile {@link GameObject} this is its south-west base tile ({@code getSceneMinLocation});
     * deriving it from {@code getWorldLocation()} instead returns the object's CENTRE tile, which for
     * a 2x2 object (e.g. the Dwarf multicannon) is off by one and makes the op miss. Falls back to
     * the world tile for 1x1 ground/wall/decorative objects. Returns null if off the loaded scene.
     */
    private static int[] objectSceneCoords(TileObject obj) {
        if (obj instanceof GameObject) {
            Point min = ((GameObject) obj).getSceneMinLocation();
            if (min != null) {
                return new int[]{min.getX(), min.getY()};
            }
        }
        LocalPoint lp = LocalPoint.fromWorld(client(), obj.getWorldLocation());
        return lp == null ? null : new int[]{lp.getSceneX(), lp.getSceneY()};
    }

    /**
     * Dispatches an explicit 1-based object option (GAME_OBJECT_*_OPTION) via menuAction, without
     * resolving the action from the composition. Use this when the action you need isn't present on
     * the static {@code ObjectComposition} (e.g. an owned Dwarven multicannon's "Fire" option, which
     * the game adds dynamically for the owner). {@code action} is only the menu string. Returns false
     * if the object's tile is off the loaded scene.
     */
    public static boolean interactObjectOption(TileObject obj, int oneBasedIndex, String action) {
        if (obj == null || oneBasedIndex < 1 || oneBasedIndex > 5) {
            return false;
        }
        int[] scene = objectSceneCoords(obj);
        if (scene == null) {
            return false;
        }
        ObjectComposition comp = TileObjectQuery.getObjectComposition(obj);
        String name = comp != null && comp.getName() != null ? comp.getName() : "";
        client().menuAction(scene[0], scene[1], objectOptionMenuAction(oneBasedIndex),
                obj.getId(), -1, action, name);
        return true;
    }

    /**
     * Walks the local player to the given world tile. Unlike the other helpers here this is NOT a
     * menuAction: {@code client.menuAction(.., MenuAction.WALK, ..)} is useless for scripted walking
     * because the rev-238 WALK op (id 23) ignores param0/param1 and just walks to the tile the
     * client already had selected (verified in the injected client's {@code qd.fa}/{@code ev.cf}).
     * So walking genuinely needs the raw move packet — the same path the repo's other walking
     * plugins use. Returns false if the tile is off the loaded scene.
     */
    public static boolean walkTo(WorldPoint wp) {
        if (wp == null) {
            return false;
        }
        if (LocalPoint.fromWorld(client(), wp) == null) {
            return false; // destination not on the loaded scene
        }
        // rev238: MOVE_GAMECLICK is jb.eo (verified by packet sniffing — see ObfuscatedNames).
        MousePackets.queueClickPacket();
        MovementPackets.queueMovement(wp);
        return true;
    }

    /**
     * True if the local player is walking/running (its pose differs from its idle pose).
     * Useful to avoid re-issuing clicks while already travelling to a target/loot.
     */
    public static boolean isMoving() {
        Player local = client().getLocalPlayer();
        return local != null && local.getPoseAnimation() != local.getIdlePoseAnimation();
    }
}
