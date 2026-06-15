package com.example.InteractionApi;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.query.NPCQuery;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
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
     * True if the local player is walking/running (its pose differs from its idle pose).
     * Useful to avoid re-issuing clicks while already travelling to a target/loot.
     */
    public static boolean isMoving() {
        Player local = client().getLocalPlayer();
        return local != null && local.getPoseAnimation() != local.getIdlePoseAnimation();
    }
}
