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
 * reverse the rev-238 "walk to tile" (MOVE_GAMECLICK) packet that has no menuAction equivalent.
 *
 * <p>How it works: the client's df packet-writer ({@code PacketReflection.getPacketWriteObject()})
 * holds pending packets in its {@code ak} field — a {@code no} linked list of {@code jm} nodes.
 * Each node keeps the packet bytes in {@code ay.al} (byte[]) and the length in {@code as}
 * (encoded; real length = {@code as * 1810843567}). We poll that list every client tick, dump any
 * short packet we haven't seen, and log the player's current walk destination alongside — so a
 * deliberate click-to-walk can be matched to its packet by finding the destination scene coords in
 * the payload.
 *
 * <p>Usage: enable this plugin + the SlayerCombat debug logging, stand still, click ONE tile to
 * walk, and read the console. The packet whose payload contains the destination's scene X/Y is the
 * walk packet. Then disable this plugin.
 */
@PluginDescriptor(
        name = "A Packet Sniffer (diagnostic)",
        description = "Logs outgoing packet bytes to reverse the walk packet",
        tags = {"packet", "debug", "diagnostic"},
        enabledByDefault = false
)
public class PacketSnifferPlugin extends Plugin {

    // jm.as is stored as (realLength * 1481414135); 1810843567 is its modular inverse, so
    // realLength = as * 1810843567 (mod 2^32, i.e. normal int overflow). Verified in df.az/df.ay.
    private static final int LENGTH_DECODE_MULTIPLIER = 1810843567;

    @Inject
    private Client client;

    @Inject
    private PacketSnifferConfig config;

    private Field dfAkField;       // no list of pending packet nodes
    private Field jmAsField;       // jm.as  (encoded length)
    private Field jmAyField;       // jm.ay  (xj buffer)
    private Field jmAhField;       // jm.ah  (jb packet definition)
    private Field bufArrayField;   // xi/xj.al (byte[])
    private Field dfAzField;       // df.az (xi output buffer that holds the flushed batch)
    private Field outArrayField;   // xi.al (byte[]) on the output buffer
    private Field outOffsetField;  // xi.au (encoded offset/length) on the output buffer
    private Method noToArray;      // no.toArray()
    private final Map<Object, String> jbName = new IdentityHashMap<>(); // jb instance -> "jb.xx"
    private String lastBatchHex = "";
    private boolean resolved;

    // De-dupe: don't re-log the same node while it lingers in the queue across ticks.
    private final Set<String> recentlyLogged = new LinkedHashSet<>();
    private int ticksSinceClear;

    // Known high-frequency noise (mouse click/move, heartbeat) — identified from earlier captures.
    private static final Set<String> NOISE = Set.of("jb.ds", "jb.eg", "jb.bj", "jb.cn");

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
        dumpOutputBatch();
    }

    /**
     * Dumps the full batch of bytes in the output buffer ({@code df.az}) whenever it changes — this
     * holds everything the client just flushed to the socket, including the move packet that the
     * queue poll keeps missing. The move packet's payload (plaintext) contains the destination
     * scene coords, so we can find it by eye against the logged destination.
     */
    private void dumpOutputBatch() {
        if (dfAzField == null) {
            return;
        }
        try {
            Object df = PacketReflection.getPacketWriteObject();
            Object outBuf = dfAzField.get(df);
            if (outBuf == null) {
                return;
            }
            byte[] arr = (byte[]) outArrayField.get(outBuf);
            int encodedOff = outOffsetField.getInt(outBuf);
            int len = encodedOff * -661977895; // decode xi.au -> real byte count (indexMultiplier)
            if (arr == null || len <= 0) {
                return;
            }
            len = Math.min(len, Math.min(arr.length, 200));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < len; i++) {
                hex.append(String.format("%02X ", arr[i] & 0xFF));
            }
            String h = hex.toString();
            if (!h.equals(lastBatchHex)) {
                lastBatchHex = h;
                System.out.println("[PacketSniffer] BATCH len=" + len + " bytes=[ " + h + "]");
            }
        } catch (Exception e) {
            System.out.println("[PacketSniffer] batch error: " + e);
        }
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
            Object df = PacketReflection.getPacketWriteObject();
            if (df == null) {
                return;
            }
            Object noList = dfAkField.get(df);
            if (noList == null) {
                return;
            }
            Object[] nodes = (Object[]) noToArray.invoke(noList);
            for (Object node : nodes) {
                if (node == null || !jmAsField.getDeclaringClass().isInstance(node)) {
                    continue;
                }
                int encodedLen = jmAsField.getInt(node);
                int len = encodedLen * LENGTH_DECODE_MULTIPLIER;
                Object buffer = jmAyField.get(node);
                if (buffer == null) {
                    continue;
                }
                byte[] arr = (byte[]) bufArrayField.get(buffer);
                if (arr == null || len <= 0 || len > arr.length || len > 160) {
                    continue;
                }
                // Identify the packet by its jb definition (no decryption needed).
                Object jbDef = jmAhField.get(node);
                String name = jbName.getOrDefault(jbDef, "jb.?");
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
            return dfAkField != null;
        }
        resolved = true;
        try {
            Object df = PacketReflection.getPacketWriteObject();
            if (df == null) {
                return false;
            }
            dfAkField = df.getClass().getDeclaredField("ak");
            dfAkField.setAccessible(true);

            Object noList = dfAkField.get(df);
            noToArray = noList.getClass().getMethod("toArray");

            // Resolve jm (node) + buffer fields from the packet-buffer-node class the framework knows.
            Class<?> jmClass = PacketReflection.getPacketBufferNodeClass();
            jmAsField = jmClass.getDeclaredField("as");
            jmAsField.setAccessible(true);
            jmAyField = jmClass.getDeclaredField("ay");
            jmAyField.setAccessible(true);
            jmAhField = jmClass.getDeclaredField("ah"); // jb packet definition
            jmAhField.setAccessible(true);

            // Build an identity map of every static jb.* packet definition -> its field name,
            // so each captured node can be labelled with its exact packet (e.g. "jb.af").
            Class<?> jbClass = PacketReflection.getClientPacketClass();
            jbName.clear();
            for (Field f : jbClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == jbClass) {
                    f.setAccessible(true);
                    Object def = f.get(null);
                    if (def != null) {
                        jbName.put(def, "jb." + f.getName());
                    }
                }
            }
            System.out.println("[PacketSniffer] mapped " + jbName.size() + " jb packet definitions");

            // Find the buffer's byte[] field ("al") — it lives on xi (superclass of xj).
            Class<?> bufClass = jmAyField.getType();
            bufArrayField = findArrayField(bufClass);
            if (bufArrayField == null) {
                System.out.println("[PacketSniffer] could not find byte[] field on " + bufClass.getName());
                return false;
            }

            // Output buffer: df.az (xi) holds the last flushed batch. Resolve its byte[] + offset.
            dfAzField = df.getClass().getDeclaredField("az");
            dfAzField.setAccessible(true);
            Class<?> outClass = dfAzField.getType();
            outArrayField = findArrayField(outClass);
            outOffsetField = outClass.getDeclaredField("au");
            outOffsetField.setAccessible(true);
            System.out.println("[PacketSniffer] resolved: df=" + df.getClass().getSimpleName()
                    + " list=" + noList.getClass().getSimpleName()
                    + " node=" + jmClass.getSimpleName()
                    + " buf=" + bufClass.getSimpleName()
                    + " arrayField=" + bufArrayField.getName());
            return true;
        } catch (Exception e) {
            System.out.println("[PacketSniffer] resolve failed: " + e);
            dfAkField = null;
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
