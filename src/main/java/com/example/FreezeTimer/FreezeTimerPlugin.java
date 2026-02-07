package com.example.FreezeTimer;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@PluginDescriptor(
        name = "A Freeze Timer",
        description = "Freeze timers",
        tags = {"freeze", "timer", "ice", "barrage", "entangle", "combat", "magic"},
        hidden = false,
        enabledByDefault = false
)
public class FreezeTimerPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private FreezeTimerConfig config;

    @Inject
    private OverlayManager overlayManager;

    private FreezeTimerOverlay worldOverlay;
    private FreezeTimerPanel sidePanel;

    // Core freeze tracking
    private final Map<NPC, FreezeData> activeFreezes = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    @Provides
    FreezeTimerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FreezeTimerConfig.class);
    }

    /**
     * Conditional logging helper - only logs when debug logging is enabled
     */
    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[FreezeTimer] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("Freeze Timer plugin started");

        // Create and register overlays
        worldOverlay = new FreezeTimerOverlay(client, this, config);
        sidePanel = new FreezeTimerPanel(client, this, config);

        overlayManager.add(worldOverlay);
        overlayManager.add(sidePanel);

        log("Overlays registered");
    }

    @Override
    protected void shutDown() {
        log("Freeze Timer plugin stopped");

        // Unregister overlays
        overlayManager.remove(worldOverlay);
        overlayManager.remove(sidePanel);

        // Clear state
        activeFreezes.clear();
        tickCounter = 0;

        log("Overlays unregistered and state cleared");
    }

    /**
     * Detects when the local player casts a freeze spell
     */
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        try {
            // Only track local player's casts
            if (event.getActor() != client.getLocalPlayer()) {
                return;
            }

            Player player = client.getLocalPlayer();
            if (player == null) {
                return;
            }

            int animation = player.getAnimation();

            // Check if animation matches a freeze spell
            FreezeData.FreezeSpell spell = FreezeData.FreezeSpell.fromAnimation(animation);
            if (spell == null) {
                return;
            }

            // Get target NPC
            Actor target = player.getInteracting();
            if (!(target instanceof NPC)) {
                log("Freeze spell animation detected but no NPC target (animation: " + animation + ")");
                return;
            }

            NPC npc = (NPC) target;

            // Create or update freeze data
            FreezeData freezeData = new FreezeData(npc, spell);
            activeFreezes.put(npc, freezeData);

            String npcName = npc.getName() != null ? npc.getName() : "Unknown";
            log("Freeze applied: " + spell.getDisplayName() + " on " + npcName +
                    " (ID: " + npc.getId() + ") - Duration: " + spell.getDurationMs() + "ms");

        } catch (Exception e) {
            log("Exception in onAnimationChanged: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Periodic cleanup of expired freeze timers
     */
    @Subscribe
    public void onGameTick(GameTick event) {
        try {
            tickCounter++;

            // Cleanup expired freezes every N ticks
            if (tickCounter % config.cleanupInterval() == 0) {
                cleanupExpiredFreezes();
            }

        } catch (Exception e) {
            log("Exception in onGameTick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove freeze data when NPC despawns
     */
    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        try {
            NPC npc = event.getNpc();
            if (activeFreezes.containsKey(npc)) {
                String npcName = npc.getName() != null ? npc.getName() : "Unknown";
                log("Removing freeze data - NPC despawned: " + npcName + " (ID: " + npc.getId() + ")");
                activeFreezes.remove(npc);
            }

        } catch (Exception e) {
            log("Exception in onNpcDespawned: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clear all freeze data on logout/world hop
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        try {
            GameState state = event.getGameState();

            if (state == GameState.LOGGING_IN || state == GameState.HOPPING) {
                int count = activeFreezes.size();
                activeFreezes.clear();
                tickCounter = 0;
                log("Cleared " + count + " freeze entries due to logout/hop");
            }

        } catch (Exception e) {
            log("Exception in onGameStateChanged: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove expired freeze entries
     */
    private void cleanupExpiredFreezes() {
        List<NPC> toRemove = new ArrayList<>();

        for (Map.Entry<NPC, FreezeData> entry : activeFreezes.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }

        for (NPC npc : toRemove) {
            String npcName = npc.getName() != null ? npc.getName() : "Unknown";
            log("Removed expired freeze from " + npcName);
            activeFreezes.remove(npc);
        }

        if (!toRemove.isEmpty()) {
            log("Cleanup removed " + toRemove.size() + " expired freeze(s)");
        }
    }

    /**
     * Getter for overlays to access active freeze data
     */
    public Map<NPC, FreezeData> getActiveFreezes() {
        return activeFreezes;
    }
}
