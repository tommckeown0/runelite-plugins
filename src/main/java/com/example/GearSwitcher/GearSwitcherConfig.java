package com.example.GearSwitcher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

import java.awt.event.KeyEvent;

@ConfigGroup("GearSwitcher")
public interface GearSwitcherConfig extends Config {

    @ConfigItem(
            keyName = "magicSetupHotkey",
            name = "Magic Setup Hotkey",
            description = "Press this key to equip magic gear and activate Mystic Might"
    )
    default Keybind magicSetupHotkey() {
        return new Keybind(KeyEvent.VK_F8, 0);
    }

    @ConfigItem(
            keyName = "rangedSetupHotkey",
            name = "Ranged Setup Hotkey",
            description = "Press this key to equip ranged gear and activate Eagle Eye"
    )
    default Keybind rangedSetupHotkey() {
        return new Keybind(KeyEvent.VK_F9, 0);
    }

    @ConfigItem(
            keyName = "meleeSetupHotkey",
            name = "Melee Setup Hotkey",
            description = "Press this key to equip melee gear and activate Piety"
    )
    default Keybind meleeSetupHotkey() {
        return new Keybind(KeyEvent.VK_F10, 0);
    }

    @ConfigItem(
            keyName = "magicGear",
            name = "Magic Gear (Item IDs)",
            description = "Comma-separated item IDs for magic setup (e.g., 4862,4874,4868)"
    )
    default String magicGear() {
        return "4862,4874,4868"; // Ahrim's staff, skirt, top
    }

    @ConfigItem(
            keyName = "rangedGear",
            name = "Ranged Gear (Item IDs)",
            description = "Comma-separated item IDs for ranged setup (e.g., 12926,22109,10378,10380)"
    )
    default String rangedGear() {
        return "12926,22109,10378,10380"; // Blowpipe, Ava's, Guthix body/chaps
    }

    @ConfigItem(
            keyName = "meleeGear",
            name = "Melee Gear (Item IDs)",
            description = "Comma-separated item IDs for melee setup (e.g., 4151,6570,12954,10378,10380)"
    )
    default String meleeGear() {
        return "4151,6570,12954,10378,10380"; // Whip, Fire cape, D defender, Guthix body/chaps
    }

    @ConfigItem(
            keyName = "enablePrayers",
            name = "Enable Prayers",
            description = "Automatically enable offensive prayers when switching gear"
    )
    default boolean enablePrayers() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable debug logging to console (may impact performance)"
    )
    default boolean debugLogging() {
        return false;
    }
}
