package com.example.Salvaging;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.InteractionApi.HumanLikeDelay;
import com.example.InteractionApi.HumanLikeDropper;
import com.example.Packets.MousePackets;
import com.example.Packets.ObjectPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.Salvaging.SalvagingConstants.*;

@PluginDescriptor(
    name = "A Simple Salvaging",
    description = "Simple salvaging helper",
    tags = {"sailing", "salvaging"},
    hidden = false,
    enabledByDefault = false
)
public class SimpleSalvagingPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private SalvagingConfig config;

    private SalvagingObjectFinder objectFinder;

    private int ticksSinceLastAction = 999;
    private int cooldownTicks = 2;

    private boolean wasSalvaging = false;
    private int idleTicksSinceSalvaging = 0;
    private int randomDelay = 0;

    private boolean wasSorting = false;
    private int idleTicksSinceSorting = 0;

    private HumanLikeDropper dropper = null;

    @Provides
    SalvagingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SalvagingConfig.class);
    }

    @Override
    protected void startUp() {
        log("Simple Salvaging plugin started - Mode: " + config.mode());
        objectFinder = new SalvagingObjectFinder(client, config);
        ticksSinceLastAction = 999;
    }

    @Override
    protected void shutDown() {
        log("Simple Salvaging plugin stopped");
        objectFinder = null;
        dropper = null;
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[SimpleSalvaging] " + message);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject obj = event.getGameObject();
        Player player = client.getLocalPlayer();

        // Only track objects in same WorldView as player (filters out other boats)
        if (player != null && obj.getWorldView() == player.getWorldView()) {
            objectFinder.addObject(obj);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        objectFinder.removeObject(event.getGameObject());
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        int animation = client.getLocalPlayer().getAnimation();

        // Log all animations if enabled (for discovering sorting animation IDs)
        if (config.logAnimations()) {
            System.out.println("[SimpleSalvaging] Animation: " + animation);
        }

        boolean currentlySalvaging = isSalvagingAnimation(animation);
        boolean currentlySorting = isSortingAnimation(animation);

        // Track salvaging
        if (currentlySalvaging && !wasSalvaging) {
            log("Started salvaging - animation: " + animation);
            wasSalvaging = true;
        } else if (!currentlySalvaging && wasSalvaging) {
            log("Stopped salvaging - animation: " + animation);
            wasSalvaging = false;
            idleTicksSinceSalvaging = 0;
            randomDelay = HumanLikeDelay.generate(HumanLikeDelay.RESOURCE_DEPLETION);
        }

        // Track sorting
        if (currentlySorting && !wasSorting) {
            log("Started sorting - animation: " + animation);
            wasSorting = true;
            idleTicksSinceSorting = 0;
        } else if (!currentlySorting && wasSorting) {
            log("Stopped sorting - animation: " + animation);
            wasSorting = false;
            idleTicksSinceSorting = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN || !config.enablePlugin()) {
            return;
        }

        ticksSinceLastAction++;

        // Handle post-salvaging delay
        if (!wasSalvaging && idleTicksSinceSalvaging < randomDelay) {
            idleTicksSinceSalvaging++;
            return;
        }

        if (config.mode() == SalvagingConfig.Mode.SALVAGING) {
            handleSalvagingMode();
        } else {
            handleSortingMode();
        }
    }

    private void handleSalvagingMode() {
        // Wait for cooldown
        if (ticksSinceLastAction < cooldownTicks) {
            return;
        }

        // If inventory full with salvage, deposit
        if (isInventoryFullOfSalvage()) {
            log("Inventory full, depositing...");
            depositSalvage();
            ticksSinceLastAction = 0;
            cooldownTicks = 2;
            return;
        }

        // If not salvaging, click hook
        if (!wasSalvaging) {
            Optional<TileObject> hook = objectFinder.findNearestSalvagingHook();
            if (hook.isPresent()) {
                log("Clicking salvaging hook");
                clickSalvagingHook(hook.get());
                ticksSinceLastAction = 0;
                cooldownTicks = HumanLikeDelay.generate(HumanLikeDelay.INTERACTION_COOLDOWN);
            }
        }
    }

    private void handleSortingMode() {
        // If currently dropping, continue
        if (dropper != null) {
            boolean hasMore = dropper.dropNextBatch();
            if (!hasMore) {
                log("Finished dropping junk - will open cargo hold next");
                dropper = null;
                ticksSinceLastAction = 0;
            }
            return;
        }

        // If currently sorting, wait for animation to complete
        if (wasSorting) {
            return;
        }

        // After sorting stops, wait longer before next action (was interrupting)
        if (idleTicksSinceSorting < 5) {
            idleTicksSinceSorting++;
            return;
        }

        // Wait for cooldown
        if (ticksSinceLastAction < cooldownTicks) {
            return;
        }

        // Priority 1: If has junk, drop it
        if (hasJunkInInventory()) {
            log("Dropping junk items...");
            startDroppingJunk();
            return;
        }

        // Priority 2: If inventory is FULL of salvage (28 items), click sorting table
        if (isInventoryFullOfSalvage()) {
            Optional<TileObject> sortingTable = objectFinder.findSortingStation();
            if (sortingTable.isPresent()) {
                log("Inventory full - clicking sorting table");
                clickSortingTable(sortingTable.get());
                ticksSinceLastAction = 0;
                cooldownTicks = 3; // Longer cooldown to avoid spam-clicking during sorting
            } else {
                log("!!! Sorting table not found");
            }
            return;
        }

        // Priority 3: If has any salvage but not full, open cargo hold and wait for user to withdraw
        if (hasSalvageInInventory()) {
            log("Have salvage but inventory not full - waiting for you to withdraw more");
            // Don't do anything - wait for user to fill inventory
            return;
        }

        // Priority 4: Inventory empty - open cargo hold for user to withdraw
        log("Opening cargo hold - waiting for you to withdraw salvage...");
        openCargoHold();
        ticksSinceLastAction = 0;
        cooldownTicks = 3;
    }

    private boolean isSalvagingAnimation(int animation) {
        for (int salvageAnim : SALVAGING_ANIMATIONS) {
            if (animation == salvageAnim) {
                return true;
            }
        }
        return false;
    }

    private boolean isSortingAnimation(int animation) {
        for (int sortAnim : SORTING_ANIMATIONS) {
            if (animation == sortAnim) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryFullOfSalvage() {
        List<Widget> items = Inventory.search().result();
        if (items.size() < 28) {
            return false;
        }

        // Check if at least some items are salvage
        for (Widget item : items) {
            for (int salvageId : SALVAGE_ITEM_IDS) {
                if (item.getItemId() == salvageId) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSalvageInInventory() {
        for (int salvageId : SALVAGE_ITEM_IDS) {
            if (Inventory.search().withId(salvageId).first().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJunkInInventory() {
        Set<Integer> junkIds = parseJunkIds();
        for (int junkId : junkIds) {
            if (Inventory.search().withId(junkId).first().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> parseJunkIds() {
        Set<Integer> junkIds = new HashSet<>();
        String configStr = config.itemIDsToDrop();
        if (configStr == null || configStr.trim().isEmpty()) {
            return junkIds;
        }

        for (String id : configStr.split(",")) {
            try {
                junkIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                log("Invalid junk ID: " + id);
            }
        }
        return junkIds;
    }

    private void clickSalvagingHook(TileObject hook) {
        MousePackets.queueClickPacket();
        ObjectPackets.queueObjectAction(hook, false, "Deploy");
    }

    private void depositSalvage() {
        Optional<TileObject> cargoHold = objectFinder.findCargoHold();
        if (cargoHold.isPresent()) {
            MousePackets.queueClickPacket();
            ObjectPackets.queueObjectAction(cargoHold.get(), false, "Quick-deposit");
        } else {
            log("!!! Cargo hold not found");
        }
    }

    private void openCargoHold() {
        Optional<TileObject> cargoHold = objectFinder.findCargoHold();
        if (cargoHold.isPresent()) {
            MousePackets.queueClickPacket();
            ObjectPackets.queueObjectAction(cargoHold.get(), false, "Open");
        } else {
            log("!!! Cargo hold not found");
        }
    }

    private void clickSortingTable(TileObject table) {
        MousePackets.queueClickPacket();
        ObjectPackets.queueObjectAction(table, false, "Sort");
    }

    private void startDroppingJunk() {
        Set<Integer> junkIds = parseJunkIds();
        if (junkIds.isEmpty()) {
            log("No junk IDs configured");
            return;
        }

        dropper = new HumanLikeDropper(junkIds);
        log("Created dropper for " + junkIds.size() + " junk item types");
    }
}
