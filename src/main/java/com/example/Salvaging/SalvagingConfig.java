package com.example.Salvaging;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Simple configuration for Salvaging plugin
 */
@ConfigGroup("Salvaging")
public interface SalvagingConfig extends Config {

    enum Mode {
        SALVAGING,
        SORTING
    }

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "SALVAGING: Auto-click hooks and deposit when full. SORTING: Click sorting table and drop junk (you manually withdraw from cargo)."
    )
    default Mode mode() {
        return Mode.SALVAGING;
    }

    @ConfigItem(
        keyName = "enablePlugin",
        name = "Enable Plugin",
        description = "Enable automatic actions"
    )
    default boolean enablePlugin() {
        return false;
    }

    @ConfigItem(
        keyName = "itemIDsToDrop",
        name = "Junk Item IDs",
        description = "Comma-separated item IDs to drop after sorting"
    )
    default String itemIDsToDrop() {
        return "1643,31914,1635,11092,413,1639,1637,31973,11085,1329,28896,405,11076,31912,31910";
    }

    @ConfigItem(
        keyName = "debugLogging",
        name = "Debug Logging",
        description = "Enable debug logging to console"
    )
    default boolean debugLogging() {
        return false;
    }

    @ConfigItem(
        keyName = "logAnimations",
        name = "Log All Animations",
        description = "Log all player animations (to discover sorting animation IDs)"
    )
    default boolean logAnimations() {
        return false;
    }
}
