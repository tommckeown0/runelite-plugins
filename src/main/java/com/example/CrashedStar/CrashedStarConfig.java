package com.example.CrashedStar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("CrashedStar")
public interface CrashedStarConfig extends Config {

    @ConfigItem(
            keyName = "enableAutoMining",
            name = "Enable Auto-Mining",
            description = "Automatically re-click the crashed star when it cracks"
    )
    default boolean enableAutoMining() {
        return true;
    }

    @ConfigItem(
            keyName = "dropSapphires",
            name = "Drop Sapphires When Full",
            description = "Automatically drop sapphires when inventory is full"
    )
    default boolean dropSapphires() {
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
