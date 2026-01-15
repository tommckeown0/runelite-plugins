package com.example.SalamanderHunting;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("SalamanderHunting")
public interface SalamanderHuntingConfig extends Config {

    @ConfigItem(
            keyName = "releaseThreshold",
            name = "Release Threshold",
            description = "Release salamanders when this many are in inventory (0 = only when full)"
    )
    @Range(min = 0, max = 28)
    default int releaseThreshold() {
        return 0;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable debug logging to console"
    )
    default boolean debugLogging() {
        return true;
    }
}
