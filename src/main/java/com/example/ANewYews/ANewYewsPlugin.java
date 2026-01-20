package com.example.ANewYews;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.Widgets;
import com.example.Packets.MousePackets;
import com.example.Packets.MovementPackets;
import com.example.Packets.NPCPackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.event.KeyEvent;
import java.util.Optional;

@PluginDescriptor(
        name = "A New Yews",
        description = "Yew longbow stringing helper",
        tags = {"fletching", "yew", "longbow"},
        enabledByDefault = false
)
public class ANewYewsPlugin extends Plugin {

    private static final int BANKER_ID = 1633;
    private static final int BOW_STRING_ID = 1777;
    private static final int YEW_LONGBOW_UNSTRUNG_ID = 66;
    private static final int FLETCHING_ANIMATION_ID = 6688;
    private static final int IDLE_TICKS_THRESHOLD = 2;

    private enum State {
        BANKING,
        USE_ITEMS,
        PRESS_SPACEBAR,
        FLETCHING
    }

    private State currentState = State.BANKING;
    private int tickDelay = 0;
    private int idleTickCount = 0;

    @Inject
    private Client client;

    @Inject
    private ANewYewsConfig config;

    @Provides
    ANewYewsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ANewYewsConfig.class);
    }

    @Override
    protected void startUp() {
        log("Plugin started");
        currentState = State.BANKING;
        tickDelay = 0;
        idleTickCount = 0;
    }

    @Override
    protected void shutDown() {
        log("Plugin stopped");
        currentState = State.BANKING;
        tickDelay = 0;
        idleTickCount = 0;
    }

    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            System.out.println("[ANewYews] [" + timestamp + "] " + message);
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
            case USE_ITEMS:
                handleUseItemsState();
                break;
            case PRESS_SPACEBAR:
                handlePressSpacebarState();
                break;
            case FLETCHING:
                handleFletchingState();
                break;
        }
    }


    private void handleBankingState() {
        boolean hasBowStrings = Inventory.search().withId(BOW_STRING_ID).first().isPresent();
        boolean hasUnstrungBows = Inventory.search().withId(YEW_LONGBOW_UNSTRUNG_ID).first().isPresent();

        // If we have materials and bank is closed, move to USE_ITEMS
        if (hasBowStrings && hasUnstrungBows && !Bank.isOpen()) {
            log("Have materials, switching to USE_ITEMS");
            currentState = State.USE_ITEMS;
            return;
        }

        // Handle banking
        if (Bank.isOpen()) {
            if (hasBowStrings && hasUnstrungBows) {
                closeBank();
            } else if (Inventory.search().result().isEmpty()) {
                withdrawItems();
            } else {
                depositInventory();
            }
        } else {
            openBank();
        }
    }

    private void handleUseItemsState() {
        boolean hasBowStrings = Inventory.search().withId(BOW_STRING_ID).first().isPresent();
        boolean hasUnstrungBows = Inventory.search().withId(YEW_LONGBOW_UNSTRUNG_ID).first().isPresent();

        if (hasBowStrings && hasUnstrungBows) {
            useBowStringOnBow();
            tickDelay = 1; // Wait for make interface to appear
            currentState = State.PRESS_SPACEBAR;
        } else {
            // No materials, go back to banking
            currentState = State.BANKING;
        }
    }

    private void handlePressSpacebarState() {
        log("Pressing spacebar to make");
        pressSpace();
        tickDelay = 2; // Wait for fletching animation to start
        idleTickCount = 0;
        currentState = State.FLETCHING;
    }

    private void handleFletchingState() {
        int currentAnim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;

        if (currentAnim == FLETCHING_ANIMATION_ID) {
            // Currently fletching, reset idle counter
            idleTickCount = 0;
            return;
        }

        // Not fletching, increment idle counter
        idleTickCount++;
        log("Idle tick: " + idleTickCount);

        if (idleTickCount >= IDLE_TICKS_THRESHOLD) {
            // Been idle for a while, fletching is done
            log("Fletching complete (idle for " + idleTickCount + " ticks)");
            idleTickCount = 0;
            currentState = State.BANKING;
        }
    }

    private void openBank() {
        Optional<NPC> banker = NPCs.search()
                .withId(BANKER_ID)
                .withAction("Bank")
                .nearestToPlayer();

        if (banker.isPresent()) {
            log("Found banker, clicking");
            MousePackets.queueClickPacket();
            NPCPackets.queueNPCAction(banker.get(), "Bank");
        } else {
            log("No banker found");
        }
    }

    private void closeBank() {
        log("Closing bank");
        MousePackets.queueClickPacket();
        MovementPackets.queueMovement(client.getLocalPlayer().getWorldLocation());
    }

    private void depositInventory() {
        Optional<Widget> depositButton = Widgets.search()
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

    private void withdrawItems() {
        Optional<Widget> bowStrings = Bank.search().withId(BOW_STRING_ID).first();
        Optional<Widget> unstrungBows = Bank.search().withId(YEW_LONGBOW_UNSTRUNG_ID).first();

        if (bowStrings.isPresent()) {
            log("Withdrawing 14 bow strings");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(bowStrings.get(), "Withdraw-14");
        } else {
            log("No bow strings in bank");
        }

        if (unstrungBows.isPresent()) {
            log("Withdrawing 14 unstrung bows");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(unstrungBows.get(), "Withdraw-14");
        } else {
            log("No unstrung bows in bank");
        }
    }

    private void useBowStringOnBow() {
        Optional<Widget> bowString = Inventory.search().withId(BOW_STRING_ID).first();
        Optional<Widget> unstrungBow = Inventory.search().withId(YEW_LONGBOW_UNSTRUNG_ID).first();

        if (bowString.isPresent() && unstrungBow.isPresent()) {
            log("Using bow string on unstrung bow");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetOnWidget(bowString.get(), unstrungBow.get());
        }
    }

    private void pressSpace() {
        KeyEvent keyPress = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyPress);
        KeyEvent keyRelease = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyRelease);
    }
}
