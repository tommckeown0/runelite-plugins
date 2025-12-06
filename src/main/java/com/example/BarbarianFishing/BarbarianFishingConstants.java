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
    public static final int INTERACTION_COOLDOWN = 2;
    public static final int FISH_DROPS_PER_TICK = 6; // Drop up to 6 fish per tick

    // Plugin state enum
    public enum PluginState {
        FISHING,   // Actively fishing or finding fishing spots
        DROPPING   // Dropping fish from inventory
    }
}
