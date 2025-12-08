package com.example.InteractionApi;

import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.query.NPCQuery;
import com.example.Packets.MousePackets;
import com.example.Packets.NPCPackets;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

import java.awt.Point;
import java.util.Optional;
import java.util.function.Predicate;

public class NPCInteraction {
    public static boolean interact(String name, String... actions) {
        return NPCs.search().withName(name).first().flatMap(npc ->
        {
            Point clickPoint = RealisticClickHelper.getNPCClickPoint(npc);
            if (clickPoint != null) {
                MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
            } else {
                MousePackets.queueClickPacket(0, 0); // Fallback
            }
            NPCPackets.queueNPCAction(npc, actions);
            return Optional.of(true);
        }).orElse(false);
    }

    public static boolean interact(int id, String... actions) {
        return NPCs.search().withId(id).first().flatMap(npc ->
        {
            Point clickPoint = RealisticClickHelper.getNPCClickPoint(npc);
            if (clickPoint != null) {
                MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
            } else {
                MousePackets.queueClickPacket(0, 0); // Fallback
            }
            NPCPackets.queueNPCAction(npc, actions);
            return Optional.of(true);
        }).orElse(false);
    }

    public static boolean interact(Predicate<? super NPC> predicate, String... actions) {
        return NPCs.search().filter(predicate).first().flatMap(npc ->
        {
            Point clickPoint = RealisticClickHelper.getNPCClickPoint(npc);
            if (clickPoint != null) {
                MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
            } else {
                MousePackets.queueClickPacket(0, 0); // Fallback
            }
            NPCPackets.queueNPCAction(npc, actions);
            return Optional.of(true);
        }).orElse(false);
    }

    public static boolean interactIndex(int index, String... actions) {
        return NPCs.search().indexIs(index).first().flatMap(npc ->
        {
            Point clickPoint = RealisticClickHelper.getNPCClickPoint(npc);
            if (clickPoint != null) {
                MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
            } else {
                MousePackets.queueClickPacket(0, 0); // Fallback
            }
            NPCPackets.queueNPCAction(npc, actions);
            return Optional.of(true);
        }).orElse(false);
    }

    public static boolean interact(NPC npc, String... actions) {
        if (npc == null) {
            return false;
        }
        NPCComposition comp = NPCQuery.getNPCComposition(npc);
        if (comp == null) {
            return false;
        }
        Point clickPoint = RealisticClickHelper.getNPCClickPoint(npc);
        if (clickPoint != null) {
            MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
        } else {
            MousePackets.queueClickPacket(0, 0); // Fallback
        }
        NPCPackets.queueNPCAction(npc, actions);
        return true;
    }
}
