package com.example.CrashedStar;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.InteractionApi.TileObjectInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Optional;

@PluginDescriptor(
        name = "A Crashed Stars",
        description = "Crashed star helper",
        tags = {"crashed", "star", "mining", "skilling"},
        hidden = false
)
public class CrashedStarPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private CrashedStarConfig config;

    // Crashed star object IDs (Size 9 to Size 1)
    private static final int[] CRASHED_STAR_IDS = {
            41020, // Size 9
            41021, // Size 8
            41223, // Size 7
            41224, // Size 6
            41225, // Size 5
            41226, // Size 4
            41227, // Size 3
            41228, // Size 2
            41229  // Size 1
    };

    // Mining animation IDs
    private static final int[] MINING_ANIMATIONS = {
            7140, // Crashed star mining (dragon pickaxe or general)
            6752, // Dragon pickaxe
            8347, // 3rd age pickaxe
            4482, // Rune pickaxe
            626,  // Generic mining
            628   // Another mining animation
    };

    private static final int SAPPHIRE_ID = 1623;

    // State tracking
    private boolean wasMining = false;
    private int idleTickCount = 0;
    private static final int IDLE_TICKS_BEFORE_RECLICK = 2; // Wait 2 ticks before re-clicking
    private int tickCounter = 0; // For periodic logging

    @Provides
    CrashedStarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CrashedStarConfig.class);
    }

    /**
     * Conditional logging helper
     */
    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[CrashedStar] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("Crashed Star Miner started");
        resetState();
    }

    @Override
    protected void shutDown() {
        log("Crashed Star Miner stopped");
        resetState();
    }

    private void resetState() {
        wasMining = false;
        idleTickCount = 0;
        tickCounter = 0;
    }

    /**
     * Check if the player is currently performing a mining animation
     */
    private boolean isMining() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        int animation = player.getAnimation();
        for (int miningAnim : MINING_ANIMATIONS) {
            if (animation == miningAnim) {
                return true;
            }
        }
        return false;
    }

    /**
     * Log all nearby tile objects for debugging
     */
    private void logNearbyObjects() {
        log("=== SCANNING ALL NEARBY TILE OBJECTS ===");
        try {
            java.util.List<TileObject> allObjects = TileObjects.search().result();
            log("Total tile objects found: " + allObjects.size());

            int count = 0;
            for (TileObject obj : allObjects) {
                if (count < 20) { // Log first 20 objects
                    int id = obj.getId();
                    log("  Object ID: " + id + " | Location: " + obj.getWorldLocation());
                    count++;
                }
            }

            // Check for objects matching crashed star IDs
            for (TileObject obj : allObjects) {
                int id = obj.getId();
                for (int starId : CRASHED_STAR_IDS) {
                    if (id == starId) {
                        log("  ★★★ FOUND MATCHING STAR ID: " + id + " | Location: " + obj.getWorldLocation());
                    }
                }
            }
        } catch (Exception e) {
            log("Error scanning objects: " + e.getMessage());
            e.printStackTrace();
        }
        log("=== END SCAN ===");
    }

    /**
     * Find the nearest crashed star
     */
    private Optional<TileObject> findCrashedStar() {
        for (int starId : CRASHED_STAR_IDS) {
            // Create a fresh query for each ID search
            TileObjectQuery query = TileObjects.search();
            Optional<TileObject> star = query.withId(starId).nearestToPlayer();
            if (star.isPresent()) {
                log("Found star (ID: " + starId + ")");
                return star;
            }
        }

        log("No star found - scanning nearby objects...");
        logNearbyObjects(); // Debug logging to help identify issues
        return Optional.empty();
    }

    /**
     * Click on the crashed star
     */
    private void clickStar() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        Optional<TileObject> star = findCrashedStar();
        if (!star.isPresent()) {
            log("Cannot click star - no star found");
            return;
        }

        log("Clicking crashed star");
        TileObjectInteraction.interact(star.get(), "Mine");
    }

    /**
     * Drop sapphires from inventory
     */
    private void dropSapphires() {
        if (!config.dropSapphires()) {
            return;
        }

        try {
            Widget sapphire = Inventory.search().withId(SAPPHIRE_ID).first().orElse(null);
            if (sapphire != null) {
                log("Dropping sapphire");
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(sapphire, "Drop");
            }
        } catch (Exception e) {
            log("Error dropping sapphire: " + e.getMessage());
        }
    }

    /**
     * Check if inventory is full
     */
    private boolean isInventoryFull() {
        return Inventory.getEmptySlots() == 0;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!config.enableAutoMining()) {
            return;
        }

        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        boolean currentlyMining = isMining();

        if (currentlyMining) {
            log("Mining detected");
            wasMining = true;
            idleTickCount = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.enableAutoMining() || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        tickCounter++;
        boolean currentlyMining = isMining();

        // Periodic star detection check (every 30 ticks / ~18 seconds) - for debugging
        if (tickCounter % 30 == 0) {
            Optional<TileObject> star = findCrashedStar();
            if (star.isPresent()) {
                log("Periodic check - star is present nearby");
            }
        }

        // If we were mining but now we're not
        if (wasMining && !currentlyMining) {
            idleTickCount++;
            log("Mining stopped, idle ticks: " + idleTickCount);

            // Wait a couple ticks to confirm we're actually idle (not just between animations)
            if (idleTickCount >= IDLE_TICKS_BEFORE_RECLICK) {
                log("Re-clicking star...");

                // Check if inventory is full and drop sapphires if needed
                if (isInventoryFull()) {
                    log("Inventory full - dropping sapphires");
                    dropSapphires();
                }

                // Check if the star still exists
                Optional<TileObject> star = findCrashedStar();
                if (star.isPresent()) {
                    clickStar();
                    wasMining = false; // Reset so we wait for mining to start again
                } else {
                    log("Star depleted - stopping");
                    wasMining = false;
                }

                idleTickCount = 0;
            }
        } else if (currentlyMining) {
            // Reset idle counter if we're mining
            idleTickCount = 0;
        }
    }
}
