package com.example.GearSwitcher;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.InteractionApi.PrayerInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
        name = "A Gear Switcher",
        description = "Dynamically highlight items depending on which attack style they're for",
        tags = {"gear", "combat", "pvm"},
        hidden = false
)
public class GearSwitcherPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private KeyManager keyManager;

    @Inject
    private GearSwitcherConfig config;

    private HotkeyListener magicSetupListener;
    private HotkeyListener rangedSetupListener;
    private HotkeyListener meleeSetupListener;

    @Provides
    GearSwitcherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GearSwitcherConfig.class);
    }

    /**
     * Conditional logging helper - only logs when debug logging is enabled in config
     */
    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[GearSwitcher] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("Starting up Gear Switcher plugin");

        // F8 - Magic Setup
        magicSetupListener = new HotkeyListener(() -> config.magicSetupHotkey()) {
            @Override
            public void hotkeyPressed() {
                log("Magic setup hotkey pressed");
                clientThread.invoke(() -> {
                    try {
                        equipMagicSetup();
                    } catch (Exception e) {
                        log("Error in equipMagicSetup: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        };

        // F9 - Ranged Setup
        rangedSetupListener = new HotkeyListener(() -> config.rangedSetupHotkey()) {
            @Override
            public void hotkeyPressed() {
                log("Ranged setup hotkey pressed");
                clientThread.invoke(() -> {
                    try {
                        equipRangedSetup();
                    } catch (Exception e) {
                        log("Error in equipRangedSetup: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        };

        // F10 - Melee Setup
        meleeSetupListener = new HotkeyListener(() -> config.meleeSetupHotkey()) {
            @Override
            public void hotkeyPressed() {
                log("Melee setup hotkey pressed");
                clientThread.invoke(() -> {
                    try {
                        equipMeleeSetup();
                    } catch (Exception e) {
                        log("Error in equipMeleeSetup: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        };

        keyManager.registerKeyListener(magicSetupListener);
        keyManager.registerKeyListener(rangedSetupListener);
        keyManager.registerKeyListener(meleeSetupListener);
        log("Gear Switcher plugin started successfully");
    }

    /**
     * Parses a comma-separated string of item IDs into an int array
     */
    private int[] parseItemIds(String itemIdsString) {
        if (itemIdsString == null || itemIdsString.trim().isEmpty()) {
            return new int[0];
        }

        String[] parts = itemIdsString.split(",");
        int[] itemIds = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            try {
                itemIds[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                log("Invalid item ID: " + parts[i]);
                itemIds[i] = -1; // Skip invalid IDs
            }
        }

        return itemIds;
    }

    /**
     * Generic method to equip a loadout of items and optionally enable a prayer.
     * @param itemIds Array of item IDs to equip
     * @param prayer Prayer to enable (can be null)
     */
    private void equipLoadout(int[] itemIds, Prayer prayer) {
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            log("Not logged in");
            return;
        }

        // Equip each item in the loadout
        for (int itemId : itemIds) {
            if (itemId <= 0) continue; // Skip invalid IDs

            log("Searching for item " + itemId + "...");
            Widget item = Inventory.search().withId(itemId).first().orElse(null);

            if (item != null) {
                log("Item " + itemId + " found! Name: " + item.getName());
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(item, "Wear", "Wield", "Equip");
            } else {
                log("Item " + itemId + " not found in inventory");
            }
        }

        // Enable prayer if specified and prayer switching is enabled
        if (config.enablePrayers() && prayer != null) {
            log("Enabling " + prayer + " prayer");
            PrayerInteraction.setPrayerState(prayer, true);
        }

        log("Loadout equipped!");
    }

    private void equipMagicSetup() {
        int[] magicGear = parseItemIds(config.magicGear());
        equipLoadout(magicGear, Prayer.MYSTIC_MIGHT);
    }

    private void equipRangedSetup() {
        int[] rangedGear = parseItemIds(config.rangedGear());
        equipLoadout(rangedGear, Prayer.EAGLE_EYE);
    }

    private void equipMeleeSetup() {
        int[] meleeGear = parseItemIds(config.meleeGear());
        equipLoadout(meleeGear, Prayer.PIETY);
    }

    @Override
    protected void shutDown() {
        log("Shutting down Gear Switcher plugin");

        if (magicSetupListener != null) {
            keyManager.unregisterKeyListener(magicSetupListener);
        }
        if (rangedSetupListener != null) {
            keyManager.unregisterKeyListener(rangedSetupListener);
        }
        if (meleeSetupListener != null) {
            keyManager.unregisterKeyListener(meleeSetupListener);
        }
    }
}
