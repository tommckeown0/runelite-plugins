package com.example.MotherlodeMine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup("MotherlodeMine")
public interface MotherlodeMineConfig extends Config {

    @ConfigItem(
            keyName = "enableAutoMining",
            name = "Enable Auto-Mining",
            description = "Automatically re-click pay-dirt deposits when player stops mining"
    )
    default boolean enableAutoMining() {
        return false;
    }

    @ConfigItem(
            keyName = "highlightDeposits",
            name = "Highlight Pay-Dirt",
            description = "Highlight nearby pay-dirt deposits"
    )
    default boolean highlightDeposits() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightColor",
            name = "Highlight Color",
            description = "Color for highlighting pay-dirt deposits"
    )
    default Color highlightColor() {
        return Color.CYAN;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable extensive debug logging to console (may impact performance)"
    )
    default boolean debugLogging() {
        return true;
    }

    @ConfigItem(
            keyName = "logCoordinates",
            name = "Log Player Coordinates",
            description = "Continuously log player coordinates (useful for mapping out locations)"
    )
    default boolean logCoordinates() {
        return false;
    }

    @ConfigItem(
            keyName = "dropGems",
            name = "Auto Drop Gems",
            description = "Automatically drop uncut gems (diamond, ruby, emerald, sapphire) while mining"
    )
    default boolean dropGems() {
        return true;
    }

    @ConfigItem(
            keyName = "logAllNearbyObjects",
            name = "Log All Nearby Objects",
            description = "Log all nearby tile objects every 30 ticks (helps identify correct IDs)"
    )
    default boolean logAllNearbyObjects() {
        return false;
    }

    @ConfigItem(
            keyName = "showDistances",
            name = "Show Distances",
            description = "Show distance to deposits in overlay"
    )
    default boolean showDistances() {
        return true;
    }

    @ConfigItem(
            keyName = "stopWhenFull",
            name = "Stop When Inventory Full",
            description = "Stop auto-mining when inventory is full"
    )
    default boolean stopWhenFull() {
        return true;
    }

    // ===== MINING REGION SETTINGS =====

    @ConfigItem(
            keyName = "useMiningRegion",
            name = "Use Mining Region",
            description = "Only mine veins within a defined region (highly recommended to avoid unreachable veins)"
    )
    default boolean useMiningRegion() {
        return false;
    }

    @ConfigItem(
            keyName = "regionMinX",
            name = "Region Min X",
            description = "Minimum X coordinate for mining region (Check logs for 'Current location')"
    )
    default int regionMinX() {
        return 3764;
    }

    @ConfigItem(
            keyName = "regionMaxX",
            name = "Region Max X",
            description = "Maximum X coordinate for mining region (Check logs for 'Current location')"
    )
    default int regionMaxX() {
        return 3769;
    }

    @ConfigItem(
            keyName = "regionMinY",
            name = "Region Min Y",
            description = "Minimum Y coordinate for mining region (Check logs for 'Current location')"
    )
    default int regionMinY() {
        return 5668;
    }

    @ConfigItem(
            keyName = "regionMaxY",
            name = "Region Max Y",
            description = "Maximum Y coordinate for mining region (Check logs for 'Current location')"
    )
    default int regionMaxY() {
        return 5675;
    }

    // ===== HOPPER DEPOSIT SETTINGS =====

    @ConfigItem(
            keyName = "autoDeposit",
            name = "Auto Deposit at Hopper",
            description = "Automatically travel to hopper and deposit pay-dirt when inventory is full"
    )
    default boolean autoDeposit() {
        return false;
    }

    @ConfigItem(
            keyName = "hopperX",
            name = "Hopper X Coordinate",
            description = "X coordinate of the hopper (exact location to click)"
    )
    default int hopperX() {
        return 3748;
    }

    @ConfigItem(
            keyName = "hopperY",
            name = "Hopper Y Coordinate",
            description = "Y coordinate of the hopper (exact location to click)"
    )
    default int hopperY() {
        return 5672;
    }

    @ConfigItem(
            keyName = "sackX",
            name = "Sack X Coordinate",
            description = "X coordinate of the sack (for collecting ore)"
    )
    default int sackX() {
        return 3748;
    }

    @ConfigItem(
            keyName = "sackY",
            name = "Sack Y Coordinate",
            description = "Y coordinate of the sack (for collecting ore)"
    )
    default int sackY() {
        return 5659;
    }

    @ConfigItem(
            keyName = "depositBoxX",
            name = "Deposit Box X Coordinate",
            description = "X coordinate of the deposit box"
    )
    default int depositBoxX() {
        return 3759;
    }

    @ConfigItem(
            keyName = "depositBoxY",
            name = "Deposit Box Y Coordinate",
            description = "Y coordinate of the deposit box"
    )
    default int depositBoxY() {
        return 5664;
    }

    @ConfigItem(
            keyName = "returnToMining",
            name = "Return to Mining After Deposit",
            description = "Automatically return to mining area after depositing"
    )
    default boolean returnToMining() {
        return true;
    }

    // ===== BROKEN STRUT SETTINGS =====

    @ConfigItem(
            keyName = "autoRepairStrut",
            name = "Auto Repair Broken Strut",
            description = "Automatically repair broken water wheel struts when detected"
    )
    default boolean autoRepairStrut() {
        return false;
    }

    @ConfigItem(
            keyName = "topStrutX",
            name = "Top Strut X Coordinate",
            description = "X coordinate of the top broken strut"
    )
    default int topStrutX() {
        return 3742;
    }

    @ConfigItem(
            keyName = "topStrutY",
            name = "Top Strut Y Coordinate",
            description = "Y coordinate of the top broken strut"
    )
    default int topStrutY() {
        return 5669;
    }

    @ConfigItem(
            keyName = "bottomStrutX",
            name = "Bottom Strut X Coordinate",
            description = "X coordinate of the bottom broken strut"
    )
    default int bottomStrutX() {
        return 3742;
    }

    @ConfigItem(
            keyName = "bottomStrutY",
            name = "Bottom Strut Y Coordinate",
            description = "Y coordinate of the bottom broken strut"
    )
    default int bottomStrutY() {
        return 5663;
    }
}
