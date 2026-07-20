package com.example.SalamanderHunting;

/**
 * Constants for red salamander hunting.
 */
public class SalamanderHuntingConstants {

    // Object IDs
    public static final int YOUNG_TREE_UNSET = 8990;           // Tree with no trap set
    public static final int YOUNG_TREE_TRAP_SET = 8989;        // Tree after trap is set (trap appears on adjacent tile)
    public static final int YOUNG_TREE_SETTING = 8991;         // Tree in process of having trap set (lasts one tick)
    public static final int TRAP_SET = 8992;                   // Net trap on the ground (waiting for salamander)

    // Caught trap IDs - there are multiple variants
    public static final int TRAP_LIZARD_CAUGHT = 8986;         // Net trap with salamander caught (variant 1)
    public static final int TRAP_LIZARD_CAUGHT_2 = 8985;       // Net trap with salamander caught (variant 2)
    public static final int TRAP_LIZARD_CAUGHT_3 = 8987;       // Net trap with salamander caught (variant 3 - possibly dismantled)

    // All caught trap IDs for easy checking
    public static final int[] CAUGHT_TRAP_IDS = {TRAP_LIZARD_CAUGHT, TRAP_LIZARD_CAUGHT_2, TRAP_LIZARD_CAUGHT_3};

    // Item IDs
    public static final int ROPE = 954;
    public static final int SMALL_FISHING_NET = 303;
    public static final int RED_SALAMANDER = 10147;

    // Animation IDs
    public static final int ANIM_SETTING_TRAP = 5215;          // Player animation when setting trap (lasts one tick)
    public static final int ANIM_CHECKING_TRAP = 5207;         // Player animation when checking/resetting trap (lasts two ticks)

    // Actions
    public static final String ACTION_SET_TRAP = "Set-trap";
    public static final String ACTION_CHECK = "Check";
    public static final String ACTION_RESET = "Reset";
    public static final String ACTION_RELEASE = "Release";

    // Ground item IDs to pick up when trap falls apart
    public static final int[] GROUND_ITEMS_TO_PICKUP = {ROPE, SMALL_FISHING_NET};
}
