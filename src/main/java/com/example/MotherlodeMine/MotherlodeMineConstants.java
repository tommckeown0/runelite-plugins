package com.example.MotherlodeMine;

/**
 * Constants for the Motherlode Mine plugin
 * Contains all object IDs, item IDs, and animation IDs
 */
public class MotherlodeMineConstants {

    // Pay-dirt ore vein IDs - Lower level (main area)
    public static final int[] PAYDIRT_VEIN_IDS = {
            26661, // Ore vein (active)
            26662, // Ore vein (active)
            26663, // Ore vein (active)
            26664, // Ore vein (active)
    };

    // Dark tunnel IDs (for traveling to hopper)
    public static final int[] DARK_TUNNEL_IDS = {
            10047 // Dark tunnel (common)
            // Add more as discovered
    };

    // Hopper IDs (for depositing pay-dirt)
    public static final int[] HOPPER_IDS = {
            26674 // Hopper
            // Add more as discovered
    };

    // Sack IDs (for collecting ore after hopper is full)
    public static final int[] SACK_IDS = {
            26688, // Sack (full of ores)
            26689  // Sack variant
            // Add more as discovered
    };

    // Deposit box IDs (for depositing ores)
    public static final int[] DEPOSIT_BOX_IDS = {
            10529, // Bank deposit box
            25937, // Bank deposit box (common)
            26969, // Deposit box variant
            29103, // Bank deposit box variant
            29104  // Bank deposit box variant
            // Add more as discovered
    };

    // Broken strut IDs (water wheel malfunction - needs repair)
    // When broken, ore deposited in hopper doesn't move to sack
    // Located ~6 tiles left of hopper/sack, same Y coordinate range
    public static final int[] BROKEN_STRUT_IDS = {
            26670  // Broken strut (two of them appear)
    };

    // Item IDs for ores and nuggets
    public static final int HAMMER_ID = 2347;
    public static final int GOLD_ORE_ID = 444;
    public static final int MITHRIL_ORE_ID = 447;
    public static final int ADAMANTITE_ORE_ID = 449;
    public static final int COAL_ID = 453;
    public static final int GOLD_NUGGET_ID = 12012;
    public static final int PAY_DIRT_ID = 12011;

    // Gem IDs (random drops while mining - take up inventory slots)
    public static final int UNCUT_DIAMOND_ID = 1617;
    public static final int UNCUT_RUBY_ID = 1619;
    public static final int UNCUT_EMERALD_ID = 1621;
    public static final int UNCUT_SAPPHIRE_ID = 1623;

    // Mining animation IDs
    public static final int[] MINING_ANIMATIONS = {
            6758,  // Motherlode Mine specific mining animation
            6752,  // Dragon pickaxe
            8347,  // 3rd age pickaxe
            4482,  // Rune pickaxe
            626,   // Generic mining
            628,   // Another mining animation
            7282,  // Crystal pickaxe
            8346,  // Infernal pickaxe
            4481,  // Adamant pickaxe
            4480   // Mithril pickaxe
    };

    // State machine constants
    public static final int IDLE_TICKS_BEFORE_RECLICK = 3;
    public static final int MAX_DEPOSIT_ATTEMPTS = 50;
    public static final int DEPOSITS_BEFORE_SACK = 4;

    // Variable interaction cooldown (human-like behavior)
    public static final int MIN_INTERACTION_COOLDOWN = 1;  // Minimum ticks between interactions
    public static final int MAX_INTERACTION_COOLDOWN = 3;  // Maximum ticks between interactions
    public static final int INTERACTION_COOLDOWN = 2;      // General interaction cooldown for state transitions

    // Random delay bounds for various actions (human-like behavior)
    public static final int MIN_VEIN_DEPLETION_DELAY = 2;   // Minimum ticks after vein depletes
    public static final int MAX_VEIN_DEPLETION_DELAY = 40;  // Maximum ticks after vein depletes

    public static final int MIN_INVENTORY_FULL_DELAY = 1;   // Minimum ticks to react to full inventory
    public static final int MAX_INVENTORY_FULL_DELAY = 5;   // Maximum ticks to react to full inventory

    public static final int MIN_POST_TUNNEL_DELAY = 3;      // Minimum ticks after exiting tunnel before mining
    public static final int MAX_POST_TUNNEL_DELAY = 15;     // Maximum ticks after exiting tunnel before mining

    public static final int MIN_GEM_DROP_DELAY = 0;         // Minimum ticks before dropping a gem
    public static final int MAX_GEM_DROP_DELAY = 10;        // Maximum ticks before dropping a gem

    public static final int MIN_POST_SACK_DELAY = 2;        // Minimum ticks after emptying sack
    public static final int MAX_POST_SACK_DELAY = 20;       // Maximum ticks after emptying sack

    public static final int MIN_POST_DEPOSIT_DELAY = 2;     // Minimum ticks after depositing ores
    public static final int MAX_POST_DEPOSIT_DELAY = 40;    // Maximum ticks after depositing ores

    // Plugin state enum
    public enum PluginState {
        MINING,              // Mining pay-dirt veins
        TRAVELING_TO_HOPPER, // Walking to hopper area
        DEPOSITING_IN_HOPPER,          // Depositing at hopper
        EMPTYING_SACK,       // Taking ore from sack (after 4 deposits)
        TRAVELING_TO_DEPOSIT_BOX,// Walking to deposit box
        DEPOSITING_ORE_IN_DEPOSIT_BOX,      // Depositing ore at deposit box
        RETURNING            // Returning to mining area
    }
}
