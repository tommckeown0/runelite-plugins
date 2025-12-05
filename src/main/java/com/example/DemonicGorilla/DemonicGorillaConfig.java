package com.example.DemonicGorilla;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("DemonicGorilla")
public interface DemonicGorillaConfig extends Config {

    @ConfigItem(
            keyName = "enablePrayerSwitching",
            name = "Enable Prayer Switching",
            description = "Automatically switch prayers based on demonic gorilla attacks"
    )
    default boolean enablePrayerSwitching() {
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
