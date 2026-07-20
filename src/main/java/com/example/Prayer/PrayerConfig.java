package com.example.Prayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("PrayerTraining")
public interface PrayerConfig extends Config {

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable extensive debug logging to console"
    )
    default boolean debugLogging() {
        return true;
    }

    @ConfigItem(
            keyName = "searchDistance",
            name = "Search Distance",
            description = "Maximum tile distance to search for objects/NPCs"
    )
    default int searchDistance() {
        return 15;
    }

    @ConfigItem(
            keyName = "stateTimeout",
            name = "State Timeout (ticks)",
            description = "Maximum ticks to wait in a state before timeout error"
    )
    default int stateTimeout() {
        return 100;
    }
}
