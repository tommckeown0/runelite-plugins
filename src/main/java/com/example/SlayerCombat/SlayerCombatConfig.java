package com.example.SlayerCombat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("slayercombat")
public interface SlayerCombatConfig extends Config {

    enum Monster {
        KURASK(new int[]{410}, "Attack", false),
        // Mountain troll (lvl 69) has several ids; lvl 71 is 4143. Killed with a cannon,
        // so cannonMode = true (no manual attacking — auto-retaliate does the fighting).
        MOUNTAIN_TROLL(new int[]{936, 937, 938, 939, 940, 941, 942, 4143}, "Attack", true);

        public final int[] npcIds;
        public final String attackAction;
        public final boolean cannonMode;

        Monster(int[] npcIds, String attackAction, boolean cannonMode) {
            this.npcIds = npcIds;
            this.attackAction = attackAction;
            this.cannonMode = cannonMode;
        }

        public java.util.List<Integer> npcIdList() {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            for (int id : npcIds) {
                ids.add(id);
            }
            return ids;
        }

        public boolean matchesId(int id) {
            for (int n : npcIds) {
                if (n == id) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * How to decide whether cannon mode is active. AUTO follows the selected monster's database
     * flag (e.g. Mountain troll = on, Kurask = off); ON/OFF force it regardless.
     */
    enum CannonControl {
        AUTO,
        ON,
        OFF
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

    // ---- Cannon mode ----

    @ConfigItem(
        keyName = "cannonControl",
        name = "Cannon Mode",
        description = "AUTO follows the selected monster (Mountain troll = on, Kurask = off). "
                + "ON/OFF force cannon mode regardless of monster."
    )
    default CannonControl cannonControl() {
        return CannonControl.AUTO;
    }

    @ConfigItem(
        keyName = "cannonSetupX",
        name = "Cannon Setup X",
        description = "X tile to stand on to set up the cannon (cannon mode). Default is the Mountain troll spot."
    )
    default int cannonSetupX() {
        return 1241;
    }

    @ConfigItem(
        keyName = "cannonSetupY",
        name = "Cannon Setup Y",
        description = "Y tile to stand on to set up the cannon (cannon mode)."
    )
    default int cannonSetupY() {
        return 3517;
    }

    @ConfigItem(
        keyName = "cannonFightX",
        name = "Fight Tile X",
        description = "X tile to stand on while the cannon aggros monsters to you (cannon mode)."
    )
    default int cannonFightX() {
        return 1241;
    }

    @ConfigItem(
        keyName = "cannonFightY",
        name = "Fight Tile Y",
        description = "Y tile to stand on while the cannon aggros monsters to you (cannon mode)."
    )
    default int cannonFightY() {
        return 3518;
    }

    @Range(min = 0, max = 60)
    @ConfigItem(
        keyName = "cannonballThreshold",
        name = "Refill Below",
        description = "Click 'Fire' on the cannon to refill when the loaded cannonball count drops below this "
                + "(read from the built-in Cannon plugin, which must be enabled)."
    )
    default int cannonballThreshold() {
        return 10;
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
