package com.example.AInventorySetup;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("AInventorySetup")
public interface AInventorySetupConfig extends Config {

    @ConfigItem(
            keyName = "searchDistance",
            name = "Bank Search Distance",
            description = "How far away to look for a bank booth/chest when filling a template"
    )
    default int searchDistance() {
        return 15;
    }

    @ConfigItem(
            keyName = "withdrawTimeoutTicks",
            name = "Withdraw Timeout (ticks)",
            description = "Max ticks to wait for a single slot to be withdrawn before skipping it"
    )
    default int withdrawTimeoutTicks() {
        return 10;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Log state machine steps to console"
    )
    default boolean debugLogging() {
        return false;
    }
}
