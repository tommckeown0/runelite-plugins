package com.example.MotherlodeMine;

import com.example.EthanApiPlugin.Collections.DepositBox;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.InteractionApi.HumanLikeDelay;
import com.example.InteractionApi.TileObjectInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import java.util.Optional;
import java.util.function.Consumer;

import static com.example.MotherlodeMine.MotherlodeMineConstants.*;

/**
 * Handles state transitions and actions for the Motherlode Mine plugin
 */
public class MotherlodeMineStateHandler {

    private final Client client;
    private final MotherlodeMineConfig config;
    private final MotherlodeMineObjectFinder objectFinder;
    private final Consumer<String> logger;

    // State tracking
    private PluginState currentState = PluginState.MINING;
    private boolean wasMining = false;
    private int idleTickCount = 0;
    private TileObject currentTarget = null;
    private int ticksSinceLastInteraction = 0;
    private boolean hasCrawledThroughTunnel = false;
    private int hopperDepositCount = 0;
    private int depositAttempts = 0;
    private int ticksSinceReturnedToMining = 0;

    // Human-like random delays
    private int currentInteractionCooldown = 0;
    private int randomVeinDepletionDelay = 0;
    private int randomInventoryFullDelay = 0;
    private boolean inventoryFullDetected = false;
    private int inventoryFullTickCount = 0;
    private int randomPostTunnelDelay = 0;
    private boolean postTunnelDelayActive = false;
    private int postTunnelTickCount = 0;
    private int randomGemDropDelay = 0;
    private int gemDropTickCount = 0;
    private boolean gemDetected = false;
    private int randomPostSackDelay = 0;
    private int postSackTickCount = 0;
    private int randomPostDepositDelay = 0;
    private int postDepositTickCount = 0;

    public MotherlodeMineStateHandler(Client client, MotherlodeMineConfig config,
                                      MotherlodeMineObjectFinder objectFinder, Consumer<String> logger) {
        this.client = client;
        this.config = config;
        this.objectFinder = objectFinder;
        this.logger = logger;
    }

    /**
     * Logging helper - wraps the injected logger for consistent syntax
     */
    private void log(String message) {
        logger.accept(message);
    }

    // Getters
    public PluginState getCurrentState() { return currentState; }
    public TileObject getCurrentTarget() { return currentTarget; }
    public boolean getWasMining() { return wasMining; }
    public int getIdleTickCount() { return idleTickCount; }

    // Setters
    public void setWasMining(boolean wasMining) { this.wasMining = wasMining; }
    public void setIdleTickCount(int count) { this.idleTickCount = count; }
    public void incrementIdleTickCount() { this.idleTickCount++; }

    public void resetState() {
        currentState = PluginState.MINING;
        wasMining = false;
        idleTickCount = 0;
        currentTarget = null;
        ticksSinceLastInteraction = 0;
        hasCrawledThroughTunnel = false;
        hopperDepositCount = 0;
        depositAttempts = 0;
        ticksSinceReturnedToMining = 0;

        // Reset human-like delay tracking
        currentInteractionCooldown = 0;
        randomVeinDepletionDelay = 0;
        randomInventoryFullDelay = 0;
        inventoryFullDetected = false;
        inventoryFullTickCount = 0;
        randomPostTunnelDelay = 0;
        postTunnelDelayActive = false;
        postTunnelTickCount = 0;
        randomGemDropDelay = 0;
        gemDropTickCount = 0;
        gemDetected = false;
        randomPostSackDelay = 0;
        postSackTickCount = 0;
        randomPostDepositDelay = 0;
        postDepositTickCount = 0;
    }

    /**
     * Detect and set the appropriate initial state based on current situation
     * Called when plugin starts to intelligently resume from current position
     */
    public void detectAndSetInitialState() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            log("!!! Cannot detect state - player is null");
            currentState = PluginState.MINING;
            return;
        }

        WorldPoint playerLoc = player.getWorldLocation();
        boolean hasPayDirtInv = hasPayDirt();
        boolean hasOresInv = hasOres();
        int sackSpace = getSackSpaceRemaining();
        boolean sackHasItems = (sackSpace != -1 && sackSpace < 108);

        // Calculate distances to key locations
        WorldPoint hopperLoc = new WorldPoint(config.hopperX(), config.hopperY(), playerLoc.getPlane());
        WorldPoint depositBoxLoc = new WorldPoint(config.depositBoxX(), config.depositBoxY(), playerLoc.getPlane());
        WorldPoint sackLoc = new WorldPoint(config.sackX(), config.sackY(), playerLoc.getPlane());

        int distToHopper = playerLoc.distanceTo(hopperLoc);
        int distToDepositBox = playerLoc.distanceTo(depositBoxLoc);
        int distToSack = playerLoc.distanceTo(sackLoc);
        boolean inMiningRegion = objectFinder.isInMiningRegion(playerLoc);

        // Check which side of the mine we're on (mining area is X ~3764-3769, hopper/sack area is X ~3749)
        int miningCenterX = (config.regionMinX() + config.regionMaxX()) / 2;
        boolean isOnHopperSide = playerLoc.getX() < miningCenterX - 5; // West of mining area
        boolean isOnMiningSide = inMiningRegion || playerLoc.getX() > miningCenterX - 2; // East side or in region

        log("=== STATE DETECTION ===");
        log(String.format("Location: %s", playerLoc));
        log(String.format("Has pay-dirt: %s | Has ores: %s | Sack has items: %s (space: %d)",
            hasPayDirtInv, hasOresInv, sackHasItems, sackSpace));
        log(String.format("Distance to hopper: %d | sack: %d | deposit box: %d",
            distToHopper, distToSack, distToDepositBox));
        log(String.format("In mining region: %s | On hopper side: %s | On mining side: %s",
            inMiningRegion, isOnHopperSide, isOnMiningSide));

        // State detection logic
        if (hasOresInv) {
            // Has ores - needs to deposit them
            // Only set to DEPOSITING state if deposit box interface is actually open
            if (distToDepositBox <= 5 && isDepositBoxOpen()) {
                log(">>> Detected: At deposit box with ores and interface open → DEPOSITING_ORE_IN_DEPOSIT_BOX");
                currentState = PluginState.DEPOSITING_ORE_IN_DEPOSIT_BOX;
                ticksSinceLastInteraction = 0;
            } else {
                log(">>> Detected: Has ores, need to open deposit box → TRAVELING_TO_DEPOSIT_BOX");
                currentState = PluginState.TRAVELING_TO_DEPOSIT_BOX;
                ticksSinceLastInteraction = 0;
            }
        } else if (hasPayDirtInv) {
            // Has pay-dirt - needs to deposit in hopper
            if (distToHopper <= 10) {
                log(">>> Detected: Near hopper with pay-dirt → DEPOSITING_IN_HOPPER");
                currentState = PluginState.DEPOSITING_IN_HOPPER;
                ticksSinceLastInteraction = 0;
            } else {
                log(">>> Detected: Has pay-dirt but not at hopper → TRAVELING_TO_HOPPER");
                currentState = PluginState.TRAVELING_TO_HOPPER;
                ticksSinceLastInteraction = 0;
            }
        } else if (sackHasItems && Inventory.getEmptySlots() > 1) {
            // Inventory is empty/has space and sack has items - should empty sack
            if (distToSack <= 10) {
                log(String.format(">>> Detected: Near sack with space in inventory, sack has items (space: %d) → EMPTYING_SACK", sackSpace));
                currentState = PluginState.EMPTYING_SACK;
                ticksSinceLastInteraction = 0;
            } else {
                log(">>> Detected: Sack has items but player is far away → MINING (will check sack later)");
                currentState = PluginState.MINING;
            }
        } else if (isOnHopperSide && !sackHasItems) {
            // Player is on hopper/sack side (west) but has nothing to do there - return to mining
            log(">>> Detected: On hopper side with no items to process → RETURNING to mining area");
            currentState = PluginState.RETURNING;
            ticksSinceLastInteraction = 0;
            hasCrawledThroughTunnel = false;
        } else if (inMiningRegion || isOnMiningSide) {
            // In or near mining region - ready to mine
            log(">>> Detected: In/near mining region with no items to process → MINING");
            currentState = PluginState.MINING;
            ticksSinceReturnedToMining = 10; // Allow immediate vein clicking
        } else {
            // Somewhere else entirely - safest to return to mining area
            log(">>> Detected: Unknown location, attempting to return → RETURNING to mining area");
            currentState = PluginState.RETURNING;
            ticksSinceLastInteraction = 0;
            hasCrawledThroughTunnel = false;
        }

        log(String.format(">>> Initial state set to: %s", currentState));
        log("======================");
    }

    /**
     * Check if inventory is full and handle state transition with human-like delay
     * Returns true if delay is complete and state transition should occur
     */
    public boolean handleInventoryFull() {
        if (config.autoDeposit()) {
            // First tick detecting full inventory - generate weighted random delay
            if (!inventoryFullDetected) {
                randomInventoryFullDelay = HumanLikeDelay.generate(HumanLikeDelay.INVENTORY_FULL);
                log(String.format("!!! INVENTORY FULL - Generated reaction delay: %d ticks (weighted distribution)",
                        randomInventoryFullDelay));
                inventoryFullDetected = true;
                inventoryFullTickCount = 0;
            }

            inventoryFullTickCount++;
            log(String.format(">>> Inventory full reaction delay: %d/%d", inventoryFullTickCount, randomInventoryFullDelay));

            // Wait for random delay before switching to traveling
            if (inventoryFullTickCount >= randomInventoryFullDelay) {
                log(">>> Reaction delay complete - Starting deposit sequence");
                currentState = PluginState.TRAVELING_TO_HOPPER;
                wasMining = false;
                idleTickCount = 0;
                depositAttempts = 0;
                ticksSinceLastInteraction = 0;
                hasCrawledThroughTunnel = false;
                inventoryFullDetected = false;
                inventoryFullTickCount = 0;
                randomInventoryFullDelay = 0;
                return true;
            }
            return false;
        } else if (config.stopWhenFull()) {
            if (wasMining) {
                log("!!! INVENTORY FULL - Stopping auto-mining");
                log(">>> Empty your inventory or enable 'Auto Deposit' to continue");
                wasMining = false;
                idleTickCount = 0;
                currentTarget = null;
            }
        }
        return false;
    }

    /**
     * Reset inventory full detection if inventory is no longer full
     */
    public void resetInventoryFullDetection() {
        if (inventoryFullDetected) {
            log(">>> Inventory no longer full - resetting detection");
            inventoryFullDetected = false;
            inventoryFullTickCount = 0;
            randomInventoryFullDelay = 0;
        }
    }

    /**
     * Click on a pay-dirt vein with variable cooldown
     */
    public void clickVein(Optional<TileObject> vein) {
        if (!vein.isPresent()) {
            return;
        }

        currentTarget = vein.get();

        // Generate new weighted random cooldown for next interaction
        currentInteractionCooldown = HumanLikeDelay.generate(HumanLikeDelay.INTERACTION_COOLDOWN);

        log(String.format("Clicking pay-dirt vein (ID: %d) at %s | Next cooldown: %d ticks",
                currentTarget.getId(), currentTarget.getWorldLocation(), currentInteractionCooldown));

        TileObjectInteraction.interact(currentTarget, "Mine");
        ticksSinceLastInteraction = 0;
    }

    /**
     * Handle the MINING state
     */
    public void handleMiningState(boolean currentlyMining, int animation,
                                   Optional<TileObject> nearestVein) {
        // Safety check: Ensure we're actually in or near the mining region
        Player player = client.getLocalPlayer();
        if (player != null) {
            WorldPoint playerLoc = player.getWorldLocation();
            int miningCenterX = (config.regionMinX() + config.regionMaxX()) / 2;
            boolean isOnHopperSide = playerLoc.getX() < miningCenterX - 5;

            if (isOnHopperSide) {
                log("!!! Cannot mine - player is on hopper side of tunnel. Switching to RETURNING state.");
                currentState = PluginState.RETURNING;
                ticksSinceLastInteraction = 0;
                hasCrawledThroughTunnel = false;
                return;
            }
        }

        // Handle post-tunnel delay if active
        if (postTunnelDelayActive) {
            postTunnelTickCount++;
            log(String.format(">>> Post-tunnel delay: %d/%d ticks", postTunnelTickCount, randomPostTunnelDelay));

            if (postTunnelTickCount >= randomPostTunnelDelay) {
                log(">>> Post-tunnel delay complete - ready to mine");
                postTunnelDelayActive = false;
                postTunnelTickCount = 0;
                randomPostTunnelDelay = 0;
                ticksSinceReturnedToMining = 100; // Set high so we can click immediately next check
            }
            return; // Wait for delay to complete
        }

        // Increment delay counter (used when returning from hopper)
        if (ticksSinceReturnedToMining < 100) {
            ticksSinceReturnedToMining++;
        }

        // If we're not mining and haven't started yet (just returned from hopper or first startup)
        if (!currentlyMining && !wasMining && ticksSinceReturnedToMining >= 5) {
            // Only try to click if cooldown has passed
            if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                log(">>> Not currently mining - finding vein to start");

                if (nearestVein.isPresent()) {
                    log(String.format(">>> CLICKING VEIN TO START MINING: ID %d", nearestVein.get().getId()));
                    clickVein(nearestVein);
                } else {
                    log("!!! NO VEIN FOUND - Check region settings or object IDs");
                }
            }
            return;
        }

        // If we were mining but now we're not (vein depleted)
        if (wasMining && !currentlyMining) {
            // First tick we notice we stopped - generate weighted random delay for vein switch
            if (idleTickCount == 0) {
                randomVeinDepletionDelay = HumanLikeDelay.generate(HumanLikeDelay.RESOURCE_DEPLETION);
                log(String.format(">>> STOPPED MINING - Generated random delay: %d ticks (weighted distribution)",
                        randomVeinDepletionDelay));
            }

            idleTickCount++;
            log(String.format(">>> Idle tick %d/%d (animation: %d)", idleTickCount, randomVeinDepletionDelay, animation));

            // Wait for the random delay before clicking new vein
            if (idleTickCount >= randomVeinDepletionDelay) {
                // If we just returned from hopper, wait a bit longer for character to be ready
                if (ticksSinceReturnedToMining < 5) {
                    log(String.format(">>> Waiting for character to be ready (delay: %d/5 ticks)", ticksSinceReturnedToMining));
                    return;
                }

                log("!!! Random delay threshold reached - Finding next vein");

                // Only interact if cooldown has passed
                if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                    // Find and click nearest vein
                    if (nearestVein.isPresent()) {
                        log(String.format(">>> CLICKING NEW VEIN: ID %d at distance %d",
                            nearestVein.get().getId(),
                            client.getLocalPlayer().getWorldLocation().distanceTo(nearestVein.get().getWorldLocation())));
                        clickVein(nearestVein);
                        wasMining = false; // Reset so we wait for mining to start again
                    } else {
                        log("!!! NO VEIN FOUND - Check region settings or object IDs");
                        wasMining = false;
                    }
                }

                idleTickCount = 0;
                randomVeinDepletionDelay = 0; // Reset for next vein depletion
            }
        } else if (currentlyMining) {
            // Reset idle counter if we're mining
            if (idleTickCount > 0) {
                log("Mining resumed, resetting idle counter");
            }
            idleTickCount = 0;
            randomVeinDepletionDelay = 0;
        }
    }

    /**
     * Handle the TRAVELING_TO_HOPPER state
     */
    public void handleTravelingToHopperState() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        ticksSinceLastInteraction++;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint hopperTarget = new WorldPoint(config.hopperX(), config.hopperY(), playerLoc.getPlane());
        int distanceToHopper = playerLoc.distanceTo(hopperTarget);

        // Check if we've traveled west (through tunnel) - mining area is around X=3766, hopper is around X=3749
        int miningCenterX = (config.regionMinX() + config.regionMaxX()) / 2;
        boolean isWestOfMiningArea = playerLoc.getX() < miningCenterX - 5;

        if (isWestOfMiningArea && !hasCrawledThroughTunnel) {
            log(">>> Successfully crawled through tunnel to hopper side");
            hasCrawledThroughTunnel = true;
        }

        log(String.format("[TRAVELING] Distance to hopper: %d tiles | West of mining: %s | Crawled: %s",
            distanceToHopper, isWestOfMiningArea, hasCrawledThroughTunnel));

        // Check if we're close to hopper area (increased threshold)
        if (distanceToHopper <= 15 && hasCrawledThroughTunnel) {
            log(">>> Near hopper area - switching to deposit mode");
            currentState = PluginState.DEPOSITING_IN_HOPPER;
            ticksSinceLastInteraction = 0;
            depositAttempts = 0;
            return;
        }

        // Safety check - max attempts
        if (depositAttempts > MAX_DEPOSIT_ATTEMPTS) {
            log("!!! MAX DEPOSIT ATTEMPTS REACHED - Returning to mining");
            currentState = PluginState.MINING;
            depositAttempts = 0;
            hasCrawledThroughTunnel = false;
            return;
        }

        // Only interact if cooldown has passed
        if (ticksSinceLastInteraction < INTERACTION_COOLDOWN) {
            return;
        }

        // Don't click tunnels if we've already crawled through (prevents spam clicking on hopper side)
        if (hasCrawledThroughTunnel) {
            log("Already through tunnel, waiting to get closer to hopper...");
            return;
        }

        // Look for dark tunnel to crawl through (only if we haven't yet)
        Optional<TileObject> tunnel = objectFinder.findNearestTunnel();
        if (tunnel.isPresent()) {
            int tunnelDist = playerLoc.distanceTo(tunnel.get().getWorldLocation());
            log(String.format(">>> Crawling through dark tunnel (distance: %d)", tunnelDist));
            currentTarget = tunnel.get();
            TileObjectInteraction.interact(tunnel.get(), "Enter");
            ticksSinceLastInteraction = 0;
            depositAttempts++;
        } else {
            log("!!! Dark tunnel not found - check tunnel IDs");
            depositAttempts++;
        }
    }

    /**
     * Handle the DEPOSITING state
     */
    public void handleDepositingInHopperState() {
        ticksSinceLastInteraction++;

        // FIRST: Check for broken struts before depositing (prevents ore backup)
        if (config.autoRepairStrut() && ticksSinceLastInteraction >= INTERACTION_COOLDOWN) {
            Optional<TileObject> brokenStrut = objectFinder.findNearestBrokenStrut();
            if (brokenStrut.isPresent()) {
                log("!!! BROKEN STRUT DETECTED - Repairing before depositing");
                currentTarget = brokenStrut.get();
                TileObjectInteraction.interact(brokenStrut.get(), "Hammer");
                ticksSinceLastInteraction = 0;
                depositAttempts++;
                return; // Don't deposit until strut is fixed
            }
        }

        // Check if we still have pay-dirt
        if (!hasPayDirt()) {
            hopperDepositCount++;
            log(String.format(">>> Pay-dirt deposited successfully (Count: %d/%d)",
                hopperDepositCount, DEPOSITS_BEFORE_SACK));

            // Wait a moment for ore to flow into sack, then check capacity
            // This ensures we make the decision with accurate sack data
            try {
                Thread.sleep(300); // Wait 300ms for ore to flow
            } catch (InterruptedException e) {
                // Ignore
            }

            // Check if sack is full by reading UI widget
            if (isSackFull()) {
                log(">>> SACK IS FULL - emptying sack");
                currentState = PluginState.EMPTYING_SACK;
                ticksSinceLastInteraction = 0;
                hopperDepositCount = 0; // Reset counter
                return;
            }

            // Not full yet, return to mining for more pay-dirt
            if (config.returnToMining()) {
                log(">>> Returning to mining area for more pay-dirt");
                currentState = PluginState.RETURNING;
                ticksSinceLastInteraction = 0;
                hasCrawledThroughTunnel = false; // Reset for return journey
            } else {
                log(">>> Deposit complete - staying at hopper");
                currentState = PluginState.MINING;
            }
            return;
        }

        // Safety check
        if (depositAttempts > MAX_DEPOSIT_ATTEMPTS) {
            log("!!! Cannot find hopper - returning to mining");
            currentState = PluginState.MINING;
            depositAttempts = 0;
            return;
        }

        // Only interact if cooldown has passed
        if (ticksSinceLastInteraction < INTERACTION_COOLDOWN) {
            return;
        }

        // Look for hopper
        Optional<TileObject> hopper = objectFinder.findNearestHopper();
        if (hopper.isPresent()) {
            log(">>> Depositing pay-dirt at hopper");
            currentTarget = hopper.get();
            TileObjectInteraction.interact(hopper.get(), "Deposit");
            ticksSinceLastInteraction = 0;
            depositAttempts++;
        } else {
            log("!!! Hopper not found - check hopper IDs");
            depositAttempts++;
        }
    }

    /**
     * Handle the EMPTYING_SACK state
     */
    public void handleEmptyingSackState() {
        ticksSinceLastInteraction++;

        // Check sack space first
        int sackSpace = getSackSpaceRemaining();
        boolean sackIsEmpty = (sackSpace == 108);
        boolean inventoryHasOres = hasOres();

        // Transition if: (1) inventory is nearly full with ores, OR (2) sack is empty and we have some ores
        boolean inventoryFull = Inventory.getEmptySlots() <= 1;
        boolean shouldTransition = inventoryHasOres && (inventoryFull || sackIsEmpty);

        if (shouldTransition) {
            // Generate weighted random post-sack delay
            if (postSackTickCount == 0) {
                randomPostSackDelay = HumanLikeDelay.generate(HumanLikeDelay.NAVIGATION_DELAY);

                if (sackIsEmpty) {
                    log(String.format(">>> Sack fully emptied (took %d items) - Generated post-sack delay: %d ticks (weighted distribution)",
                            28 - Inventory.getEmptySlots(), randomPostSackDelay));
                } else if (sackSpace != -1 && sackSpace < 108) {
                    log(String.format(">>> Inventory full with ores. Sack still has items (space: %d/108) - Generated post-sack delay: %d ticks (weighted distribution)",
                            sackSpace, randomPostSackDelay));
                } else {
                    log(String.format(">>> Inventory full with ores - Generated post-sack delay: %d ticks (weighted distribution)", randomPostSackDelay));
                }
            }

            postSackTickCount++;
            log(String.format(">>> Post-sack delay: %d/%d ticks", postSackTickCount, randomPostSackDelay));

            // Wait for random delay before transitioning
            if (postSackTickCount >= randomPostSackDelay) {
                log(">>> Post-sack delay complete - going to deposit");
                currentState = PluginState.TRAVELING_TO_DEPOSIT_BOX;
                ticksSinceLastInteraction = 0;
                depositAttempts = 0;
                postSackTickCount = 0;
                randomPostSackDelay = 0;
            }
            return;
        }

        // Reset post-sack delay if not transitioning
        if (postSackTickCount > 0) {
            postSackTickCount = 0;
            randomPostSackDelay = 0;
        }

        // Safety check
        if (depositAttempts > MAX_DEPOSIT_ATTEMPTS) {
            log("!!! Cannot find sack - returning to mining");
            currentState = PluginState.MINING;
            depositAttempts = 0;
            return;
        }

        // Only interact if cooldown has passed
        if (ticksSinceLastInteraction < INTERACTION_COOLDOWN) {
            return;
        }

        // Look for sack
        Optional<TileObject> sack = objectFinder.findNearestSack();
        if (sack.isPresent()) {
            log(">>> Searching sack for ores");
            currentTarget = sack.get();
            TileObjectInteraction.interact(sack.get(), "Search");
            ticksSinceLastInteraction = 0;
            depositAttempts++;
        } else {
            log("!!! Sack not found - check sack IDs and coordinates");
            depositAttempts++;
        }
    }

    /**
     * Handle the TRAVELING_TO_DEPOSIT state
     */
    public void handleTravelingToDepositBoxState() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        ticksSinceLastInteraction++;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint depositBoxTarget = new WorldPoint(config.depositBoxX(), config.depositBoxY(), playerLoc.getPlane());
        int distanceToBox = playerLoc.distanceTo(depositBoxTarget);

        log(String.format("[TRAVELING_TO_DEPOSIT] Distance to deposit box: %d tiles", distanceToBox));

        // Check if deposit box interface is open (player clicked and it opened)
        if (isDepositBoxOpen()) {
            log(">>> Deposit box interface opened - switching to deposit ore mode");
            currentState = PluginState.DEPOSITING_ORE_IN_DEPOSIT_BOX;
            ticksSinceLastInteraction = 0;
            depositAttempts = 0;
            return;
        }

        // If we're somewhat close but not right there, try to find and click the deposit box
        if (distanceToBox <= 10 && ticksSinceLastInteraction >= INTERACTION_COOLDOWN) {
            Optional<TileObject> depositBox = objectFinder.findNearestDepositBox();
            if (depositBox.isPresent()) {
                log(String.format(">>> Clicking deposit box to get closer (distance: %d)", distanceToBox));
                currentTarget = depositBox.get();
                TileObjectInteraction.interact(depositBox.get(), "Deposit");
                ticksSinceLastInteraction = 0;
                return;
            }
        }

        // Safety check - max attempts
        if (depositAttempts > MAX_DEPOSIT_ATTEMPTS) {
            log("!!! MAX ATTEMPTS REACHED while traveling to deposit - returning to mining");
            currentState = PluginState.MINING;
            depositAttempts = 0;
            return;
        }

        // For now, just wait for player to walk there manually or implement pathfinding
        // The player should be close already since hopper/sack/deposit are all nearby
        log("Waiting for player to get closer to deposit box...");
    }

    /**
     * Handle the DEPOSITING_ORE state
     * Optimized to deposit all ore types in a single tick by queuing all clicks at once
     */
    public void handleDepositingOreInDepositBoxState() {
        ticksSinceLastInteraction++;

        // Wait a few ticks for deposit box interface to fully load
        if (ticksSinceLastInteraction < 2) {
            log(String.format("Waiting for deposit box interface to load (tick %d/2)", ticksSinceLastInteraction));
            return;
        }

        // Check if we still have ores to deposit
        if (!hasOres()) {
            // Generate weighted random post-deposit delay
            if (postDepositTickCount == 0) {
                randomPostDepositDelay = HumanLikeDelay.generate(HumanLikeDelay.BANK_DEPOSIT_COMPLETE);
                log(String.format(">>> All ores deposited - Generated post-deposit delay: %d ticks (weighted distribution)",
                        randomPostDepositDelay));
            }

            postDepositTickCount++;
            log(String.format(">>> Post-deposit delay: %d/%d ticks", postDepositTickCount, randomPostDepositDelay));

            // Wait for random delay before transitioning
            if (postDepositTickCount >= randomPostDepositDelay) {
                log(">>> Post-deposit delay complete");
                postDepositTickCount = 0;
                randomPostDepositDelay = 0;

                // Check if sack still has items - if so, go back to empty more
                int sackSpace = getSackSpaceRemaining();
                if (sackSpace != -1 && sackSpace < 108) {
                    log(String.format(">>> Sack still has items (space: %d/108) - going back to empty more", sackSpace));
                    currentState = PluginState.EMPTYING_SACK;
                    ticksSinceLastInteraction = 0;
                    depositAttempts = 0;
                    return;
                }

                // Sack is fully empty, return to mining
                log(">>> Sack fully empty - returning to mining");
                if (config.returnToMining()) {
                    currentState = PluginState.RETURNING;
                    ticksSinceLastInteraction = 0;
                    hasCrawledThroughTunnel = false; // Reset for return journey
                } else {
                    currentState = PluginState.MINING;
                }
            }
            return;
        }

        // Reset post-deposit delay if we still have ores
        if (postDepositTickCount > 0) {
            postDepositTickCount = 0;
            randomPostDepositDelay = 0;
        }

        // Safety check
        if (depositAttempts > MAX_DEPOSIT_ATTEMPTS) {
            log("!!! Cannot complete ore depositing - returning to mining");
            currentState = PluginState.MINING;
            depositAttempts = 0;
            return;
        }

        // Only interact if cooldown has passed
        if (ticksSinceLastInteraction < INTERACTION_COOLDOWN) {
            return;
        }

        // Ore types to deposit (order doesn't matter since all clicks are queued in same tick)
        int[][] oreTypes = {
            {GOLD_ORE_ID, 0},
            {MITHRIL_ORE_ID, 1},
            {ADAMANTITE_ORE_ID, 2},
            {COAL_ID, 3},
            {GOLD_NUGGET_ID, 4}
        };
        String[] oreNames = {"gold ore", "mithril ore", "adamantite ore", "coal", "gold nugget"};

        boolean clickedAny = false;

        // Queue all deposit clicks in a single tick - they will all execute on the next tick
        for (int[] oreType : oreTypes) {
            int oreId = oreType[0];
            int nameIndex = oreType[1];

            Widget widget = DepositBox.search().withId(oreId).first().orElse(null);
            if (widget != null) {
                log(">>> Depositing: " + oreNames[nameIndex]);
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(widget, "Deposit-All");
                clickedAny = true;
            }
        }

        if (clickedAny) {
            ticksSinceLastInteraction = 0;
            depositAttempts++;
            return;
        }

        // If we get here, hasOres() returned true but we couldn't find any widgets
        // This might happen if we need to open the deposit box interface first
        log(">>> No ore widgets found - trying to open deposit box");
        Optional<TileObject> depositBox = objectFinder.findNearestDepositBox();
        if (depositBox.isPresent()) {
            log(String.format(">>> Opening deposit box at distance %d",
                client.getLocalPlayer().getWorldLocation().distanceTo(depositBox.get().getWorldLocation())));
            currentTarget = depositBox.get();
            TileObjectInteraction.interact(depositBox.get(), "Deposit");
            ticksSinceLastInteraction = 0;
            depositAttempts++;
        } else {
            log("!!! Deposit box not found - check deposit box IDs and coordinates");
            depositAttempts++;
        }
    }

    /**
     * Handle the RETURNING state
     */
    public void handleReturningToPayDirtState() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        ticksSinceLastInteraction++;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint miningTarget = new WorldPoint(
            (config.regionMinX() + config.regionMaxX()) / 2,
            (config.regionMinY() + config.regionMaxY()) / 2,
            playerLoc.getPlane()
        );
        int distanceToMining = playerLoc.distanceTo(miningTarget);

        // Check if we've traveled east (back through tunnel)
        int miningCenterX = (config.regionMinX() + config.regionMaxX()) / 2;
        boolean isEastOfHopper = playerLoc.getX() > miningCenterX - 5;

        if (isEastOfHopper && !hasCrawledThroughTunnel) {
            log(">>> Successfully crawled back through tunnel to mining side");
            hasCrawledThroughTunnel = true;
        }

        log(String.format("[RETURNING] Distance to mining: %d tiles | East of hopper: %s | Crawled: %s",
            distanceToMining, isEastOfHopper, hasCrawledThroughTunnel));

        // Check if we're back in mining region
        if (objectFinder.isInMiningRegion(playerLoc) && hasCrawledThroughTunnel) {
            // Generate weighted random post-tunnel delay
            randomPostTunnelDelay = HumanLikeDelay.generate(HumanLikeDelay.NAVIGATION_DELAY);

            log(String.format(">>> Back in mining region - Generated post-tunnel delay: %d ticks (weighted distribution)",
                    randomPostTunnelDelay));

            currentState = PluginState.MINING;
            hasCrawledThroughTunnel = false;
            ticksSinceLastInteraction = 0;
            ticksSinceReturnedToMining = 0; // Reset will be used to count up to random delay
            postTunnelDelayActive = true;
            postTunnelTickCount = 0;

            // Don't click vein immediately - let handleMiningState wait for random delay
            return;
        }

        // Only interact if cooldown has passed
        if (ticksSinceLastInteraction < INTERACTION_COOLDOWN) {
            return;
        }

        // Don't click tunnels if we've already crawled through (prevents spam clicking on mining side)
        if (hasCrawledThroughTunnel) {
            log("Already through tunnel, waiting to get closer to mining area...");
            return;
        }

        // Look for dark tunnel to crawl back through (only if we haven't yet)
        Optional<TileObject> tunnel = objectFinder.findNearestTunnel();
        if (tunnel.isPresent()) {
            int tunnelDist = playerLoc.distanceTo(tunnel.get().getWorldLocation());
            log(String.format(">>> Crawling back through dark tunnel (distance: %d)", tunnelDist));
            currentTarget = tunnel.get();
            TileObjectInteraction.interact(tunnel.get(), "Enter");
            ticksSinceLastInteraction = 0;
        } else {
            log("!!! Dark tunnel not found - check tunnel IDs");
        }
    }

    // Helper methods
    private boolean hasPayDirt() {
        return Inventory.search().withId(PAY_DIRT_ID).first().isPresent();
    }

    /**
     * Check if deposit box interface is currently open
     * Deposit box widget is at 192:2 (parent group ID 192)
     */
    private boolean isDepositBoxOpen() {
        try {
            net.runelite.api.widgets.Widget depositBoxWidget = client.getWidget(192, 2);
            return depositBoxWidget != null && !depositBoxWidget.isHidden();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasOres() {
        // Check inventory first (always safe)
        boolean hasOresInInventory = Inventory.search().withId(GOLD_ORE_ID).first().isPresent() ||
                                      Inventory.search().withId(MITHRIL_ORE_ID).first().isPresent() ||
                                      Inventory.search().withId(ADAMANTITE_ORE_ID).first().isPresent() ||
                                      Inventory.search().withId(COAL_ID).first().isPresent() ||
                                      Inventory.search().withId(GOLD_NUGGET_ID).first().isPresent();

        // Try DepositBox only if interface is open (to avoid NullPointerException)
        boolean hasOresInDepositBox = false;
        try {
            hasOresInDepositBox = DepositBox.search().withId(GOLD_ORE_ID).first().isPresent() ||
                                  DepositBox.search().withId(MITHRIL_ORE_ID).first().isPresent() ||
                                  DepositBox.search().withId(ADAMANTITE_ORE_ID).first().isPresent() ||
                                  DepositBox.search().withId(COAL_ID).first().isPresent() ||
                                  DepositBox.search().withId(GOLD_NUGGET_ID).first().isPresent();
        } catch (NullPointerException e) {
            // DepositBox interface not open, ignore
        }

        return hasOresInDepositBox || hasOresInInventory;
    }

    /**
     * Drop any uncut gems in inventory to save space for pay-dirt with human-like delay
     */
    public void dropGemsIfEnabled() {
        if (!config.dropGems()) {
            return;
        }

        // Check if any gem exists
        boolean hasAnyGem = Inventory.search().withId(UNCUT_DIAMOND_ID).first().isPresent() ||
                           Inventory.search().withId(UNCUT_RUBY_ID).first().isPresent() ||
                           Inventory.search().withId(UNCUT_EMERALD_ID).first().isPresent() ||
                           Inventory.search().withId(UNCUT_SAPPHIRE_ID).first().isPresent();

        if (!hasAnyGem && gemDetected) {
            // No gems left, reset detection
            gemDetected = false;
            gemDropTickCount = 0;
            randomGemDropDelay = 0;
            return;
        }

        if (hasAnyGem && !gemDetected) {
            // First tick detecting gem - generate weighted random delay
            randomGemDropDelay = HumanLikeDelay.generate(HumanLikeDelay.ITEM_DROP);
            log(String.format("!!! GEM DETECTED - Generated drop delay: %d ticks (weighted distribution)",
                    randomGemDropDelay));
            gemDetected = true;
            gemDropTickCount = 0;
        }

        if (!gemDetected) {
            return; // No gems to drop
        }

        gemDropTickCount++;

        // Wait for random delay before dropping
        if (gemDropTickCount < randomGemDropDelay) {
            log(String.format(">>> Gem drop delay: %d/%d ticks", gemDropTickCount, randomGemDropDelay));
            return;
        }

        // Delay complete, drop gems
        Widget uncutDiamond = Inventory.search().withId(UNCUT_DIAMOND_ID).first().orElse(null);
        if (uncutDiamond != null) {
            log(">>> Dropping uncut diamond");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(uncutDiamond, "Drop");
            gemDetected = false; // Reset to generate new delay for next gem
            gemDropTickCount = 0;
            return; // Drop one per tick
        }

        Widget uncutRuby = Inventory.search().withId(UNCUT_RUBY_ID).first().orElse(null);
        if (uncutRuby != null) {
            log(">>> Dropping uncut ruby");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(uncutRuby, "Drop");
            gemDetected = false;
            gemDropTickCount = 0;
            return;
        }

        Widget uncutEmerald = Inventory.search().withId(UNCUT_EMERALD_ID).first().orElse(null);
        if (uncutEmerald != null) {
            log(">>> Dropping uncut emerald");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(uncutEmerald, "Drop");
            gemDetected = false;
            gemDropTickCount = 0;
            return;
        }

        Widget uncutSapphire = Inventory.search().withId(UNCUT_SAPPHIRE_ID).first().orElse(null);
        if (uncutSapphire != null) {
            log(">>> Dropping uncut sapphire");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(uncutSapphire, "Drop");
            gemDetected = false;
            gemDropTickCount = 0;
            return;
        }
    }

    /**
     * Get the remaining space in the sack from the UI widget
     * Widget found at: 382:3 static child[3] - contains "Space: 54"
     * Returns -1 if widget not found
     */
    private int getSackSpaceRemaining() {
        try {
            // Optimized: directly check the known widget location
            net.runelite.api.widgets.Widget widget = client.getWidget(382, 3);
            if (widget != null && !widget.isHidden()) {
                net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
                if (staticChildren != null && staticChildren.length > 3) {
                    net.runelite.api.widgets.Widget spaceWidget = staticChildren[3];
                    if (spaceWidget != null && spaceWidget.getText() != null) {
                        String text = spaceWidget.getText();
                        if (text.contains("Space:") || text.toLowerCase().contains("space")) {
                            // Extract number from "Space: X" format
                            String[] parts = text.split(":");
                            if (parts.length > 1) {
                                String numberStr = parts[1].trim().replaceAll("[^0-9]", "");
                                if (!numberStr.isEmpty()) {
                                    int space = Integer.parseInt(numberStr);
                                    return space;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("Error reading sack capacity widget: " + e.getMessage());
        }
        return -1; // Widget not found
    }

    /**
     * Check if sack needs to be emptied
     * Golden nuggets stack, so we can't rely on exact math (27 pay-dirt might only add 24-27 items to sack)
     * Strategy: After 3 deposits, check if space <= 54 (half capacity) to account for golden nugget variance
     */
    private boolean isSackFull() {
        int spaceRemaining = getSackSpaceRemaining();
        if (spaceRemaining == -1) {
            // Fallback to counting inventories if widget not found
            log("Widget not found, using inventory count fallback");
            return hopperDepositCount >= DEPOSITS_BEFORE_SACK;
        }

        // After 4 deposits, check if we're at half capacity or less
        // This accounts for golden nuggets stacking and causing variance
        if (hopperDepositCount == 4 || spaceRemaining < 54) {
            log(String.format("Sack space: %d after %d deposits (threshold: < 54 after 4 deposits)",
                spaceRemaining, hopperDepositCount));
            return true;
        }

        // Also check absolute threshold for safety (in case counting is off)
        if (spaceRemaining <= 27) {
            log(String.format("Sack space: %d (absolute threshold: <= 27)", spaceRemaining));
            return true;
        }

        return false;
    }

    /**
     * Periodically check if sack is full and switch to emptying state if needed
     * Handles golden nugget stacking by checking both deposit count and sack space
     * Returns true if we switched to EMPTYING_SACK state
     */
    public boolean checkAndHandleFullSack() {
        int spaceRemaining = getSackSpaceRemaining();

        // Only check if widget is available
        if (spaceRemaining != -1) {
            // After 3 deposits, check if space <= 54 (accounts for golden nugget variance)
            if (hopperDepositCount >= 3 && spaceRemaining <= 54) {
                log(String.format("!!! SACK NEEDS EMPTYING after %d deposits (space: %d <= 54) - Switching to EMPTYING_SACK",
                    hopperDepositCount, spaceRemaining));
                currentState = PluginState.EMPTYING_SACK;
                ticksSinceLastInteraction = 0;
                depositAttempts = 0;
                hopperDepositCount = 0;
                return true;
            }

            // Also check absolute threshold (safety net)
            if (spaceRemaining <= 27) {
                log(String.format("!!! SACK NEEDS EMPTYING (space: %d <= 27) - Switching to EMPTYING_SACK", spaceRemaining));
                currentState = PluginState.EMPTYING_SACK;
                ticksSinceLastInteraction = 0;
                depositAttempts = 0;
                hopperDepositCount = 0;
                return true;
            }
        }
        return false;
    }
}
