package com.example.DemonicGorilla;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("DemonicGorilla")
public interface DemonicGorillaConfig extends Config {

    @ConfigItem(
            keyName = "enablePrayerSwitching",
            name = "Enable Prayer Switching",
            description = "Automatically switch protection prayer based on the gorilla's melee/ranged/magic attack animation"
    )
    default boolean enablePrayerSwitching() {
        return true;
    }

    @ConfigItem(
            keyName = "enableStyleMismatchAlert",
            name = "Enable Attack Style Mismatch Alert",
            description = "Chat warning when the gorilla's own protection prayer is blocking your current attack style"
    )
    default boolean enableStyleMismatchAlert() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoGearSwitch",
            name = "Enable Auto Gear Switch",
            description = "Automatically switch to A Gear Switcher's melee/ranged loadout when the gorilla prays against your current attack style"
    )
    default boolean enableAutoGearSwitch() {
        return false;
    }

    @ConfigItem(
            keyName = "enableBoulderWarning",
            name = "Enable Boulder Warning",
            description = "Chat message and overlay tile when the gorilla beats its chest to drop a boulder on your position"
    )
    default boolean enableBoulderWarning() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoDodgeBoulder",
            name = "Enable Auto Dodge Boulder",
            description = "Automatically step off the danger tile when the gorilla drops a boulder"
    )
    default boolean enableAutoDodgeBoulder() {
        return false;
    }

    @ConfigItem(
            keyName = "enableAutoReattackAfterBoulder",
            name = "Enable Auto Re-attack After Boulder",
            description = "Automatically re-attack the gorilla once the boulder has resolved"
    )
    default boolean enableAutoReattackAfterBoulder() {
        return false;
    }

    @ConfigItem(
            keyName = "enableAutoDrinkPrayerPotion",
            name = "Enable Auto Drink Prayer Potion",
            description = "Automatically drink a prayer potion when prayer points fall below the threshold"
    )
    default boolean enableAutoDrinkPrayerPotion() {
        return false;
    }

    @ConfigItem(
            keyName = "prayerPotionThreshold",
            name = "Prayer Potion Threshold",
            description = "Drink a prayer potion when boosted prayer falls below this value"
    )
    default int prayerPotionThreshold() {
        return 20;
    }

    @ConfigItem(
            keyName = "enableAutoEatFood",
            name = "Enable Auto Eat Food",
            description = "Automatically eat food when hitpoints fall below the threshold"
    )
    default boolean enableAutoEatFood() {
        return false;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Food Name",
            description = "Comma-separated food to eat, tried in order - each entry is either a wildcard-matched name (e.g. 'shark') or an item ID (e.g. '11936'). Example: 'shark,Dark crab'"
    )
    default String foodName() {
        return "shark";
    }

    @ConfigItem(
            keyName = "combatEatThreshold",
            name = "Combat Eat Threshold",
            description = "Eat when hitpoints fall below this value while fighting a gorilla"
    )
    default int combatEatThreshold() {
        return 30;
    }

    @ConfigItem(
            keyName = "outOfCombatEatThreshold",
            name = "Out of Combat Eat Threshold",
            description = "Eat when hitpoints fall below this value while not fighting a gorilla"
    )
    default int outOfCombatEatThreshold() {
        return 20;
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
