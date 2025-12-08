package com.example.BarbarianFishing;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.InteractionApi.HumanLikeDelay;
import com.example.InteractionApi.HumanLikeDropper;
import com.example.InteractionApi.NPCInteraction;
import com.example.InteractionApi.RealisticClickHelper;
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
    private int randomSpotSwitchDelay = 0; // Random delay for current spot switch (human-like)
    private int randomPostDropDelay = 0; // Random delay after dropping fish (human-like)
    private int postDropTickCount = 0; // Tick counter for post-drop delay
    private int currentInteractionCooldown = 0; // Variable cooldown for current interaction (human-like)
    private int randomInventoryFullDelay = 0; // Random delay after detecting full inventory (human-like)
    private int inventoryFullTickCount = 0; // Tick counter for inventory full delay
    private boolean inventoryFullDetected = false; // Flag to track if we've detected full inventory

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

        // Enable coordinate logging if debug mode is on
        RealisticClickHelper.setLoggingEnabled(config.debugLogging());
        if (config.debugLogging()) {
            log("Coordinate logging enabled");
        }

        resetState();
    }

    @Override
    protected void shutDown() {
        log("=================================================");
        log("Barbarian Fishing Auto STOPPED");
        log("=================================================");

        // Disable coordinate logging
        RealisticClickHelper.setLoggingEnabled(false);

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
        randomSpotSwitchDelay = 0;
        randomPostDropDelay = 0;
        postDropTickCount = 0;
        currentInteractionCooldown = 0;
        randomInventoryFullDelay = 0;
        inventoryFullTickCount = 0;
        inventoryFullDetected = false;
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

        // Generate new weighted random cooldown for next interaction
        currentInteractionCooldown = HumanLikeDelay.generate(HumanLikeDelay.INTERACTION_COOLDOWN);
        log(String.format("Generated interaction cooldown: %d ticks", currentInteractionCooldown));

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

        if (currentlyFishing) {
            wasFishing = true;
            idleTickCount = 0;
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

        // Update coordinate logging if debug setting changed
        RealisticClickHelper.setLoggingEnabled(config.debugLogging());

        tickCounter++;
        ticksSinceLastInteraction++;
        boolean currentlyFishing = isFishing();

        // Handle state machine
        switch (currentState) {
            case FISHING:
                handleFishingState(currentlyFishing);
                break;

            case DROPPING:
                handleDroppingState();
                break;

            case POST_DROP_DELAY:
                handlePostDropDelayState();
                break;
        }
    }

    /**
     * Handle the FISHING state
     */
    private void handleFishingState(boolean currentlyFishing) {
        // Check if inventory is full and we should drop
        if (isInventoryFull() && config.autoDrop()) {
            // First tick detecting full inventory - generate weighted random delay
            if (!inventoryFullDetected) {
                randomInventoryFullDelay = HumanLikeDelay.generate(HumanLikeDelay.INVENTORY_FULL);
                log(String.format("Generated inventory full reaction delay: %d ticks", randomInventoryFullDelay));
                inventoryFullDetected = true;
                inventoryFullTickCount = 0;
            }

            inventoryFullTickCount++;
            log(String.format("Inventory full reaction delay: %d/%d", inventoryFullTickCount, randomInventoryFullDelay));

            // Wait for random delay before switching to dropping
            if (inventoryFullTickCount >= randomInventoryFullDelay) {
                log("Inventory full reaction delay complete");
                currentState = PluginState.DROPPING;
                wasFishing = false;
                inventoryFullDetected = false;
                inventoryFullTickCount = 0;
                randomInventoryFullDelay = 0;
            }
            return;
        }

        // Reset inventory full tracking if inventory is no longer full
        if (!isInventoryFull() && inventoryFullDetected) {
            inventoryFullDetected = false;
            inventoryFullTickCount = 0;
            randomInventoryFullDelay = 0;
        }

        // If we're not fishing and haven't started yet
        if (!currentlyFishing && !wasFishing) {
            // Only try to click if cooldown has passed
            if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                Optional<NPC> nearestSpot = findNearestFishingSpot();
                if (nearestSpot.isPresent()) {
                    clickFishingSpot(nearestSpot.get());
                }
            }
            return;
        }

        // If we were fishing but now we're not (stopped)
        if (wasFishing && !currentlyFishing) {
            // First tick we notice we stopped - generate weighted random delay for this spot switch
            if (idleTickCount == 0) {
                randomSpotSwitchDelay = HumanLikeDelay.generate(HumanLikeDelay.RESOURCE_DEPLETION);
                log(String.format("Generated spot switch delay: %d ticks", randomSpotSwitchDelay));
            }

            idleTickCount++;
            log(String.format("Spot switch delay: %d/%d", idleTickCount, randomSpotSwitchDelay));

            // Wait for the random delay before clicking new spot
            if (idleTickCount >= randomSpotSwitchDelay) {
                // Only interact if cooldown has passed
                if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                    Optional<NPC> nearestSpot = findNearestFishingSpot();
                    if (nearestSpot.isPresent()) {
                        clickFishingSpot(nearestSpot.get());
                        wasFishing = false; // Reset so we wait for fishing to start again
                    } else {
                        wasFishing = false;
                    }
                }

                idleTickCount = 0;
                randomSpotSwitchDelay = 0; // Reset for next spot switch
            }
        } else if (currentlyFishing) {
            // Reset idle counter if we're fishing
            idleTickCount = 0;
            randomSpotSwitchDelay = 0;
        }
    }

    /**
     * Handle the DROPPING state
     * Uses HumanLikeDropper to drop fish in zigzag pattern with random "missed" items
     */
    private void handleDroppingState() {
        // Initialize dropper on first entry to this state
        if (dropper == null) {
            dropper = new HumanLikeDropper(getFishIdsToDrop())
                    .setLogging(false); // Disable dropper logging
        }

        // Check if we still have fish to drop
        if (!hasFish()) {
            // Generate weighted random delay before returning to fishing
            randomPostDropDelay = HumanLikeDelay.generate(HumanLikeDelay.POST_ACTIVITY_RESUME);
            log(String.format("Generated post-drop delay: %d ticks", randomPostDropDelay));

            currentState = PluginState.POST_DROP_DELAY;
            dropper = null; // Reset for next time
            postDropTickCount = 0;
            return;
        }

        // Drop next batch of fish (handles zigzag pattern, missed items, etc.)
        boolean hasMore = dropper.dropNextBatch();

        if (hasMore) {
            ticksSinceLastInteraction = 0;
        } else {
            // Dropper says no more items, but double-check
            if (!hasFish()) {
                // Generate weighted random delay before returning to fishing
                randomPostDropDelay = HumanLikeDelay.generate(HumanLikeDelay.POST_ACTIVITY_RESUME);
                log(String.format("Generated post-drop delay: %d ticks", randomPostDropDelay));

                currentState = PluginState.POST_DROP_DELAY;
                dropper = null;
                postDropTickCount = 0;
            }
        }
    }

    /**
     * Handle the POST_DROP_DELAY state
     * Waits for a random delay after dropping all fish before returning to fishing (human-like)
     */
    private void handlePostDropDelayState() {
        postDropTickCount++;
        log(String.format("Post-drop delay: %d/%d", postDropTickCount, randomPostDropDelay));

        if (postDropTickCount >= randomPostDropDelay) {
            log("Post-drop delay complete");
            currentState = PluginState.FISHING;
            postDropTickCount = 0;
            randomPostDropDelay = 0;
            ticksSinceLastInteraction = 0;
        }
    }
}
