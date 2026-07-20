package com.example.GreenDhideBodies;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("GreenDhideBodies")
public interface GreenDhideBodiesConfig extends Config {

    @ConfigItem(
            keyName = "enablePlugin",
            name = "Enable Plugin",
            description = "Enable the plugin"
    )
    default boolean enablePlugin() {
        return true;
    }

    @ConfigItem(
            keyName = "useSpacebarForMake",
            name = "Use Spacebar for Make",
            description = "If enabled, uses spacebar for make-all. If disabled, clicks the make widget directly."
    )
    default boolean useSpacebarForMake() {
        return false;
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
