package com.example.Testing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration for Widget Discovery Tool
 */
@ConfigGroup("WidgetDiscovery")
public interface TestingPluginConfig extends Config {

    @ConfigItem(
        keyName = "scanWidgets",
        name = "Scan Widgets",
        description = "Enable to scan and log all visible widgets. Toggle off to stop logging."
    )
    default boolean scanWidgets() {
        return false;
    }

    @ConfigItem(
        keyName = "logAllWidgets",
        name = "Log All Widgets",
        description = "Log all widget groups, not just ones with items (generates a LOT of output)"
    )
    default boolean logAllWidgets() {
        return false;
    }

    @ConfigItem(
        keyName = "maxWidgetsToLog",
        name = "Max Widgets Per Group",
        description = "Maximum number of child widgets to log per group (0 = unlimited)"
    )
    default int maxWidgetsToLog() {
        return 20;
    }

    @ConfigItem(
        keyName = "logWidgetPosition",
        name = "Log Widget Position",
        description = "Include position and size information for each widget"
    )
    default boolean logWidgetPosition() {
        return false;
    }

    @ConfigItem(
        keyName = "logNestedWidgets",
        name = "Log Nested Widgets",
        description = "Log deeply nested child widgets (can generate a lot of output)"
    )
    default boolean logNestedWidgets() {
        return false;
    }
}
