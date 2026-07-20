package com.example.PacketSniffer;

import com.example.PacketUtils.PacketReflection;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.ClientTick;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One-off diagnostic to capture the bytes of OUTGOING packets the game client queues, so we can
 * reverse packets that have no menuAction equivalent (originally built for MOVE_GAMECLICK; reused
 * 2026-07-12 for the "use item on NPC/object" packets, since {@code client.menuAction} cannot
 * synthesize those — see memory "menuaction-requires-existing-entry").
 *
 * <p>How it works: the client's dw packet-writer ({@code PacketReflection.getPacketWriteObject()})
 * holds pending packets in its {@code ag} field — an {@code nu} (Collection) of {@code jr} nodes.
 * Each node keeps the packet bytes in {@code al.ak} (byte[]) and the length in {@code aq}
 * (encoded; real length = {@code aq * -1547427033} — this exact constant is what {@code dw.al()}/
 * {@code dw.av()} themselves use to decode {@code jr.aq} before copying, read directly out of their
 * disassembly rather than derived). We poll that list every client tick, dump any short packet we
 * haven't seen, and log the player's current walk destination alongside — so a deliberate
 * click-to-walk (or any other single deliberate action) can be matched to its packet by eye.
 *
 * <p>Field names here are specific to injected-client 1.12.31.1 (rev238) and were re-derived by
 * javap-ing that jar after the previous {@code ak}/{@code as}/{@code ay}/{@code ah} names (from an
 * older client build) started throwing {@code NoSuchMethodException}/wrong-type errors — the {@code
 * dw} class's pending-queue field is now named {@code ag} (type {@code nu}), not {@code ak} (which
 * is now an unrelated {@code int}). The output-batch capture ({@code dumpOutputBatch}) relied on a
 * {@code df.az} field that no longer exists in this form and has been removed rather than guessed.
 *
 * <p>Usage: enable this plugin, perform ONE deliberate action (e.g. click a tile to walk, or use an
 * item on an NPC/object), and read the console. Then disable this plugin.
 */
@PluginDescriptor(
        name = "A Packet Sniffer (diagnostic)",
        description = "Logs outgoing packet bytes to reverse the walk packet",
        tags = {"packet", "debug", "diagnostic"},
        enabledByDefault = false
)
public class PacketSnifferPlugin extends Plugin {

    // jr.aq is a doubly-encoded buffer offset; -1547427033 decodes it to a real byte count.
    // Read directly out of dw.al()/dw.av()'s own decode call sites, not independently derived.
    private static final int LENGTH_DECODE_MULTIPLIER = -1547427033;

    @Inject
    private Client client;

    @Inject
    private PacketSnifferConfig config;

    private Field dwAgField;       // dw.ag — nu (Collection) of pending packet nodes
    private Field jrAqField;       // jr.aq — encoded length
    private Field jrAlField;       // jr.al — xv buffer
    private Field jrAxField;       // jr.ax — jf packet definition
    private Field bufArrayField;   // xv/xm.ak (byte[])
    private Method nuToArray;      // nu.toArray()
    private final Map<Object, String> jfName = new IdentityHashMap<>(); // jf instance -> "jf.xx"
    private boolean resolved;

    // De-dupe: don't re-log the same node while it lingers in the queue across ticks.
    private final Set<String> recentlyLogged = new LinkedHashSet<>();
    private int ticksSinceClear;

    // Known high-frequency noise (mouse click/move, heartbeat) — identified from earlier captures.
    // Field names were "jb.xx" under the old (stale) mapping; re-verify against the "mapped N jf
    // packet definitions" log + a quiet baseline capture before trusting this list again.
    private static final Set<String> NOISE = Set.of("jf.ds", "jf.eg", "jf.bj", "jf.cn");

    @Provides
    PacketSnifferConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PacketSnifferConfig.class);
    }

    @Override
    protected void startUp() {
        resolved = false;
        recentlyLogged.clear();
        System.out.println("[PacketSniffer] started — click a tile to walk and watch for the MOVE packet");
    }

    @Override
    protected void shutDown() {
        System.out.println("[PacketSniffer] stopped");
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (!config.enabled()) {
            return;
        }
        dumpPendingPackets();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Clear the de-dupe set periodically so a repeated action can be re-captured.
        if (++ticksSinceClear >= 3) {
            recentlyLogged.clear();
            ticksSinceClear = 0;
        }
        // Also log the live destination each game tick so it's easy to line up with packets.
        if (config.enabled()) {
            dumpPendingPackets();
            LocalPoint dest = client.getLocalDestinationLocation();
            if (dest != null) {
                WorldPoint w = WorldPoint.fromLocal(client, dest);
                System.out.println("[PacketSniffer] destination set -> scene(" + dest.getSceneX() + ","
                        + dest.getSceneY() + ") world(" + w.getX() + "," + w.getY() + ")");
            }
        }
    }

    private void dumpPendingPackets() {
        if (!resolve()) {
            return;
        }
        try {
            Object dw = PacketReflection.getPacketWriteObject();
            if (dw == null) {
                return;
            }
            Object nuList = dwAgField.get(dw);
            if (nuList == null) {
                return;
            }
            Object[] nodes = (Object[]) nuToArray.invoke(nuList);
            for (Object node : nodes) {
                if (node == null || !jrAqField.getDeclaringClass().isInstance(node)) {
                    continue;
                }
                int encodedLen = jrAqField.getInt(node);
                int len = encodedLen * LENGTH_DECODE_MULTIPLIER;
                Object buffer = jrAlField.get(node);
                if (buffer == null) {
                    continue;
                }
                byte[] arr = (byte[]) bufArrayField.get(buffer);
                if (arr == null || len <= 0 || len > arr.length || len > 160) {
                    continue;
                }
                // Identify the packet by its jf definition (no decryption needed).
                Object jfDef = jrAxField.get(node);
                String name = jfName.getOrDefault(jfDef, "jf.?");
                if (NOISE.contains(name)) {
                    continue; // skip mouse/heartbeat traffic — we want the walk packet
                }
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < len; i++) {
                    hex.append(String.format("%02X ", arr[i] & 0xFF));
                }
                String sig = name + ":" + len + ":" + hex;
                if (recentlyLogged.add(sig)) {
                    System.out.println("[PacketSniffer] OUT " + name + " len=" + len + " bytes=[ " + hex + "]");
                }
            }
        } catch (Exception e) {
            System.out.println("[PacketSniffer] error: " + e);
        }
    }

    private boolean resolve() {
        if (resolved) {
            return dwAgField != null;
        }
        resolved = true;
        try {
            Object dw = PacketReflection.getPacketWriteObject();
            if (dw == null) {
                return false;
            }
            dwAgField = dw.getClass().getDeclaredField("ag");
            dwAgField.setAccessible(true);

            Object nuList = dwAgField.get(dw);
            nuToArray = nuList.getClass().getMethod("toArray");

            // Resolve jr (node) + buffer fields from the packet-buffer-node class the framework knows.
            Class<?> jrClass = PacketReflection.getPacketBufferNodeClass();
            jrAqField = jrClass.getDeclaredField("aq");
            jrAqField.setAccessible(true);
            jrAlField = jrClass.getDeclaredField("al");
            jrAlField.setAccessible(true);
            jrAxField = jrClass.getDeclaredField("ax"); // jf packet definition
            jrAxField.setAccessible(true);

            // Build an identity map of every static jf.* packet definition -> its field name,
            // so each captured node can be labelled with its exact packet (e.g. "jf.ea").
            Class<?> jfClass = PacketReflection.getClientPacketClass();
            jfName.clear();
            for (Field f : jfClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == jfClass) {
                    f.setAccessible(true);
                    Object def = f.get(null);
                    if (def != null) {
                        jfName.put(def, "jf." + f.getName());
                    }
                }
            }
            System.out.println("[PacketSniffer] mapped " + jfName.size() + " jf packet definitions");

            // Find the buffer's byte[] field ("ak") — it lives on xm (superclass of xv).
            Class<?> bufClass = jrAlField.getType();
            bufArrayField = findArrayField(bufClass);
            if (bufArrayField == null) {
                System.out.println("[PacketSniffer] could not find byte[] field on " + bufClass.getName());
                return false;
            }

            System.out.println("[PacketSniffer] resolved: dw=" + dw.getClass().getSimpleName()
                    + " list=" + nuList.getClass().getSimpleName()
                    + " node=" + jrClass.getSimpleName()
                    + " buf=" + bufClass.getSimpleName()
                    + " arrayField=" + bufArrayField.getName());
            return true;
        } catch (Exception e) {
            System.out.println("[PacketSniffer] resolve failed: " + e);
            dwAgField = null;
            return false;
        }
    }

    /** Finds the (first) byte[] field on the class or its superclasses. */
    private static Field findArrayField(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == byte[].class) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }

}
