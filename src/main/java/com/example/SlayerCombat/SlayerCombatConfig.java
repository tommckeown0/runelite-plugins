package com.example.SlayerCombat;

import net.runelite.api.Prayer;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("slayercombat")
public interface SlayerCombatConfig extends Config {

    enum Monster {
        // Not cannoned, so the cannon tiles are unused (0,0). Protect from melee.
        KURASK(new int[]{410}, "Attack", false, 0, 0, 0, 0, Prayer.PROTECT_FROM_MELEE),
        // Greater demon — not cannoned, same pattern as Kurask. Protect from melee. Ids by level:
        //   92  -> 2025-2032 (+ Catacombs of Kourend 5872-5875)
        //   100 -> 7245, 101 -> 7244, 113 -> 7246
        //   104 (Wilderness Slayer Cave) -> 7871, 7872, 7873
        GREATER_DEMON(new int[]{
                2025, 2026, 2027, 2028, 2029, 2030, 2031, 2032,
                5872, 5873, 5874, 5875,
                7244, 7245, 7246,
                7871, 7872, 7873
        }, "Attack", false, 0, 0, 0, 0, Prayer.PROTECT_FROM_MELEE),
        // Mountain troll (lvl 69) has several ids; lvl 71 is 4143. Killed with a cannon,
        // so cannonMode = true (no manual attacking — auto-retaliate does the fighting).
        // Cannon setup/fight tiles below are the Mountain troll spot. Protect from melee.
        MOUNTAIN_TROLL(new int[]{936, 937, 938, 939, 940, 941, 942, 4143}, "Attack", true,
                1242, 3517, 1241, 3518, Prayer.PROTECT_FROM_MELEE);

        public final int[] npcIds;
        public final String attackAction;
        public final boolean cannonMode;
        // Tile to stand on to set up the cannon, and the tile to stand on while it aggros monsters.
        // Only meaningful when cannonMode is true.
        public final int cannonSetupX;
        public final int cannonSetupY;
        public final int cannonFightX;
        public final int cannonFightY;
        // Protection prayer to keep up against this monster (e.g. PROTECT_FROM_MELEE). null = none.
        // Only used when the "Auto Protection Prayer" config toggle is on.
        public final Prayer protectionPrayer;

        Monster(int[] npcIds, String attackAction, boolean cannonMode,
                int cannonSetupX, int cannonSetupY, int cannonFightX, int cannonFightY,
                Prayer protectionPrayer) {
            this.npcIds = npcIds;
            this.attackAction = attackAction;
            this.cannonMode = cannonMode;
            this.cannonSetupX = cannonSetupX;
            this.cannonSetupY = cannonSetupY;
            this.cannonFightX = cannonFightX;
            this.cannonFightY = cannonFightY;
            this.protectionPrayer = protectionPrayer;
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

    @ConfigSection(
        name = "Prayer & Potions",
        description = "Auto protection prayer and prayer potions",
        position = 1
    )
    String prayerSection = "prayerSection";

    @ConfigSection(
        name = "Looting",
        description = "Auto-loot rules",
        position = 2
    )
    String lootingSection = "lootingSection";

    @ConfigSection(
        name = "Cannon",
        description = "Dwarf multicannon mode and refilling",
        position = 3
    )
    String cannonSection = "cannonSection";

    @ConfigItem(
        keyName = "monster",
        name = "Target Monster",
        description = "Monster to fight"
    )
    default Monster monster() {
        return Monster.KURASK;
    }

    @ConfigItem(
        keyName = "enableProtectionPrayer",
        name = "Auto Protection Prayer",
        description = "Keep the selected monster's protection prayer active (e.g. Protect from Melee for "
                + "Kurask/Greater demon/Mountain troll). Requires the prayer to be unlocked and prayer points.",
        section = prayerSection
    )
    default boolean enableProtectionPrayer() {
        return false;
    }

    @ConfigItem(
        keyName = "enablePrayerPots",
        name = "Auto Prayer Potions",
        description = "Automatically drink prayer potions when prayer points drop below threshold",
        section = prayerSection
    )
    default boolean enablePrayerPots() {
        return false;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "prayerThreshold",
        name = "Prayer Threshold",
        description = "Drink prayer potion when prayer points drop below this value",
        section = prayerSection
    )
    default int prayerThreshold() {
        return 20;
    }

    @ConfigItem(
        keyName = "enableLooting",
        name = "Auto Loot",
        description = "Automatically pick up ground items whose total stack value is over the threshold",
        section = lootingSection
    )
    default boolean enableLooting() {
        return false;
    }

    @ConfigItem(
        keyName = "lootValueThreshold",
        name = "Loot Value Threshold",
        description = "Only pick up a ground item stack worth more than this (GE value of the whole stack)",
        section = lootingSection
    )
    default int lootValueThreshold() {
        return 5000;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
        keyName = "maxLootDistance",
        name = "Max Loot Distance",
        description = "Ignore valuable items further than this many tiles from the player",
        section = lootingSection
    )
    default int maxLootDistance() {
        return 20;
    }

    @ConfigItem(
        keyName = "lootWhitelist",
        name = "Loot Whitelist",
        description = "Always pick these up regardless of value (e.g. untradeables like Crystal shard). "
                + "Comma-separated; each entry is an item name (case-insensitive) or a numeric item ID. "
                + "e.g. Crystal shard, 23956",
        section = lootingSection
    )
    default String lootWhitelist() {
        return "";
    }

    @ConfigItem(
        keyName = "lootBlacklist",
        name = "Loot Blacklist",
        description = "Never pick these up, even if over the value threshold. Same format as the whitelist "
                + "(comma-separated item names or numeric IDs). Takes priority over the whitelist.",
        section = lootingSection
    )
    default String lootBlacklist() {
        return "";
    }

    @ConfigItem(
        keyName = "cannonControl",
        name = "Cannon Mode",
        description = "AUTO follows the selected monster (Mountain troll = on, Kurask = off). "
                + "ON/OFF force cannon mode regardless of monster.",
        section = cannonSection
    )
    default CannonControl cannonControl() {
        return CannonControl.AUTO;
    }

    @Range(min = 0, max = 60)
    @ConfigItem(
        keyName = "cannonballThreshold",
        name = "Refill Below",
        description = "Click 'Fire' on the cannon to refill when the loaded cannonball count drops below this "
                + "(read from the built-in Cannon plugin, which must be enabled).",
        section = cannonSection
    )
    default int cannonballThreshold() {
        return 10;
    }

    @Range(min = 0, max = 120)
    @ConfigItem(
        keyName = "cannonStallSeconds",
        name = "Restart If Idle (s)",
        description = "A cannon stops firing on its own after ~30 min and must be re-Fired. If the loaded "
                + "count hasn't dropped for this many seconds while a target is in range, click 'Fire' to "
                + "restart it. 0 = disabled.",
        section = cannonSection
    )
    default int cannonStallSeconds() {
        return 12;
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
