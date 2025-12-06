package com.example.MotherlodeMine;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.MotherlodeMine.MotherlodeMineConstants.*;

@PluginDescriptor(
        name = "A Motherlode Mine Auto",
        description = "Automatically mines pay-dirt deposits in Motherlode Mine with debugging overlays",
        tags = {"motherlode", "mine", "mining", "skilling", "paydirt"},
        hidden = false,
        enabledByDefault = false
)
public class MotherlodeMinePlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private MotherlodeMineConfig config;

    @Inject
    private OverlayManager overlayManager;

    private MotherlodeMineOverlay overlay;
    private MotherlodeMineObjectFinder objectFinder;
    private MotherlodeMineStateHandler stateHandler;

    // State tracking
    private int tickCounter = 0;
    private List<TileObject> cachedNearbyVeins = new ArrayList<>();
    private int ticksSinceLastMiningStart = 0;
    private boolean needsStateDetection = false;

    @Provides
    MotherlodeMineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MotherlodeMineConfig.class);
    }

    /**
     * Conditional logging helper
     */
    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][MotherlodeMine] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("=================================================");
        log("Motherlode Mine Auto-Miner STARTED");
        log("=================================================");

        // Initialize modules
        objectFinder = new MotherlodeMineObjectFinder(client, config);
        stateHandler = new MotherlodeMineStateHandler(client, config, objectFinder, this::log);

        overlay = new MotherlodeMineOverlay(client, this, config);
        overlayManager.add(overlay);

        // Reset local state
        tickCounter = 0;
        cachedNearbyVeins.clear();
        ticksSinceLastMiningStart = 0;

        // Flag state detection to run on first game tick (must be on client thread)
        needsStateDetection = true;
    }

    @Override
    protected void shutDown() {
        log("=================================================");
        log("Motherlode Mine Auto-Miner STOPPED");
        log("=================================================");
        overlayManager.remove(overlay);
        resetState();
    }

    private void resetState() {
        tickCounter = 0;
        cachedNearbyVeins.clear();
        ticksSinceLastMiningStart = 0;
        needsStateDetection = false;
        if (stateHandler != null) {
            stateHandler.resetState();
        }
        log("State reset");
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
     * Get current player animation (for debugging)
     */
    public int getCurrentAnimation() {
        Player player = client.getLocalPlayer();
        return player != null ? player.getAnimation() : -1;
    }

    /**
     * Log all nearby tile objects for debugging
     */
    private void logNearbyObjects() {
        log("========== SCANNING ALL NEARBY TILE OBJECTS ==========");
        try {
            Player player = client.getLocalPlayer();
            if (player == null) {
                log("Player is null, cannot scan");
                return;
            }

            WorldPoint playerLoc = player.getWorldLocation();
            log("Player location: " + playerLoc);

            List<TileObject> allObjects = TileObjects.search().result();
            log("Total tile objects found: " + allObjects.size());

            // Log first 30 objects with details
            int count = 0;
            for (TileObject obj : allObjects) {
                if (count < 30) {
                    int id = obj.getId();
                    WorldPoint objLoc = obj.getWorldLocation();
                    int distance = playerLoc.distanceTo(objLoc);
                    log(String.format("  [%02d] ID: %d | Location: %s | Distance: %d | Name: %s",
                        count + 1, id, objLoc, distance, obj.toString()));
                    count++;
                }
            }

            // Check for objects matching our known vein IDs
            log("--- Checking for known pay-dirt vein IDs ---");
            boolean foundAny = false;
            for (TileObject obj : allObjects) {
                int id = obj.getId();
                for (int veinId : PAYDIRT_VEIN_IDS) {
                    if (id == veinId) {
                        WorldPoint objLoc = obj.getWorldLocation();
                        int distance = playerLoc.distanceTo(objLoc);
                        log(String.format("  *** FOUND MATCHING VEIN ID: %d | Location: %s | Distance: %d",
                            id, objLoc, distance));
                        foundAny = true;
                    }
                }
            }
            if (!foundAny) {
                log("  (No matching vein IDs found - you may need to add IDs to the array)");
            }

            // Log objects near player (within 10 tiles)
            log("--- Objects within 10 tiles of player ---");
            List<TileObject> nearbyObjects = new ArrayList<>();
            for (TileObject obj : allObjects) {
                WorldPoint objLoc = obj.getWorldLocation();
                int distance = playerLoc.distanceTo(objLoc);
                if (distance <= 10) {
                    nearbyObjects.add(obj);
                    log(String.format("  ID: %d | Distance: %d | Location: %s",
                        obj.getId(), distance, objLoc));
                }
            }
            log("Found " + nearbyObjects.size() + " objects within 10 tiles");

        } catch (Exception e) {
            log("ERROR scanning objects: " + e.getMessage());
            e.printStackTrace();
        }
        log("========== END SCAN ==========");
    }

    /**
     * Check if inventory is full
     */
    private boolean isInventoryFull() {
        return Inventory.getEmptySlots() == 0;
    }

    /**
     * Get number of empty inventory slots
     */
    private int getEmptySlots() {
        return Inventory.getEmptySlots();
    }

    // Getters for overlay
    public TileObject getCurrentTarget() {
        return stateHandler != null ? stateHandler.getCurrentTarget() : null;
    }

    public List<TileObject> getCachedNearbyVeins() {
        return cachedNearbyVeins;
    }

    public int getEmptySlotsForOverlay() {
        return getEmptySlots();
    }

    public String getCurrentStateForOverlay() {
        return stateHandler != null ? stateHandler.getCurrentState().toString() : "UNKNOWN";
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
        int animation = getCurrentAnimation();

        if (currentlyMining) {
            if (!stateHandler.getWasMining()) {
                log(String.format("*** STARTED MINING - Animation: %d", animation));
                ticksSinceLastMiningStart = 0;
            }
            stateHandler.setWasMining(true);
            stateHandler.setIdleTickCount(0);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Perform state detection on first tick (must be on client thread)
        if (needsStateDetection) {
            stateHandler.detectAndSetInitialState();
            needsStateDetection = false;
        }

        tickCounter++;
        boolean currentlyMining = isMining();
        int animation = getCurrentAnimation();

        // Log player coordinates if enabled (every 2 ticks for readability)
        if (config.logCoordinates() && tickCounter % 2 == 0) {
            Player player = client.getLocalPlayer();
            if (player != null) {
                WorldPoint loc = player.getWorldLocation();
                System.out.println(String.format("=== PLAYER COORDS === X: %d | Y: %d | Plane: %d",
                    loc.getX(), loc.getY(), loc.getPlane()));
            }
        }

        // Track time since last successful mining start
        if (currentlyMining) {
            ticksSinceLastMiningStart = 0;
        } else {
            ticksSinceLastMiningStart++;
        }

        // Update cached veins for overlay
        cachedNearbyVeins = objectFinder.findAllVeins();

        // Log animation every 5 ticks for debugging
        if (tickCounter % 5 == 0) {
            int emptySlots = getEmptySlots();
            log(String.format("[Tick %d] Animation: %d | Mining: %s | WasMining: %s | IdleTicks: %d | InvSlots: %d",
                tickCounter, animation, currentlyMining, stateHandler.getWasMining(),
                stateHandler.getIdleTickCount(), emptySlots));

            // If we see an animation that's not -1 and not in our list, alert the user
            if (animation != -1 && !currentlyMining) {
                boolean knownAnimation = false;
                for (int anim : MINING_ANIMATIONS) {
                    if (animation == anim) {
                        knownAnimation = true;
                        break;
                    }
                }
                if (!knownAnimation && animation != 808 && animation != 813) { // 808/813 are idle animations
                    log(String.format("??? UNKNOWN ANIMATION: %d - You may need to add this to MINING_ANIMATIONS array", animation));
                }
            }
        }

        // Periodic status check
        if (tickCounter % 30 == 0) {
            Player player = client.getLocalPlayer();
            WorldPoint playerLoc = player != null ? player.getWorldLocation() : null;

            log(String.format("=== STATUS: State: %s | Veins nearby: %d | Target: %s | Inv slots: %d ===",
                stateHandler.getCurrentState(),
                cachedNearbyVeins.size(),
                stateHandler.getCurrentTarget() != null ? "ID " + stateHandler.getCurrentTarget().getId() : "None",
                getEmptySlots()));

            if (playerLoc != null) {
                log(String.format("Current location: X=%d, Y=%d, Plane=%d",
                    playerLoc.getX(), playerLoc.getY(), playerLoc.getPlane()));

                if (config.useMiningRegion()) {
                    boolean inRegion = objectFinder.isInMiningRegion(playerLoc);
                    log(String.format("Mining region: X[%d-%d] Y[%d-%d] | Player in region: %s",
                        config.regionMinX(), config.regionMaxX(),
                        config.regionMinY(), config.regionMaxY(),
                        inRegion ? "YES" : "NO"));
                }
            }

            if (config.logAllNearbyObjects()) {
                logNearbyObjects();
            }
        }

        if (!config.enableAutoMining()) {
            return;
        }

        // ===== STATE MACHINE LOGIC =====

        // Check if inventory is full and handle accordingly
        if (isInventoryFull() && stateHandler.getCurrentState() == PluginState.MINING) {
            stateHandler.handleInventoryFull();
            if (config.stopWhenFull() && !config.autoDeposit()) {
                return; // Don't auto-click new veins when inventory is full
            }
        }

        // Don't check sack during RETURNING or MINING - we want to complete current mining cycle
        // Sack checking is handled in DEPOSITING state where we can make the decision
        // before leaving the hopper area

        // Drop gems every tick while in MINING state (frees up inventory space)
        if (stateHandler.getCurrentState() == PluginState.MINING) {
            stateHandler.dropGemsIfEnabled();
        }

        // Handle different states
        switch (stateHandler.getCurrentState()) {
            case MINING:
                Optional<TileObject> nearestVein = objectFinder.findNearestVein();
                stateHandler.handleMiningState(currentlyMining, animation, nearestVein);
                break;

            case TRAVELING_TO_HOPPER:
                stateHandler.handleTravelingToHopperState();
                break;

            case DEPOSITING_IN_HOPPER:
                stateHandler.handleDepositingInHopperState();
                break;

            case EMPTYING_SACK:
                stateHandler.handleEmptyingSackState();
                break;

            case TRAVELING_TO_DEPOSIT_BOX:
                stateHandler.handleTravelingToDepositBoxState();
                break;

            case DEPOSITING_ORE_IN_DEPOSIT_BOX:
                stateHandler.handleDepositingOreInDepositBoxState();
                break;

            case RETURNING:
                stateHandler.handleReturningToPayDirtState();
                break;
        }
    }
}
