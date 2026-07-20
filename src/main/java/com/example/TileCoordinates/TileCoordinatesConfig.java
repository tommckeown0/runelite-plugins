package com.example.TileCoordinates;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("TileCoordinates")
public interface TileCoordinatesConfig extends Config {
    @ConfigItem(
            keyName = "showHoveredTile",
            name = "Show hovered tile",
            description = "Outline the tile under the mouse and show its coordinates next to the cursor",
            position = 0
    )
    default boolean showHoveredTile() {
        return true;
    }

    @ConfigItem(
            keyName = "showPlayerTile",
            name = "Show player tile",
            description = "Show the local player's current coordinates on their tile",
            position = 1
    )
    default boolean showPlayerTile() {
        return false;
    }

    @ConfigItem(
            keyName = "showRegion",
            name = "Show region info",
            description = "Include region id and region-local x/y in the coordinate labels",
            position = 2
    )
    default boolean showRegion() {
        return false;
    }

    @ConfigItem(
            keyName = "logHotkey",
            name = "Log/copy hotkey",
            description = "While held/pressed, log the hovered tile to the console and copy it to the clipboard",
            position = 3
    )
    default Keybind logHotkey() {
        return Keybind.NOT_SET;
    }
}
