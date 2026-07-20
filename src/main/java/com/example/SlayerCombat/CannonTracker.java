package com.example.SlayerCombat;

import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

import java.lang.reflect.Field;

/**
 * Reads live Dwarven multicannon state from RuneLite's built-in "Cannon" plugin
 * ({@code net.runelite.client.plugins.cannon.CannonPlugin}), which already tracks the
 * cannonball count for its overlay. That state is package-private, so we read it reflectively
 * off the running plugin instance.
 *
 * <p>Requirements/caveats:
 * <ul>
 *   <li>The built-in <b>Cannon</b> plugin must be enabled — otherwise its counter never updates
 *       and {@link #cballsLeft()} returns -1.</li>
 *   <li>Field names ({@code cballsLeft}/{@code cannonPlaced}) are RuneLite internals; if a client
 *       update renames them this returns -1 and {@link #describeFields()} can dump the real names.</li>
 * </ul>
 */
public final class CannonTracker {

    private static final String CANNON_PLUGIN_CLASS = "net.runelite.client.plugins.cannon.CannonPlugin";

    private static Plugin cannonPlugin;
    private static Field cballsField;
    private static Field placedField;
    private static boolean resolveAttempted;

    private CannonTracker() {
    }

    /** Cannonballs currently loaded in the cannon, or -1 if the count is unavailable. */
    public static int cballsLeft() {
        if (!resolve()) {
            return -1;
        }
        try {
            return cballsField.getInt(cannonPlugin);
        } catch (IllegalAccessException e) {
            return -1;
        }
    }

    /** True if the Cannon plugin reports a placed cannon; false if not placed or unavailable. */
    public static boolean cannonPlaced() {
        if (!resolve() || placedField == null) {
            return false;
        }
        try {
            return placedField.getBoolean(cannonPlugin);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    /** True once the CannonPlugin instance and its {@code cballsLeft} field were located. */
    public static boolean isAvailable() {
        return resolve();
    }

    private static boolean resolve() {
        if (cannonPlugin != null && cballsField != null) {
            return true;
        }
        if (resolveAttempted && cannonPlugin == null) {
            // Plugin not found yet (e.g. not loaded). Retry — it may get enabled later.
            findPlugin();
            return cannonPlugin != null && cballsField != null;
        }
        resolveAttempted = true;
        findPlugin();
        if (cannonPlugin == null) {
            return false;
        }
        Class<?> cls = cannonPlugin.getClass();
        cballsField = declaredField(cls, "cballsLeft");
        placedField = declaredField(cls, "cannonPlaced");
        return cballsField != null;
    }

    private static void findPlugin() {
        try {
            PluginManager pluginManager = RuneLite.getInjector().getInstance(PluginManager.class);
            for (Plugin p : pluginManager.getPlugins()) {
                if (p.getClass().getName().equals(CANNON_PLUGIN_CLASS)) {
                    cannonPlugin = p;
                    return;
                }
            }
        } catch (Exception ignored) {
            // injector/plugin manager not ready
        }
    }

    private static Field declaredField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** Comma-separated declared field names of the located CannonPlugin (debug aid if fields move). */
    public static String describeFields() {
        if (!resolve() && cannonPlugin == null) {
            return "CannonPlugin not found";
        }
        StringBuilder sb = new StringBuilder();
        for (Field f : cannonPlugin.getClass().getDeclaredFields()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(f.getType().getSimpleName()).append(' ').append(f.getName());
        }
        return sb.toString();
    }
}
