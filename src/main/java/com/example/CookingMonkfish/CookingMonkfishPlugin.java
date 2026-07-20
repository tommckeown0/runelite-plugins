package com.example.CookingMonkfish;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.Packets.MousePackets;
import com.example.Packets.MovementPackets;
import com.example.Packets.NPCPackets;
import com.example.Packets.ObjectPackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.event.KeyEvent;
import java.util.Optional;

@PluginDescriptor(
        name = "A Cooking Monkfish",
        description = "Monkfish cooking helper",
        tags = {"cooking", "monkfish", "skilling"},
        enabledByDefault = false
)
public class CookingMonkfishPlugin extends Plugin {

    private static final int RAW_MONKFISH_ID = 7944;
    private static final int COOKING_ANIMATION_ID = 896;
    private static final int IDLE_TICKS_THRESHOLD = 3;

    // Common range/stove object IDs - can be expanded as needed
    private static final int[] RANGE_IDS = {
            26181,  // Hosidius range
            9682,   // Regular range
            114,    // Stove
            2728,   // Clay oven
            12269,  // Myths' Guild range
            21302,  // Rogues' Den fire
            4172  ,  // Castle Wars range
    };

    private enum State {
        BANKING,
        CLICKING_RANGE,
        PRESS_SPACEBAR,
        WAITING_FOR_COOKING,
        COOKING
    }

    private State currentState = State.BANKING;
    private int tickDelay = 0;
    private int idleTickCount = 0;
    private int waitingTicks = 0;
    private static final int MAX_WAITING_TICKS = 5;

    @Inject
    private Client client;

    @Inject
    private CookingMonkfishConfig config;

    @Provides
    CookingMonkfishConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CookingMonkfishConfig.class);
    }

    @Override
    protected void startUp() {
        log("Plugin started");
        currentState = State.BANKING;
        tickDelay = 0;
        idleTickCount = 0;
        waitingTicks = 0;
    }

    @Override
    protected void shutDown() {
        log("Plugin stopped");
        currentState = State.BANKING;
        tickDelay = 0;
        idleTickCount = 0;
        waitingTicks = 0;
    }

    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            System.out.println("[CookingMonkfish] [" + timestamp + "] " + message);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.enablePlugin()) return;
        if (client.getGameState() != GameState.LOGGED_IN) return;

        int currentAnim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;
        log("Tick - State: " + currentState + ", Animation: " + currentAnim + ", Delay: " + tickDelay + ", IdleTicks: " + idleTickCount);

        // Handle tick delay
        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        switch (currentState) {
            case BANKING:
                handleBankingState();
                break;
            case CLICKING_RANGE:
                handleClickingRangeState();
                break;
            case PRESS_SPACEBAR:
                handlePressSpacebarState();
                break;
            case WAITING_FOR_COOKING:
                handleWaitingForCookingState();
                break;
            case COOKING:
                handleCookingState();
                break;
        }
    }

    private void handleBankingState() {
        boolean hasRawMonkfish = Inventory.search().withId(RAW_MONKFISH_ID).first().isPresent();

        // If we have raw monkfish and bank is closed, move to CLICKING_RANGE
        if (hasRawMonkfish && !Bank.isOpen()) {
            log("Have raw monkfish, switching to CLICKING_RANGE");
            currentState = State.CLICKING_RANGE;
            return;
        }

        // Handle banking
        if (Bank.isOpen()) {
            if (hasRawMonkfish) {
                closeBank();
            } else if (Inventory.search().result().isEmpty()) {
                withdrawMonkfish();
            } else {
                depositInventory();
            }
        } else {
            openBank();
        }
    }

    private void handleClickingRangeState() {
        boolean hasRawMonkfish = Inventory.search().withId(RAW_MONKFISH_ID).first().isPresent();

        if (hasRawMonkfish) {
            if (clickRange()) {
                tickDelay = 2; // Wait for make interface to appear
                currentState = State.PRESS_SPACEBAR;
            }
        } else {
            // No raw monkfish, go back to banking
            currentState = State.BANKING;
        }
    }

    private void handlePressSpacebarState() {
        log("Pressing spacebar to cook all");
        pressSpace();
        waitingTicks = 0;
        currentState = State.WAITING_FOR_COOKING;
    }

    private void handleWaitingForCookingState() {
        int currentAnim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;

        if (currentAnim == COOKING_ANIMATION_ID) {
            log("Cooking animation detected, now cooking");
            idleTickCount = 0;
            currentState = State.COOKING;
            return;
        }

        waitingTicks++;
        log("Waiting for cooking animation: " + waitingTicks + "/" + MAX_WAITING_TICKS + ", Animation: " + currentAnim);

        if (waitingTicks >= MAX_WAITING_TICKS) {
            log("Cooking animation did not start, going back to clicking range");
            waitingTicks = 0;
            currentState = State.CLICKING_RANGE;
        }
    }

    private void handleCookingState() {
        int currentAnim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;

        if (currentAnim == COOKING_ANIMATION_ID) {
            // Currently cooking, reset idle counter
            idleTickCount = 0;
            return;
        }

        // Not cooking, increment idle counter
        idleTickCount++;
        log("Idle tick: " + idleTickCount);

        if (idleTickCount >= IDLE_TICKS_THRESHOLD) {
            // Been idle for a while, cooking is done
            log("Cooking complete (idle for " + idleTickCount + " ticks)");
            idleTickCount = 0;
            currentState = State.BANKING;
        }
    }

    private void openBank() {
        // Try to find a banker NPC
        Optional<NPC> banker = NPCs.search()
                .withAction("Bank")
                .nearestToPlayer();

        if (banker.isPresent()) {
            log("Found banker, clicking");
            MousePackets.queueClickPacket();
            NPCPackets.queueNPCAction(banker.get(), "Bank");
            return;
        }

        // Try to find a bank booth object with "Bank" action
        Optional<TileObject> bankBooth = TileObjects.search()
                .withAction("Bank")
                .nearestToPlayer();

        if (bankBooth.isPresent()) {
            log("Found bank booth, clicking");
            MousePackets.queueClickPacket();
            ObjectPackets.queueObjectAction(bankBooth.get(), false, "Bank");
            return;
        }

        // Try to find a bank chest with "Use" action (e.g., Myth's Guild)
        Optional<TileObject> bankChest = TileObjects.search()
                .withAction("Use")
                .nameContains("Bank")
                .nearestToPlayer();

        if (bankChest.isPresent()) {
            log("Found bank chest, clicking");
            MousePackets.queueClickPacket();
            ObjectPackets.queueObjectAction(bankChest.get(), false, "Use");
            return;
        }

        log("No bank found");
    }

    private void closeBank() {
        log("Closing bank");
        MousePackets.queueClickPacket();
        MovementPackets.queueMovement(client.getLocalPlayer().getWorldLocation());
    }

    private void depositInventory() {
        Optional<Widget> depositButton = com.example.EthanApiPlugin.Collections.Widgets.search()
                .withAction("Deposit inventory")
                .first();

        if (depositButton.isPresent()) {
            log("Clicking deposit inventory");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(depositButton.get(), "Deposit inventory");
        } else {
            log("Deposit inventory button not found");
        }
    }

    private void withdrawMonkfish() {
        Optional<Widget> rawMonkfish = Bank.search().withId(RAW_MONKFISH_ID).first();

        if (rawMonkfish.isPresent()) {
            log("Withdrawing all raw monkfish");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(rawMonkfish.get(), "Withdraw-All");
        } else {
            log("No raw monkfish in bank");
        }
    }

    private boolean clickRange() {
        // Search for any range/stove object by ID first
        for (int rangeId : RANGE_IDS) {
            Optional<TileObject> range = TileObjects.search()
                    .withId(rangeId)
                    .nearestToPlayer();

            if (range.isPresent()) {
                log("Found range (ID: " + rangeId + "), clicking to cook");
                MousePackets.queueClickPacket();
                ObjectPackets.queueObjectAction(range.get(), false, "Cook");
                return true;
            }
        }

        // Fallback: search by action if no known range ID found
        Optional<TileObject> range = TileObjects.search()
                .withAction("Cook")
                .nearestToPlayer();

        if (range.isPresent()) {
            log("Found cooking object by action, clicking");
            MousePackets.queueClickPacket();
            ObjectPackets.queueObjectAction(range.get(), false, "Cook");
            return true;
        }

        log("No range/stove found");
        return false;
    }

    private void pressSpace() {
        KeyEvent keyPress = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyPress);
        KeyEvent keyRelease = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyRelease);
    }
}
