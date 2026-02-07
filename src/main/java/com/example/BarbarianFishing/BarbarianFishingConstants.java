package com.example.BarbarianFishing;

/**
 * Constants for the Barbarian Fishing plugin
 * Contains all object IDs, item IDs, and animation IDs
 */
public class BarbarianFishingConstants {

    // Fishing spot IDs for barbarian fishing
    public static final int[] FISHING_SPOT_IDS = {
            1542,  // Fishing spot (Rod)
            1544,  // Fishing spot (Use-rod)
            // Add more as discovered
    };

    // Fish item IDs (barbarian fishing catches)
    public static final int LEAPING_TROUT_ID = 11328;
    public static final int LEAPING_SALMON_ID = 11330;
    public static final int LEAPING_STURGEON_ID = 11332;

    // Fishing animation IDs
    public static final int[] FISHING_ANIMATIONS = {
            9349,  // Barbarian fishing animation (casting/starting)
            9350,  // Barbarian fishing animation (waiting for catch)
            623,   // Alternative barbarian fishing animation
            622,   // Regular fishing animation (fallback)
            // Add more as discovered
    };

    // Plugin constants
    public static final int IDLE_TICKS_BEFORE_RECLICK = 3;
    public static final int MAX_INTERACTION_ATTEMPTS = 20;
    public static final int FISH_DROPS_PER_TICK = 6; // Drop up to 6 fish per tick

    // Variable interaction cooldown (human-like behavior)
    public static final int MIN_INTERACTION_COOLDOWN = 1;  // Minimum ticks between interactions
    public static final int MAX_INTERACTION_COOLDOWN = 3;  // Maximum ticks between interactions

    // Random delay bounds when fishing spot depletes (human-like behavior)
    public static final int MIN_SPOT_SWITCH_DELAY = 3;  // Minimum ticks before clicking new spot
    public static final int MAX_SPOT_SWITCH_DELAY = 15; // Maximum ticks before clicking new spot

    // Random delay bounds after finishing dropping fish (human-like behavior)
    public static final int MIN_POST_DROP_DELAY = 2;  // Minimum ticks after dropping before fishing again
    public static final int MAX_POST_DROP_DELAY = 9; // Maximum ticks after dropping before fishing again

    // Random delay after detecting full inventory (human-like behavior)
    public static final int MIN_INVENTORY_FULL_DELAY = 1;  // Minimum ticks to react to full inventory
    public static final int MAX_INVENTORY_FULL_DELAY = 11;  // Maximum ticks to react to full inventory

    // Plugin state enum
    public enum PluginState {
        FISHING,        // Actively fishing or finding fishing spots
        DROPPING,       // Dropping fish from inventory
        POST_DROP_DELAY // Waiting after dropping before returning to fishing (human-like)
    }
}
