package com.example.GreenDhideBodies;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.BankInventory;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.Widgets;
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
        name = "A Green D'hide Bodies",
        description = "Green d'hide body crafting helper",
        tags = {"crafting", "dhide", "dragonhide", "leather", "skilling"},
        enabledByDefault = false
)
public class GreenDhideBodiesPlugin extends Plugin {

    private static final int GREEN_DRAGONLEATHER_ID = 1745;
    private static final int GREEN_DHIDE_BODY_ID = 1135;
    private static final int NEEDLE_ID = 29920;
    private static final int CRAFTING_ANIMATION_ID = 1249;
    private static final int IDLE_TICKS_THRESHOLD = 3;

    // Widget ID for make-all green d'hide body in crafting interface
    // The crafting interface typically uses widget group 270
    private static final int CRAFTING_INTERFACE_GROUP = 270;

    private enum State {
        BANKING,
        USE_ITEMS,
        SELECT_MAKE,
        CRAFTING
    }

    private State currentState = State.BANKING;
    private int tickDelay = 0;
    private int idleTickCount = 0;

    @Inject
    private Client client;

    @Inject
    private GreenDhideBodiesConfig config;

    @Provides
    GreenDhideBodiesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GreenDhideBodiesConfig.class);
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
            System.out.println("[GreenDhideBodies] [" + timestamp + "] " + message);
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
            case SELECT_MAKE:
                handleSelectMakeState();
                break;
            case CRAFTING:
                handleCraftingState();
                break;
        }
    }

    private void handleBankingState() {
        boolean hasNeedle = Inventory.search().withId(NEEDLE_ID).first().isPresent();
        boolean hasLeather = Inventory.search().withId(GREEN_DRAGONLEATHER_ID).first().isPresent();
        boolean hasBodies = Inventory.search().withId(GREEN_DHIDE_BODY_ID).first().isPresent();

        // If we have needle and leather and bank is closed, move to USE_ITEMS
        if (hasNeedle && hasLeather && !Bank.isOpen()) {
            log("Have materials, switching to USE_ITEMS");
            currentState = State.USE_ITEMS;
            return;
        }

        // Handle banking
        if (Bank.isOpen()) {
            if (hasNeedle && hasLeather) {
                closeBank();
            } else if (hasBodies) {
                // Deposit the crafted bodies first
                depositBodies();
            } else if (!hasNeedle) {
                // Withdraw needle if we don't have one
                withdrawNeedle();
            } else if (!hasLeather) {
                // Withdraw leather
                withdrawLeather();
            }
        } else {
            openBank();
        }
    }

    private void handleUseItemsState() {
        boolean hasNeedle = Inventory.search().withId(NEEDLE_ID).first().isPresent();
        boolean hasLeather = Inventory.search().withId(GREEN_DRAGONLEATHER_ID).first().isPresent();

        if (hasNeedle && hasLeather) {
            useNeedleOnLeather();
            tickDelay = 1; // Wait for make interface to appear
            currentState = State.SELECT_MAKE;
        } else {
            // Missing materials, go back to banking
            currentState = State.BANKING;
        }
    }

    private void handleSelectMakeState() {
        if (config.useSpacebarForMake()) {
            log("Pressing spacebar to make all");
            pressSpace();
        } else {
            log("Clicking make widget for green d'hide body");
            clickMakeWidget();
        }
        tickDelay = 2; // Wait for crafting animation to start
        idleTickCount = 0;
        currentState = State.CRAFTING;
    }

    private void handleCraftingState() {
        int currentAnim = client.getLocalPlayer() != null ? client.getLocalPlayer().getAnimation() : -1;

        if (currentAnim == CRAFTING_ANIMATION_ID) {
            // Currently crafting, reset idle counter
            idleTickCount = 0;
            return;
        }

        // Not crafting, increment idle counter
        idleTickCount++;
        log("Idle tick: " + idleTickCount);

        if (idleTickCount >= IDLE_TICKS_THRESHOLD) {
            // Been idle for a while, crafting is done
            log("Crafting complete (idle for " + idleTickCount + " ticks)");
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

    private void depositBodies() {
        Optional<Widget> bodies = BankInventory.search().withId(GREEN_DHIDE_BODY_ID).first();

        if (bodies.isPresent()) {
            log("Depositing green d'hide bodies");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(bodies.get(), "Deposit-All");
        }
    }

    private void withdrawNeedle() {
        Optional<Widget> needle = Bank.search().withId(NEEDLE_ID).first();

        if (needle.isPresent()) {
            log("Withdrawing needle");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(needle.get(), "Withdraw-1");
        } else {
            log("No needle in bank");
        }
    }

    private void withdrawLeather() {
        Optional<Widget> leather = Bank.search().withId(GREEN_DRAGONLEATHER_ID).first();

        if (leather.isPresent()) {
            log("Withdrawing all green dragonleather");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(leather.get(), "Withdraw-All");
        } else {
            log("No green dragonleather in bank");
        }
    }

    private void useNeedleOnLeather() {
        Optional<Widget> needle = Inventory.search().withId(NEEDLE_ID).first();
        Optional<Widget> leather = Inventory.search().withId(GREEN_DRAGONLEATHER_ID).first();

        if (needle.isPresent() && leather.isPresent()) {
            log("Using needle on green dragonleather");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetOnWidget(needle.get(), leather.get());
        }
    }

    private void clickMakeWidget() {
        // Try to find the green d'hide body make button in the crafting interface
        // The crafting interface shows different items, we need to find the body option
        Optional<Widget> makeBodyWidget = Widgets.search()
                .withTextContains("Green d'hide body")
                .first();

        if (makeBodyWidget.isPresent()) {
            log("Found make body widget, clicking");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(makeBodyWidget.get(), "Make");
        } else {
            // Fallback: try to find any make widget with "body" text
            Optional<Widget> makeWidget = Widgets.search()
                    .withAction("Make")
                    .first();

            if (makeWidget.isPresent()) {
                log("Found make widget, clicking");
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(makeWidget.get(), "Make");
            } else {
                // Last resort: press spacebar
                log("No make widget found, trying spacebar");
                pressSpace();
            }
        }
    }

    private void pressSpace() {
        KeyEvent keyPress = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyPress);
        KeyEvent keyRelease = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        client.getCanvas().dispatchEvent(keyRelease);
    }
}
