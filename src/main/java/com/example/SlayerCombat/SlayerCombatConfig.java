package com.example.SlayerCombat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("slayercombat")
public interface SlayerCombatConfig extends Config {

    enum Monster {
        KURASK(410, "Attack");

        public final int npcId;
        public final String attackAction;

        Monster(int npcId, String attackAction) {
            this.npcId = npcId;
            this.attackAction = attackAction;
        }
    }

    @ConfigItem(
        keyName = "enabled",
        name = "Enable Plugin",
        description = "Enable automatic combat"
    )
    default boolean enabled() {
        return false;
    }

    @ConfigItem(
        keyName = "monster",
        name = "Target Monster",
        description = "Monster to fight"
    )
    default Monster monster() {
        return Monster.KURASK;
    }

    @ConfigItem(
        keyName = "enablePrayerPots",
        name = "Auto Prayer Potions",
        description = "Automatically drink prayer potions when prayer points drop below threshold"
    )
    default boolean enablePrayerPots() {
        return false;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "prayerThreshold",
        name = "Prayer Threshold",
        description = "Drink prayer potion when prayer points drop below this value"
    )
    default int prayerThreshold() {
        return 20;
    }

    @ConfigItem(
        keyName = "enableLooting",
        name = "Auto Loot",
        description = "Automatically pick up ground items whose total stack value is over the threshold"
    )
    default boolean enableLooting() {
        return false;
    }

    @ConfigItem(
        keyName = "lootValueThreshold",
        name = "Loot Value Threshold",
        description = "Only pick up a ground item stack worth more than this (GE value of the whole stack)"
    )
    default int lootValueThreshold() {
        return 5000;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
        keyName = "maxLootDistance",
        name = "Max Loot Distance",
        description = "Ignore valuable items further than this many tiles from the player"
    )
    default int maxLootDistance() {
        return 20;
    }

    @ConfigItem(
        keyName = "lootWhitelist",
        name = "Loot Whitelist",
        description = "Always pick these up regardless of value (e.g. untradeables like Crystal shard). "
                + "Comma-separated; each entry is an item name (case-insensitive) or a numeric item ID. "
                + "e.g. Crystal shard, 23956"
    )
    default String lootWhitelist() {
        return "";
    }

    @ConfigItem(
        keyName = "lootBlacklist",
        name = "Loot Blacklist",
        description = "Never pick these up, even if over the value threshold. Same format as the whitelist "
                + "(comma-separated item names or numeric IDs). Takes priority over the whitelist."
    )
    default String lootBlacklist() {
        return "";
    }

    @ConfigItem(
        keyName = "debugLogging",
        name = "Debug Logging",
        description = "Log debug info to console"
    )
    default boolean debugLogging() {
        return false;
    }
}
