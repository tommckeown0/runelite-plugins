package com.example.ATormentedDemon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ATormentedDemon")
public interface ATormentedDemonConfig extends Config {

    @ConfigItem(
            keyName = "enablePrayerFlicking",
            name = "Enable Prayer Flicking",
            description = "Instantly switch protection prayer based on the demon's incoming attack animation",
            position = 0
    )
    default boolean enablePrayerFlicking() {
        return true;
    }

    @ConfigItem(
            keyName = "enableStyleMismatchAlert",
            name = "Enable Style Mismatch Alert",
            description = "Warn in the game chat when the demon's own protection prayer is blocking your current attack style",
            position = 1
    )
    default boolean enableStyleMismatchAlert() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFireBombWarning",
            name = "Enable Fire Bomb Warning",
            description = "Alert in the game chat and highlight the two landing tiles when a fire bomb is incoming",
            position = 2
    )
    default boolean enableFireBombWarning() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoDodgeFireBomb",
            name = "Enable Auto Dodge Fire Bomb",
            description = "Automatically walk off a marked fire bomb tile onto a safe one",
            position = 3
    )
    default boolean enableAutoDodgeFireBomb() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoReattackAfterFireBomb",
            name = "Enable Auto Reattack After Fire Bomb",
            description = "Automatically re-attack the demon once a fire bomb has landed and resolved",
            position = 4
    )
    default boolean enableAutoReattackAfterFireBomb() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoGearSwitch",
            name = "Enable Auto Gear Switch",
            description = "Automatically equip A Gear Switcher's melee/ranged loadout and offensive prayer when the demon's protection prayer blocks your current style",
            position = 5
    )
    default boolean enableAutoGearSwitch() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoDrinkPrayerPotion",
            name = "Enable Auto Drink Prayer Potion",
            description = "Automatically drink a prayer potion when prayer points drop below the threshold",
            position = 6
    )
    default boolean enableAutoDrinkPrayerPotion() {
        return true;
    }

    @ConfigItem(
            keyName = "prayerPotionThreshold",
            name = "Prayer Potion Threshold",
            description = "Drink a prayer potion when prayer points fall below this value",
            position = 7
    )
    default int prayerPotionThreshold() {
        return 15;
    }

    @ConfigItem(
            keyName = "enableAutoEatDarkCrab",
            name = "Enable Auto Eat Dark Crab",
            description = "Automatically eat dark crabs (11936) when HP drops below the relevant threshold",
            position = 8
    )
    default boolean enableAutoEatDarkCrab() {
        return true;
    }

    @ConfigItem(
            keyName = "combatEatThreshold",
            name = "Combat Eat Threshold",
            description = "Eat when HP falls below this value while a tormented demon is engaged",
            position = 9
    )
    default int combatEatThreshold() {
        return 40;
    }

    @ConfigItem(
            keyName = "outOfCombatEatThreshold",
            name = "Out Of Combat Eat Threshold",
            description = "Eat (possibly multiple crabs) when HP falls below this value while no demon is engaged",
            position = 10
    )
    default int outOfCombatEatThreshold() {
        return 74;
    }

    @ConfigItem(
            keyName = "enableAutoSummonThrall",
            name = "Enable Auto Summon Thrall",
            description = "Cast Resurrect Greater Ghost whenever no greater ghost thrall is nearby",
            position = 11
    )
    default boolean enableAutoSummonThrall() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable debug logging to console (may impact performance)",
            position = 12
    )
    default boolean debugLogging() {
        return false;
    }
}
