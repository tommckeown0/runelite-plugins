package com.example.NewTrees;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("YewTrees")
public interface NewTreesConfig extends Config {

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable extensive debug logging to console"
    )
    default boolean debugLogging() {
        return true;
    }

    @ConfigItem(
            keyName = "treeDistance",
            name = "Tree Distance",
            description = "Maximum tile distance to search for trees"
    )
    default int treeDistance(){
        return 15;
    }
}