package com.example.NewTrees;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.InteractionApi.HumanLikeDelay;
import com.example.InteractionApi.HumanLikeDropper;
import com.example.InteractionApi.RealisticClickHelper;
import com.example.InteractionApi.TileObjectInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.ObjectPackets;
import com.example.Packets.WidgetPackets;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@PluginDescriptor(
        name = "A New Trees",
        description = "New tree helper",
        tags = {"woodcutting", "trees", "skilling", "training"},
        hidden = false,
        enabledByDefault = false
)
public class NewTreesPlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private NewTreesConfig config;

    public static final int[] TEAK_TREE_IDS = {
            30480,
            30481,
            30482
    };

    public static final int TEAK_LOG_ID = 6333; // Teak logs item ID
    public static final int LOG_BASKET_ID = 28142; // Log basket item ID

    private boolean wasWoodcutting = false;
    private boolean isDropping = false;
    private HumanLikeDropper dropper = null;
    private boolean hasEmptiedBasketThisCycle = false;
    private int idleTickCount = 0;
    private int currentInteractionCooldown = 0;
    private int ticksSinceLastInteraction = 0;
    private int randomTreeDepletionDelay = 0;
    private int treeRespawnedDelay = 0;
    private int treeRespawnTickCounter = 0;
    private int prevTreeCount = 0;
    private int currentTreeCount = 0;
    private boolean treeRespawnDetected = false;

    @Provides
    NewTreesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NewTreesConfig.class);
    }

    /**
     * Conditional logging helper
     */
    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][NewTrees] " + message);
        }
    }

    protected void startUp(){
        log("New trees started");
        // Enable debug logging for RealisticClickHelper
        RealisticClickHelper.setLoggingEnabled(config.debugLogging());
    }

    protected void shutDown(){
        log("New trees stopped");
    }

    /**
     * Get current player animation (for debugging)
     */
    private int getCurrentAnimation() {
        Player player = client.getLocalPlayer();
        return player != null ? player.getAnimation() : -1;
    }

    /**
     * Check if the player is currently performing a woodcutting animation
     */
    private boolean isWoodcutting() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        int animation = player.getAnimation();
        return animation == 10071;
    }

    private void clickTree() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

//        Optional<TileObject> tree = TileObjects.search().withId(10822).withinDistance(config.treeDistance()).first();
        // Search for any tree within bounds
        Optional<TileObject> tree = Optional.empty();
        for (int treeId : TEAK_TREE_IDS) {
            tree = TileObjects.search()
                    .withId(treeId)
                    .withinDistance(config.treeDistance())
                    .withAction("Chop down")
                    .first();
            if (tree.isPresent()) {
                break;
            }
        }

        if (tree.isEmpty()) {
            log("No tree found - both trees may be depleted, will retry next tick");
            return;
        }

        TileObject treeObj = tree.get();
        WorldPoint treeLocation = treeObj.getWorldLocation();

        // Always log clicks (not just debug mode)
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "][NewTrees] >>> ATTEMPTING TO CLICK TREE at x=" +
                treeLocation.getX() + ", y=" + treeLocation.getY());

        // Generate new weighted random cooldown for next interaction
        currentInteractionCooldown = HumanLikeDelay.generate(HumanLikeDelay.INTERACTION_COOLDOWN);
        log(String.format("Generated interaction cooldown: %d ticks", currentInteractionCooldown));

        // Use camera rotation if tree is off-screen
        java.awt.Point clickPoint = RealisticClickHelper.getTileObjectClickPoint(treeObj, true);
        if (clickPoint != null) {
            MousePackets.queueClickPacket(clickPoint.x, clickPoint.y);
            ObjectPackets.queueObjectAction(treeObj, false, "Chop down");
            System.out.println("[" + timestamp + "][NewTrees] SUCCESS: Queued click packet at (" +
                    clickPoint.x + "," + clickPoint.y + ") and object action");
        } else {
            // Fallback: use TileObjectInteraction if we can't get a click point
            System.out.println("[" + timestamp + "][NewTrees] Could not get click point, using fallback interaction");
            TileObjectInteraction.interact(treeObj, "Chop down");
        }

        ticksSinceLastInteraction = 0;
    }

    private int countAvailableTrees(){
        Player player = client.getLocalPlayer();
        if (player == null) {
            return -1;
        }
        int count = 0;
        for (int treeId : TEAK_TREE_IDS) {
            count += TileObjects.search()
                    .withId(treeId)
                    .withinDistance(config.treeDistance())
                    .withAction("Chop down")
                    .result()
                    .size();
        }
        return count;
    }

    /**
     * Check if inventory is full (all 28 slots occupied)
     */
    private boolean isInventoryFull() {
        return Inventory.getEmptySlots() == 0;
    }

    /**
     * Empty the log basket if it exists in inventory
     * @return true if basket was found and emptied, false otherwise
     */
    private boolean emptyLogBasket() {
        return Inventory.search()
                .withId(LOG_BASKET_ID)
                .first()
                .map(basket -> {
                    String timestamp = LocalDateTime.now().format(FORMATTER);
                    System.out.println("[" + timestamp + "][NewTrees] Emptying log basket");
                    WidgetPackets.queueWidgetSubAction(basket, "Check", "Empty");
                    return true;
                })
                .orElse(false);
    }

    /**
     * Start the dropping process
     */
    private void startDropping() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "][NewTrees] Starting to drop logs");

        isDropping = true;
        hasEmptiedBasketThisCycle = false; // Reset at start of new drop cycle
        dropper = new HumanLikeDropper(TEAK_LOG_ID);
        dropper.setLogging(config.debugLogging());
    }

    /**
     * Continue dropping items (call each tick while dropping)
     * @return true if still dropping, false if finished
     */
    private boolean continueDropping() {
        if (dropper == null) {
            return false;
        }

        boolean hasMore = dropper.dropNextBatch();

        if (!hasMore) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][NewTrees] Finished dropping all logs");
            isDropping = false;
            dropper = null;

            // Check if we need to empty the basket
            if (!hasEmptiedBasketThisCycle) {
                boolean basketExists = Inventory.search().withId(LOG_BASKET_ID).first().isPresent();
                if (basketExists) {
                    // Empty basket on next tick (this will fill inventory again)
                    log("Will empty log basket next tick");
                } else {
                    log("No log basket found, resuming woodcutting");
                }
            } else {
                // We already emptied the basket and dropped those logs
                // Don't reset flag - it will be reset when we start a new drop cycle
                log("Finished full drop cycle (inventory + basket), resuming woodcutting");
            }

            return false;
        }

        return true;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {

        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        boolean currentlyWoodcutting = isWoodcutting();

        if (currentlyWoodcutting) {
            if (!wasWoodcutting) {
                // Just started woodcutting
                String timestamp = LocalDateTime.now().format(FORMATTER);
                System.out.println("[" + timestamp + "][NewTrees] Started woodcutting (animation: " + getCurrentAnimation() + ")");
            }
            wasWoodcutting = true;
            idleTickCount = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Update debug logging setting
        RealisticClickHelper.setLoggingEnabled(config.debugLogging());

        ticksSinceLastInteraction++;

        // Handle dropping state - takes priority over everything else
        if (isDropping) {
            continueDropping();
            return; // Don't do anything else while dropping
        }

        // Check if we just finished dropping and need to empty the basket
        if (!isDropping && !hasEmptiedBasketThisCycle) {
            boolean basketExists = Inventory.search().withId(LOG_BASKET_ID).first().isPresent();
            if (basketExists && Inventory.search().withId(TEAK_LOG_ID).result().isEmpty()) {
                // We finished dropping inventory logs and basket exists, empty it now
                boolean basketEmptied = emptyLogBasket();
                if (basketEmptied) {
                    hasEmptiedBasketThisCycle = true;
                    log("Log basket emptied, inventory will fill up next tick");
                    return;
                }
            }
        }

        // Check if inventory is full and start dropping if needed
        if (isInventoryFull()) {
            startDropping();
            return;
        }

        currentTreeCount = countAvailableTrees();

        boolean currentlyWoodcutting = isWoodcutting();

        // Handle idle detection and tree clicking
        if (wasWoodcutting && !currentlyWoodcutting) {
            // First tick we notice we stopped - generate weighted random delay
            if (idleTickCount == 0) {
                randomTreeDepletionDelay = HumanLikeDelay.generate(HumanLikeDelay.RESOURCE_DEPLETION);
                log(String.format("Tree depleted, generated delay: %d ticks", randomTreeDepletionDelay));
            }

            idleTickCount++;
            log(String.format("Tree depletion delay: %d/%d", idleTickCount, randomTreeDepletionDelay));

            // Wait for the random delay before clicking new tree
            if (idleTickCount >= randomTreeDepletionDelay) {
                // Only interact if cooldown has passed
                if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                    log(">>> Attempting to find and click tree in bounds");
                    clickTree();

                    // Reset state
                    wasWoodcutting = false;
                    idleTickCount = 0;
                    randomTreeDepletionDelay = 0;
                }
            }
        } else if (currentlyWoodcutting) {
            // Reset idle counter if we're woodcutting
            idleTickCount = 0;
            randomTreeDepletionDelay = 0;
        }

        // Handle tree respawn detection (when both trees were depleted and one respawns)
        if (!wasWoodcutting && !currentlyWoodcutting){
            // Check if a new tree has spawned
            if (currentTreeCount > prevTreeCount && !treeRespawnDetected) {
                // Tree just respawned - generate delay ONCE
                treeRespawnedDelay = HumanLikeDelay.generate(HumanLikeDelay.RESOURCE_DEPLETION);
                treeRespawnDetected = true;
                treeRespawnTickCounter = 0;
                log(String.format("New tree respawned, generated delay before clicking: %d ticks", treeRespawnedDelay));
            }

            if (treeRespawnDetected) {
                treeRespawnTickCounter++;
                log(String.format("Tree respawn click delay: %d/%d", treeRespawnTickCounter, treeRespawnedDelay));

                // Wait for the random delay before clicking new tree
                if (treeRespawnTickCounter >= treeRespawnedDelay) {
                    // Only interact if cooldown has passed
                    if (ticksSinceLastInteraction >= currentInteractionCooldown) {
                        log(">>> Attempting to find and click tree <<<");
                        clickTree();

                        // Reset respawn state
                        treeRespawnDetected = false;
                        treeRespawnTickCounter = 0;
                        treeRespawnedDelay = 0;
                    }
                }
            }
        } else {
            // Reset respawn detection when we start woodcutting again
            treeRespawnDetected = false;
            treeRespawnTickCounter = 0;
        }

        prevTreeCount = currentTreeCount;
    }
}