package com.example.AImpKillerGWD;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("aimpkillergwd")
public interface AImpKillerGWDConfig extends Config {

    @Range(min = 0, max = 200)
    @ConfigItem(
            keyName = "targetKillCount",
            name = "Target Kill Count",
            description = "Stop attacking after this many kills. 35 = Zamorak GWD room entry. 0 = run indefinitely."
    )
    default int targetKillCount() {
        return 35;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Print debug messages to the console."
    )
    default boolean debugLogging() {
        return false;
    }
}
