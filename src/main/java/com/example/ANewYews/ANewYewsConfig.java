package com.example.ANewYews;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ANewYews")
public interface ANewYewsConfig extends Config {

    @ConfigItem(
            keyName = "enablePlugin",
            name = "Enable Plugin",
            description = "Enable the plugin"
    )
    default boolean enablePlugin() {
        return true;
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
