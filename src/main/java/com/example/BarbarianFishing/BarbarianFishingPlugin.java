package com.example.BarbarianFishing;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.InteractionApi.HumanLikeDropper;
import com.example.InteractionApi.NPCInteraction;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.BarbarianFishing.BarbarianFishingConstants.*;

@PluginDescriptor(
        name = "A Barbarian Fishing Auto",
        description = "Automatically fishes and drops fish at barbarian fishing spots",
        tags = {"barbarian", "fishing", "skilling", "training"},
        hidden = false,
        enabledByDefault = false
)
public class BarbarianFishingPlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private BarbarianFishingConfig config;

    // State tracking
    private PluginState currentState = PluginState.FISHING;
    private boolean wasFishing = false;
    private int idleTickCount = 0;
    private int ticksSinceLastInteraction = 0;
    private int interactionAttempts = 0;
    private NPC currentTarget = null;
    private int tickCounter = 0;
    private HumanLikeDropper dropper = null; // Human-like item dropper

    @Provides
    BarbarianFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BarbarianFishingConfig.class);
    }

    /**
     * Conditional logging helper
     */
    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][BarbarianFishing] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("=================================================");
        log("Barbarian Fishing Auto STARTED");
        log("=================================================");
        resetState();
    }

    @Override
    protected void shutDown() {
        log("=================================================");
        log("Barbarian Fishing Auto STOPPED");
        log("=================================================");
        resetState();
    }

    private void resetState() {
        currentState = PluginState.FISHING;
        wasFishing = false;
        idleTickCount = 0;
        ticksSinceLastInteraction = 0;
        interactionAttempts = 0;
        currentTarget = null;
        dropper = null;
        log("State reset");
    }

    /**
     * Get current player animation (for debugging)
     */
    private int getCurrentAnimation() {
        Player player = client.getLocalPlayer();
        return player != null ? player.getAnimation() : -1;
    }

    /**
     * Check if the player is currently performing a fishing animation
     */
    private boolean isFishing() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        int animation = player.getAnimation();
        for (int fishingAnim : FISHING_ANIMATIONS) {
            if (animation == fishingAnim) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if inventory is full
     */
    private boolean isInventoryFull() {
        return Inventory.getEmptySlots() == 0;
    }

    /**
     * Check if inventory has any fish
     */
    private boolean hasFish() {
        return (config.dropLeapingTrout() && Inventory.search().withId(LEAPING_TROUT_ID).first().isPresent()) ||
               (config.dropLeapingSalmon() && Inventory.search().withId(LEAPING_SALMON_ID).first().isPresent()) ||
               (config.dropLeapingSturgeon() && Inventory.search().withId(LEAPING_STURGEON_ID).first().isPresent());
    }

    /**
     * Find the nearest fishing spot
     */
    private Optional<NPC> findNearestFishingSpot() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        return NPCs.search()
                .withAction("Use-rod")
                .nearestToPlayer();
    }

    /**
     * Click on a fishing spot
     */
    private void clickFishingSpot(NPC fishingSpot) {
        currentTarget = fishingSpot;
        log(String.format("Clicking fishing spot (ID: %d) at %s",
                fishingSpot.getId(), fishingSpot.getWorldLocation()));

        NPCInteraction.interact(fishingSpot, "Use-rod");
        ticksSinceLastInteraction = 0;
        interactionAttempts++;
    }

    /**
     * Get list of fish IDs that should be dropped based on config
     */
    private java.util.List<Integer> getFishIdsToDrop() {
        java.util.List<Integer> fishIds = new java.util.ArrayList<>();
        if (config.dropLeapingTrout()) {
            fishIds.add(LEAPING_TROUT_ID);
        }
        if (config.dropLeapingSalmon()) {
            fishIds.add(LEAPING_SALMON_ID);
        }
        if (config.dropLeapingSturgeon()) {
            fishIds.add(LEAPING_STURGEON_ID);
        }
        return fishIds;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!config.enableAutoFishing()) {
            return;
        }

        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        boolean currentlyFishing = isFishing();
        int animation = getCurrentAnimation();

        if (currentlyFishing) {
            if (!wasFishing) {
                log(String.format("*** STARTED FISHING - Animation: %d", animation));
            }
            wasFishing = true;
            idleTickCount = 0;
        } else {
            // Log animation changes even when not fishing (for debugging)
            if (animation != -1 && animation != 808 && animation != 813) {
                log(String.format("Animation changed to: %d (not recognized as fishing)", animation));
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (!config.enableAutoFishing()) {
            return;
        }

        tickCounter++;
        ticksSinceLastInteraction++;
        boolean currentlyFishing = isFishing();
        int animation = getCurrentAnimation();

        // Log animation and state every 5 ticks for debugging
        if (tickCounter % 5 == 0) {
            log(String.format("[Tick %d] Animation: %d | Fishing: %s | WasFishing: %s | State: %s | InvSlots: %d",
                    tickCounter, animation, currentlyFishing, wasFishing, currentState, Inventory.getEmptySlots()));

            // Alert if we see an unknown animation that's not idle
            if (animation != -1 && !currentlyFishing && animation != 808 && animation != 813) {
                log(String.format("??? UNKNOWN ANIMATION: %d - You may need to add this to FISHING_ANIMATIONS array", animation));
            }
        }

        // Handle state machine
        switch (currentState) {
            case FISHING:
                handleFishingState(currentlyFishing);
                break;

            case DROPPING:
                handleDroppingState();
                break;
        }
    }

    /**
     * Handle the FISHING state
     */
    private void handleFishingState(boolean currentlyFishing) {
        // Check if inventory is full and we should drop
        if (isInventoryFull() && config.autoDrop()) {
            log("!!! INVENTORY FULL - Switching to dropping mode");
            currentState = PluginState.DROPPING;
            wasFishing = false;
            return;
        }

        // If we're not fishing and haven't started yet
        if (!currentlyFishing && !wasFishing) {
            // Only try to click if cooldown has passed
            if (ticksSinceLastInteraction >= INTERACTION_COOLDOWN) {
                Optional<NPC> nearestSpot = findNearestFishingSpot();
                if (nearestSpot.isPresent()) {
                    log(">>> Not fishing - clicking fishing spot to start");
                    clickFishingSpot(nearestSpot.get());
                } else {
                    log("!!! NO FISHING SPOT FOUND - Check IDs or get closer");
                }
            }
            return;
        }

        // If we were fishing but now we're not (stopped)
        if (wasFishing && !currentlyFishing) {
            idleTickCount++;
            log(String.format(">>> STOPPED FISHING - Idle tick %d", idleTickCount));

            // Wait a few ticks to confirm we're actually idle
            if (idleTickCount >= IDLE_TICKS_BEFORE_RECLICK) {
                log("!!! IDLE THRESHOLD REACHED - Finding next fishing spot");

                // Only interact if cooldown has passed
                if (ticksSinceLastInteraction >= INTERACTION_COOLDOWN) {
                    Optional<NPC> nearestSpot = findNearestFishingSpot();
                    if (nearestSpot.isPresent()) {
                        log(String.format(">>> CLICKING NEW FISHING SPOT: ID %d",
                                nearestSpot.get().getId()));
                        clickFishingSpot(nearestSpot.get());
                        wasFishing = false; // Reset so we wait for fishing to start again
                    } else {
                        log("!!! NO FISHING SPOT FOUND - Check IDs or get closer");
                        wasFishing = false;
                    }
                }

                idleTickCount = 0;
            }
        } else if (currentlyFishing) {
            // Reset idle counter if we're fishing
            if (idleTickCount > 0) {
                log("Fishing resumed, resetting idle counter");
            }
            idleTickCount = 0;
        }
    }

    /**
     * Handle the DROPPING state
     * Uses HumanLikeDropper to drop fish in zigzag pattern with random "missed" items
     */
    private void handleDroppingState() {
        // Initialize dropper on first entry to this state
        if (dropper == null) {
            log(">>> Initializing HumanLikeDropper for fish");
            dropper = new HumanLikeDropper(getFishIdsToDrop());
        }

        // Check if we still have fish to drop
        if (!hasFish()) {
            log(">>> All fish dropped - returning to fishing");
            currentState = PluginState.FISHING;
            dropper = null; // Reset for next time
            ticksSinceLastInteraction = 0;
            return;
        }

        // Drop next batch of fish (handles zigzag pattern, missed items, etc.)
        boolean hasMore = dropper.dropNextBatch();

        if (hasMore) {
            log(">>> Dropped batch of fish, more remaining");
            ticksSinceLastInteraction = 0;
        } else {
            // Dropper says no more items, but double-check
            if (!hasFish()) {
                log(">>> All fish dropped - returning to fishing");
                currentState = PluginState.FISHING;
                dropper = null;
                ticksSinceLastInteraction = 0;
            }
        }
    }
}
