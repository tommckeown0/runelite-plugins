package com.example.Hunllef;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Hunllef")
public interface HunllefConfig extends Config {

    @ConfigItem(
            keyName = "enablePrayerSwitching",
            name = "Enable Prayer Switching",
            description = "Automatically switch prayers based on Hunllef attack count"
    )
    default boolean enablePrayerSwitching() {
        return true;
    }

    @ConfigItem(
            keyName = "enablePrayerRestore",
            name = "Restore Disabled Prayers",
            description = "Automatically re-enable protection prayers if they get turned off"
    )
    default boolean enablePrayerRestore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeaponSwitching",
            name = "Enable Weapon Switching",
            description = "Automatically switch weapons based on Hunllef's overhead prayer"
    )
    default boolean enableWeaponSwitching() {
        return true;
    }

    @ConfigItem(
            keyName = "enableOffensivePrayers",
            name = "Enable Offensive Prayers",
            description = "Automatically switch offensive prayers based on equipped weapon"
    )
    default boolean enableOffensivePrayers() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable debug logging to console"
    )
    default boolean debugLogging() {
        return false;
    }
}
