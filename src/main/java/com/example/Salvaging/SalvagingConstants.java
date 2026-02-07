package com.example.Salvaging;

/**
 * Constants for the Salvaging plugin
 * Contains object IDs, animation IDs, item IDs, and state enum
 */
public class SalvagingConstants {

    // ===== SALVAGING HOOK IDs =====
    // Discovered in-game: 60505, 60506 (Adamant tier)
    // From LlemonDuck/sailing plugin for reference:
    // Bronze tier hooks (4 variants: raft, standard, large, alternate large)
    public static final int[] SALVAGING_HOOK_BRONZE = {54471, 54472, 54473, 54474};

    // Iron tier hooks
    public static final int[] SALVAGING_HOOK_IRON = {54475, 54476, 54477, 54478};

    // Steel tier hooks
    public static final int[] SALVAGING_HOOK_STEEL = {54479, 54480, 54481, 54482};

    // Mithril tier hooks
    public static final int[] SALVAGING_HOOK_MITHRIL = {54483, 54484, 54485, 54486};

    // Adamant tier hooks (CONFIRMED: 60487=Raft, 60494=Skiff, 60505/60506=Sloop)
    public static final int[] SALVAGING_HOOK_ADAMANT = {54487, 54488, 54489, 54490, 60487, 60494, 60505, 60506};

    // Rune tier hooks
    public static final int[] SALVAGING_HOOK_RUNE = {54491, 54492, 54493, 54494};

    // Dragon tier hooks
    public static final int[] SALVAGING_HOOK_DRAGON = {54495, 54496, 54497, 54498};

    // 60475 depleted, 60474 active
    // Mercenary shipwreck - gives Martial Salvage
    public static final int MERCENARY_WRECK_DEPLETED = 60475;
    public static final int MERCENARY_WRECK_ACTIVE = 60474;

    // All hook tiers combined for easy iteration
    public static final int[][] ALL_HOOK_TIERS = {
        SALVAGING_HOOK_BRONZE,
        SALVAGING_HOOK_IRON,
        SALVAGING_HOOK_STEEL,
        SALVAGING_HOOK_MITHRIL,
        SALVAGING_HOOK_ADAMANT,
        SALVAGING_HOOK_RUNE,
        SALVAGING_HOOK_DRAGON
    };

    // ===== DISCOVERED IN-GAME =====

    // Salvaging animation IDs (CONFIRMED: 13583, 13584)
    // 13583 = Starting salvaging animation
    // 13584 = Active salvaging animation
    public static final int[] SALVAGING_ANIMATIONS = {13583, 13584};

    // Sorting animation IDs (discovered in-game)
    // Enable "Debug Logging" to see animation IDs when sorting
    // Update these once discovered
    public static final int[] SORTING_ANIMATIONS = {13585, 13586}; // Placeholder - needs discovery

    // Camphor cargo hold IDs (from OSRS Wiki)
    // Closed: 60253, 60267, 60281
    // Open: 60254, 60268, 60282
    public static final int[] CARGO_HOLD_CLOSED = {60253, 60267, 60281};
    public static final int[] CARGO_HOLD_OPEN = {60254, 60268, 60282};
    public static final int[] CARGO_HOLD_ALL = {60253, 60267, 60281, 60254, 60268, 60282};

    // ===== TO BE DISCOVERED IN-GAME =====

    // Sorting station object IDs
    // Enable "Log Nearby Objects" config and look for salvaging station
    public static final int[] SORTING_STATION_IDS = {59699, 59700, 59701};

    // Plundered Salvage item IDs (the raw salvage items you pick up)
    // 32855 = Plundered salvage
    // 32857 = Martial salvage
    public static final int[] SALVAGE_ITEM_IDS = {32855, 32857};

    // Junk item IDs to drop after sorting (provided by user)
    public static final int[] DEFAULT_JUNK_ITEM_IDS = {
        1643, 31914, 1635, 11092, 413, 1639, 1637, 31973,
        11085, 1329, 28896, 405, 11076, 31912, 31910
    };

    // ===== CARGO HOLD CONTAINER IDs (from LlemonDuck/sailing plugin) =====
    public static final int CARGO_HOLD_BOAT_1 = 1047; // InventoryID.SAILING_BOAT_1_CARGOHOLD
    public static final int CARGO_HOLD_BOAT_2 = 1048; // InventoryID.SAILING_BOAT_2_CARGOHOLD
    public static final int CARGO_HOLD_BOAT_3 = 1049; // InventoryID.SAILING_BOAT_3_CARGOHOLD
    public static final int CARGO_HOLD_BOAT_4 = 1050; // InventoryID.SAILING_BOAT_4_CARGOHOLD
    public static final int CARGO_HOLD_BOAT_5 = 1051; // InventoryID.SAILING_BOAT_5_CARGOHOLD

    public static final int[] ALL_CARGO_HOLD_IDS = {
        CARGO_HOLD_BOAT_1,
        CARGO_HOLD_BOAT_2,
        CARGO_HOLD_BOAT_3,
        CARGO_HOLD_BOAT_4,
        CARGO_HOLD_BOAT_5
    };

    // ===== STATE MACHINE CONSTANTS =====
    public static final int IDLE_TICKS_BEFORE_RECLICK = 3;
    public static final int INTERACTION_COOLDOWN = 2; // Base cooldown between actions
    public static final int MAX_INTERACTION_ATTEMPTS = 20; // Safety limit
    public static final int CARGO_THRESHOLD_FOR_SORTING = 120; // Start sorting when cargo has ~120 items (out of 160)
    public static final int CARGO_NEARLY_EMPTY = 12; // Consider cargo "nearly empty" when <= 12 items (9 repair kits + buffer)

    /**
     * Plugin state enum for salvaging state machine
     */
    public enum PluginState {
        SALVAGING,              // Actively salvaging from hook
        WAITING_FOR_SALVAGE,    // Clicked hook, waiting for animation to start
        DEPOSITING_IN_HOLD,     // Depositing salvage items into cargo hold
        SORTING_SALVAGE,        // At sorting station, sorting salvage
        DROPPING_JUNK,          // Dropping whitelisted junk items from inventory
        IDLE                    // Not on boat or plugin disabled
    }
}
