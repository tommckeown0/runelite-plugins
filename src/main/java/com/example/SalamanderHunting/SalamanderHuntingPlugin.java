package com.example.SalamanderHunting;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.InteractionApi.HumanLikeDropper;
import com.example.InteractionApi.RealisticClickHelper;
import com.example.Packets.MousePackets;
import com.example.Packets.ObjectPackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.Point;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.SalamanderHunting.SalamanderHuntingConstants.*;

@PluginDescriptor(
    name = "A Salamander Helper",
    description = "Red salamander helper",
    tags = {"hunting", "salamander", "hunter"},
    hidden = false,
    enabledByDefault = false
)
public class SalamanderHuntingPlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private SalamanderHuntingConfig config;

    private TrapManager trapManager;
    private HumanLikeDropper releaser;
    private PluginState currentState = PluginState.IDLE;
    private int tickCounter = 0;
    private int actionCooldown = 0; // Ticks to wait before next action
    private TrapLocation currentTargetLocation = null;
    private TrapLocation pickupLocation = null; // Track which location we're picking up items from
    private int debugLogCounter = 0; // For periodic debug logging

    // Track last action to prevent duplicate clicks before game processes
    private TrapLocation lastActionLocation = null;
    private TrapState.TrapStatus lastActionInitialStatus = null; // The status when we sent the action - wait for it to change
    private int lastActionTick = 0; // Tick when action was sent - for timeout
    private static final int ACTION_TIMEOUT_TICKS = 15; // Max ticks to wait for action to complete

    enum PluginState {
        IDLE,               // Waiting / checking state
        HANDLING_TRAP,      // Setting, checking, or resetting a trap
        PICKING_UP_ITEMS,   // Picking up rope/net from fallen trap
        RELEASING           // Releasing salamanders from inventory
    }

    @Provides
    SalamanderHuntingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SalamanderHuntingConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][SalamanderHunting] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("=================================================");
        log("Salamander Hunting Plugin STARTED");
        log("=================================================");

        trapManager = new TrapManager(this::log);
        currentState = PluginState.IDLE;
        tickCounter = 0;
        actionCooldown = 0;
        currentTargetLocation = null;
        pickupLocation = null;
        debugLogCounter = 0;
        lastActionLocation = null;
        lastActionInitialStatus = null;
        lastActionTick = 0;
    }

    @Override
    protected void shutDown() {
        log("=================================================");
        log("Salamander Hunting Plugin STOPPED");
        log("=================================================");

        trapManager = null;
        releaser = null;
        currentState = PluginState.IDLE;
    }

    /**
     * Count salamanders in inventory.
     */
    private int getSalamanderCount() {
        return (int) Inventory.search()
            .filter(w -> w.getItemId() == RED_SALAMANDER)
            .result()
            .size();
    }

    /**
     * Check if we have rope and net in inventory.
     */
    private boolean hasSupplies() {
        boolean hasRope = Inventory.search()
            .filter(w -> w.getItemId() == ROPE)
            .first()
            .isPresent();
        boolean hasNet = Inventory.search()
            .filter(w -> w.getItemId() == SMALL_FISHING_NET)
            .first()
            .isPresent();
        return hasRope && hasNet;
    }

    /**
     * Check if we should release salamanders based on config threshold.
     */
    private boolean shouldReleaseSalamanders() {
        int salamanderCount = getSalamanderCount();
        int threshold = config.releaseThreshold();

        if (threshold == 0) {
            // Only release when inventory is full
            return Inventory.getEmptySlots() == 0 && salamanderCount > 0;
        } else {
            return salamanderCount >= threshold;
        }
    }

    /**
     * Check if player is performing a hunting animation.
     */
    private boolean isAnimating() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }
        int animation = player.getAnimation();
        return animation == ANIM_SETTING_TRAP || animation == ANIM_CHECKING_TRAP;
    }

    /**
     * Check if player is moving.
     */
    private boolean isMoving() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }
        return player.getPoseAnimation() != player.getIdlePoseAnimation();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        tickCounter++;

        // Debug: Log animation and movement state every tick
        Player player = client.getLocalPlayer();
        if (player != null && config.debugLogging()) {
            int animId = player.getAnimation();
            int poseAnim = player.getPoseAnimation();
            int idlePose = player.getIdlePoseAnimation();
            boolean moving = poseAnim != idlePose;

            // Only log when something interesting is happening
            if (animId != -1 || moving || actionCooldown > 0) {
                log(String.format("[Tick %d] Animation: %d | PoseAnim: %d | IdlePose: %d | Moving: %s | Cooldown: %d",
                    tickCounter, animId, poseAnim, idlePose, moving, actionCooldown));
            }
        }

        // Decrease action cooldown
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        // Don't act while animating or moving
        if (isAnimating()) {
            log("[Tick " + tickCounter + "] Waiting - player is animating");
            return;
        }
        if (isMoving()) {
            log("[Tick " + tickCounter + "] Waiting - player is moving");
            return;
        }

        // Update trap states
        trapManager.updateTrapStates();

        // Check if we're waiting for a previous action to complete
        if (lastActionLocation != null && lastActionInitialStatus != null) {
            TrapState lastTrap = trapManager.getState(lastActionLocation);
            int ticksWaiting = tickCounter - lastActionTick;

            if (lastTrap.getStatus() != lastActionInitialStatus) {
                // Status changed - action completed, clear the tracking
                log("Action completed at " + lastActionLocation + " (changed from " + lastActionInitialStatus + " to " + lastTrap.getStatus() + ")");

                // For reset actions (SALAMANDER_CAUGHT -> NO_TRAP), wait for auto-set to complete
                // The reset is a multi-step action: check(5207) -> idle gap -> set(5215)
                if (lastActionInitialStatus == TrapState.TrapStatus.SALAMANDER_CAUGHT
                    && lastTrap.getStatus() == TrapState.TrapStatus.NO_TRAP) {
                    // Still waiting for auto-set to complete - keep tracking but update expected status
                    log("Reset in progress at " + lastActionLocation + " - waiting for auto-set to TRAP_SET");
                    lastActionInitialStatus = TrapState.TrapStatus.NO_TRAP;
                    lastActionTick = tickCounter; // Reset timeout for the set phase
                    return;
                }

                // Action fully completed - add small random delay (0-2 ticks) before next action
                int postActionDelay = ThreadLocalRandom.current().nextInt(0, 3);
                if (postActionDelay > 0) {
                    actionCooldown = postActionDelay;
                    log("Post-action delay: " + postActionDelay + " ticks");
                }

                lastActionLocation = null;
                lastActionInitialStatus = null;
            } else if (ticksWaiting >= ACTION_TIMEOUT_TICKS) {
                // Timeout - action probably failed, clear tracking and retry
                log("Action TIMEOUT at " + lastActionLocation + " after " + ticksWaiting + " ticks - clearing tracking");
                lastActionLocation = null;
                lastActionInitialStatus = null;
            } else {
                // Still waiting for state change - don't send new actions
                log("[Tick " + tickCounter + "] Waiting for " + lastActionLocation + " to change from " + lastActionInitialStatus + " (" + ticksWaiting + "/" + ACTION_TIMEOUT_TICKS + " ticks)");
                return;
            }
        }

        // Log status periodically
        if (tickCounter % 10 == 0) {
            logStatus();
        }

        // State machine
        switch (currentState) {
            case RELEASING:
                handleReleasingState();
                break;

            case PICKING_UP_ITEMS:
                handlePickingUpState();
                break;

            case HANDLING_TRAP:
                handleTrapState();
                break;

            case IDLE:
            default:
                handleIdleState();
                break;
        }
    }

    private void handleIdleState() {
        // Priority 1: Release salamanders if needed
        if (shouldReleaseSalamanders()) {
            log("Starting salamander release (count: " + getSalamanderCount() + ")");
            releaser = new HumanLikeDropper(RED_SALAMANDER)
                .setAction(ACTION_RELEASE)
                .setLogging(config.debugLogging());
            currentState = PluginState.RELEASING;
            return;
        }

        // Priority 2: Handle traps based on event priority (timestamp order)
        // This includes SALAMANDER_CAUGHT, TRAP_FALLEN, and NO_TRAP - all in the order they occurred
        Optional<TrapState> nextTrap = trapManager.getNextTrapToHandle();
        if (nextTrap.isPresent()) {
            TrapState trap = nextTrap.get();
            currentTargetLocation = trap.getLocation();
            currentState = PluginState.HANDLING_TRAP;
            log("Targeting trap at " + currentTargetLocation + " (status: " + trap.getStatus() + ")");
            handleTrapAction(trap);
        }
    }

    private void handleReleasingState() {
        if (releaser == null || !releaser.dropNextBatch()) {
            // Done releasing
            log("Finished releasing salamanders");
            releaser = null;
            currentState = PluginState.IDLE;
        }
    }

    private void handlePickingUpState() {
        // Check for more items to pick up at the same location
        if (pickupLocation != null) {
            Optional<ETileItem> groundItem = trapManager.findGroundItemsAtLocation(pickupLocation);
            if (groundItem.isPresent()) {
                pickupGroundItem(groundItem.get());
                return;
            }
        }

        // No more items at this location - now set trap here
        if (pickupLocation != null && hasSupplies()) {
            Optional<TileObject> tree = trapManager.findTreeAtLocation(pickupLocation);
            if (tree.isPresent()) {
                interactWithObject(tree.get(), ACTION_SET_TRAP);
                // Track this action - wait for trap status to change from NO_TRAP/TRAP_FALLEN
                lastActionLocation = pickupLocation;
                lastActionInitialStatus = trapManager.getState(pickupLocation).getStatus();
                lastActionTick = tickCounter;
                log("Finished picking up, setting trap at " + pickupLocation);
            }
        } else {
            log("Finished picking up items");
        }

        pickupLocation = null;
        currentState = PluginState.IDLE;
    }

    private void handleTrapState() {
        if (currentTargetLocation == null) {
            currentState = PluginState.IDLE;
            return;
        }

        TrapState trap = trapManager.getState(currentTargetLocation);

        // Check if trap still needs attention
        if (!trap.needsAttention()) {
            log("Trap at " + currentTargetLocation + " no longer needs attention");
            currentTargetLocation = null;
            currentState = PluginState.IDLE;
            return;
        }

        // Handle the trap based on its status
        handleTrapAction(trap);
    }

    private void handleTrapAction(TrapState trap) {
        TrapLocation location = trap.getLocation();

        switch (trap.getStatus()) {
            case SALAMANDER_CAUGHT:
                // Reset the trap (this checks and auto-resets)
                Optional<TileObject> caughtTrap = trapManager.findTrapObjectAtLocation(location);
                if (caughtTrap.isPresent()) {
                    interactWithObject(caughtTrap.get(), ACTION_RESET);
                    // Track this action - wait for trap status to change from SALAMANDER_CAUGHT
                    lastActionLocation = location;
                    lastActionInitialStatus = TrapState.TrapStatus.SALAMANDER_CAUGHT;
                    lastActionTick = tickCounter;
                    log("Resetting trap at " + location);
                }
                currentTargetLocation = null;
                currentState = PluginState.IDLE;
                break;

            case NO_TRAP:
                // Set a new trap (only if we have supplies)
                if (hasSupplies()) {
                    Optional<TileObject> tree = trapManager.findTreeAtLocation(location);
                    if (tree.isPresent()) {
                        interactWithObject(tree.get(), ACTION_SET_TRAP);
                        // Track this action - wait for trap status to change from NO_TRAP
                        lastActionLocation = location;
                        lastActionInitialStatus = TrapState.TrapStatus.NO_TRAP;
                        lastActionTick = tickCounter;
                        log("Setting trap at " + location);
                    }
                } else {
                    log("No supplies available, skipping trap at " + location);
                }
                currentTargetLocation = null;
                currentState = PluginState.IDLE;
                break;

            case TRAP_FALLEN:
                // Pick up items - transition to picking up state
                Optional<ETileItem> groundItem = trapManager.findGroundItemsAtLocation(location);
                if (groundItem.isPresent()) {
                    pickupLocation = location;
                    currentState = PluginState.PICKING_UP_ITEMS;
                    pickupGroundItem(groundItem.get());
                    log("Picking up fallen trap items at " + location);
                } else {
                    log("No ground items found at " + location + " - trap may have been picked up already");
                }
                currentTargetLocation = null;
                break;

            default:
                // Trap is being set or already set - move on
                currentTargetLocation = null;
                currentState = PluginState.IDLE;
                break;
        }
    }

    /**
     * Pick up a ground item with realistic click coordinates.
     */
    private void pickupGroundItem(ETileItem item) {
        // Use the item's interact method which handles click packets
        item.interact(false);
        log("Picking up item: " + item.getTileItem().getId() + " at " + item.getLocation());
    }

    /**
     * Interact with a tile object using realistic click coordinates.
     */
    private void interactWithObject(TileObject object, String action) {
        Point clickPoint = RealisticClickHelper.getTileObjectClickPoint(object);
        if (clickPoint != null) {
            MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
        } else {
            MousePackets.queueClickPacket();
        }
        ObjectPackets.queueObjectAction(object, false, action);
    }

    private void logStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Status [Tick ").append(tickCounter).append("] ===\n");
        sb.append("State: ").append(currentState).append("\n");
        sb.append("Salamanders: ").append(getSalamanderCount()).append("\n");
        sb.append("Has supplies: ").append(hasSupplies()).append("\n");
        sb.append("Traps:\n");
        for (TrapState state : trapManager.getAllStates()) {
            sb.append("  ").append(state.toString()).append("\n");
        }
        log(sb.toString());

        // Debug log objects near trees every 50 ticks
        debugLogCounter++;
        if (debugLogCounter >= 50) {
            debugLogCounter = 0;
            for (TrapLocation location : TrapLocation.values()) {
                trapManager.debugLogObjectsNearTree(location);
            }
        }
    }
}
