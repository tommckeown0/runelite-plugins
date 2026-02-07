package com.example.BarbarianFishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup("BarbarianFishing")
public interface BarbarianFishingConfig extends Config {

    @ConfigItem(
            keyName = "enableAutoFishing",
            name = "Enable Auto-Fishing",
            description = "Automatically re-click fishing spots when player stops fishing"
    )
    default boolean enableAutoFishing() {
        return true;
    }

    @ConfigItem(
            keyName = "autoDrop",
            name = "Auto Drop Fish",
            description = "Automatically drop fish when inventory is full"
    )
    default boolean autoDrop() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightSpots",
            name = "Highlight Fishing Spots",
            description = "Highlight nearby fishing spots"
    )
    default boolean highlightSpots() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightColor",
            name = "Highlight Color",
            description = "Color for highlighting fishing spots"
    )
    default Color highlightColor() {
        return Color.CYAN;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable extensive debug logging to console"
    )
    default boolean debugLogging() {
        return true;
    }

    @ConfigItem(
            keyName = "fishingSpotSearchRadius",
            name = "Fishing Spot Search Radius",
            description = "Maximum distance to search for fishing spots (tiles)"
    )
    default int fishingSpotSearchRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "dropLeapingTrout",
            name = "Drop Leaping Trout",
            description = "Drop leaping trout when dropping fish"
    )
    default boolean dropLeapingTrout() {
        return true;
    }

    @ConfigItem(
            keyName = "dropLeapingSalmon",
            name = "Drop Leaping Salmon",
            description = "Drop leaping salmon when dropping fish"
    )
    default boolean dropLeapingSalmon() {
        return true;
    }

    @ConfigItem(
            keyName = "dropLeapingSturgeon",
            name = "Drop Leaping Sturgeon",
            description = "Drop leaping sturgeon when dropping fish"
    )
    default boolean dropLeapingSturgeon() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCameraMovement",
            name = "Enable Camera Movement",
            description = "Automatically adjust camera for better view and realism"
    )
    default boolean enableCameraMovement() {
        return true;
    }

    @ConfigItem(
            keyName = "randomCameraAdjustments",
            name = "Random Camera Adjustments",
            description = "Periodically make random camera adjustments (looks more human)"
    )
    default boolean randomCameraAdjustments() {
        return true;
    }

    @ConfigItem(
            keyName = "cameraAdjustmentInterval",
            name = "Camera Adjustment Interval (seconds)",
            description = "How often to make random camera adjustments (30-120 seconds recommended)"
    )
    default int cameraAdjustmentInterval() {
        return 60;
    }
}
