package com.example.InteractionApi;

import com.example.EthanApiPlugin.Collections.NPCs;
import net.runelite.api.NPC;

import java.util.function.Predicate;

/**
 * NPC interaction entry points used across the fork's plugins. These now dispatch via the
 * rev-238-verified {@link MenuActionInteractions#interactNpc(NPC, String...)} path instead of
 * the broken obfuscated {@code MousePackets.queueClickPacket} + {@code NPCPackets.queueNPCAction}
 * raw-packet path (the {@code OPNPC} names are stale on rev 238 — see CLAUDE.md). The synthetic
 * click is no longer needed: menuAction triggers the action directly.
 *
 * <p>Return contract preserved: true if a matching NPC was found and one of the requested
 * actions resolved/was dispatched, false otherwise.
 */
public class NPCInteraction {
    public static boolean interact(String name, String... actions) {
        return NPCs.search().withName(name).first()
                .map(npc -> MenuActionInteractions.interactNpc(npc, actions))
                .orElse(false);
    }

    public static boolean interact(int id, String... actions) {
        return NPCs.search().withId(id).first()
                .map(npc -> MenuActionInteractions.interactNpc(npc, actions))
                .orElse(false);
    }

    public static boolean interact(Predicate<? super NPC> predicate, String... actions) {
        return NPCs.search().filter(predicate).first()
                .map(npc -> MenuActionInteractions.interactNpc(npc, actions))
                .orElse(false);
    }

    public static boolean interactIndex(int index, String... actions) {
        return NPCs.search().indexIs(index).first()
                .map(npc -> MenuActionInteractions.interactNpc(npc, actions))
                .orElse(false);
    }

    public static boolean interact(NPC npc, String... actions) {
        return MenuActionInteractions.interactNpc(npc, actions);
    }
}
