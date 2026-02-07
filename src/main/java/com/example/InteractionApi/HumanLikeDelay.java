package com.example.InteractionApi;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates human-like delays using weighted probability buckets based on real human behavior patterns.
 * Uses tick-based distributions to match what anti-cheat systems actually observe.
 *
 * Key principles for avoiding bot detection:
 * - NO overwhelming concentration at 1 tick (spread across 1-3 tick range)
 * - Include realistic long tail (occasional 5-20+ tick delays for distractions)
 * - Maintain variance even in repetitive tasks (humans aren't perfectly rhythmic)
 * - Different contexts have different distributions (reacting vs navigating vs planning)
 *
 * Human tick-delay patterns:
 * - Quick reactions: 1-2 ticks (40-70% depending on context)
 * - Normal delays: 3-5 ticks (20-30% - distractions, multitasking)
 * - Long tail: 6-20+ ticks (5-10% - attention lapses, fatigue, checking UI)
 *
 * Usage:
 *   int delayTicks = HumanLikeDelay.generate(HumanLikeDelay.Profile.RESOURCE_DEPLETION);
 */
public class HumanLikeDelay {

    /** OSRS tick duration in milliseconds */
    public static final int TICK_MS = 600;

    /**
     * Convert milliseconds to OSRS ticks (rounded)
     */
    public static int msToTicks(int ms) {
        return Math.max(1, Math.round((float) ms / TICK_MS));
    }

    /**
     * Delay bucket - represents a range with an associated probability weight
     * Can be defined in either ticks or milliseconds
     */
    public static class Bucket {
        public final int minValue;
        public final int maxValue;
        public final int weight; // Relative weight (doesn't need to sum to 100)
        public final boolean isMilliseconds;

        /** Create a tick-based bucket */
        public Bucket(int minTicks, int maxTicks, int weight) {
            this.minValue = minTicks;
            this.maxValue = maxTicks;
            this.weight = weight;
            this.isMilliseconds = false;
        }

        /** Create a millisecond-based bucket (converts to ticks on generation) */
        public static Bucket fromMs(int minMs, int maxMs, int weight) {
            Bucket b = new Bucket(0, 0, weight);
            return new Bucket(b.minValue, b.maxValue, weight, minMs, maxMs);
        }

        private Bucket(int unused1, int unused2, int weight, int minMs, int maxMs) {
            this.minValue = minMs;
            this.maxValue = maxMs;
            this.weight = weight;
            this.isMilliseconds = true;
        }

        /** Generate a value from this bucket (in ticks) */
        public int generateTicks() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int value = random.nextInt(minValue, maxValue + 1);
            return isMilliseconds ? msToTicks(value) : value;
        }
    }

    /**
     * Delay profile - collection of weighted buckets defining a delay distribution
     */
    public static class Profile {
        public final String name;
        public final Bucket[] buckets;
        private final int totalWeight;

        public Profile(String name, Bucket... buckets) {
            this.name = name;
            this.buckets = buckets;

            // Calculate total weight
            int sum = 0;
            for (Bucket bucket : buckets) {
                sum += bucket.weight;
            }
            this.totalWeight = sum;
        }

        /**
         * Generate a delay from this profile using weighted random selection
         * Returns value in ticks (automatically converts if bucket is in ms)
         */
        public int generateDelay() {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            // Pick a bucket based on weights
            int roll = random.nextInt(totalWeight);
            int cumulativeWeight = 0;

            for (Bucket bucket : buckets) {
                cumulativeWeight += bucket.weight;
                if (roll < cumulativeWeight) {
                    // Selected this bucket - generate value (converts to ticks if needed)
                    return bucket.generateTicks();
                }
            }

            // Fallback (shouldn't happen)
            return buckets[buckets.length - 1].generateTicks();
        }

        /**
         * Get human-readable description of this profile
         */
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(":\n");
            for (Bucket bucket : buckets) {
                int percentage = (bucket.weight * 100) / totalWeight;
                if (bucket.isMilliseconds) {
                    sb.append(String.format("  %2d%% → %d-%dms\n", percentage, bucket.minValue, bucket.maxValue));
                } else {
                    sb.append(String.format("  %2d%% → %d-%d ticks\n", percentage, bucket.minValue, bucket.maxValue));
                }
            }
            return sb.toString();
        }
    }

    // ========== PREDEFINED DELAY PROFILES ==========

    /**
     * RESOURCE_DEPLETION - When a resource node (vein, tree, fishing spot, etc.) depletes
     * Tick-based distribution that matches real human behavior patterns.
     *
     * Key characteristics of human reactions:
     * - Peak at 1-2 ticks (most common, but NOT overwhelming)
     * - Gradual falloff through 3-5 ticks (distractions, slower reactions)
     * - Long tail up to 15-20 ticks (attention lapses, multitasking)
     * - Mean: ~2.5 ticks, but with high variance
     */
    public static final Profile RESOURCE_DEPLETION = new Profile(
            "Resource Depletion (Human)",
            new Bucket(1, 1, 5),      // 40% - quick reaction (1 tick)
            new Bucket(2, 2, 18),      // 30% - normal reaction (2 ticks)
            new Bucket(3, 3, 40),      // 15% - slight delay
            new Bucket(4, 5, 30),       // 8% - distraction/slower
            new Bucket(6, 8, 4),       // 4% - brief attention lapse
            new Bucket(9, 12, 2),      // 2% - looking elsewhere
            new Bucket(13, 20, 1)      // 1% - long tail (fatigue/multitask)
    );

    /**
     * INVENTORY_FULL - Noticing inventory became full
     * UI changes are noticed faster, but humans still have variance
     * Tighter distribution than world changes, but still realistic spread
     */
    public static final Profile INVENTORY_FULL = new Profile(
            "Inventory Full Reaction",
//            new Bucket(1, 1, 55),      // 55% - quick UI awareness
//            new Bucket(2, 2, 30),      // 30% - normal check
//            new Bucket(3, 3, 10),      // 10% - finishing action first
//            new Bucket(4, 6, 4),       // 4% - delayed notice
//            new Bucket(7, 12, 1)       // 1% - distracted
            new Bucket(1, 1, 10),
            new Bucket(2, 2, 20),
            new Bucket(3, 3, 40),
            new Bucket(4, 6, 25),
            new Bucket(7, 12, 4),
            new Bucket(12, 19, 1)
    );

    /**
     * NAVIGATION_DELAY - Delay after arriving at a location before interacting
     * Humans pause to orient, check surroundings, plan next action
     * Longer delays are normal and expected here
     */
    public static final Profile NAVIGATION_DELAY = new Profile(
            "Post-Navigation Delay",
            new Bucket(1, 1, 8),       // 8% - immediate action (knows route well)
            new Bucket(2, 2, 25),      // 25% - quick orientation
            new Bucket(3, 4, 35),      // 35% - PEAK (normal pause)
            new Bucket(5, 7, 20),      // 20% - checking surroundings
            new Bucket(8, 12, 8),      // 8% - slower orientation
            new Bucket(13, 20, 3),     // 3% - checking map/other UI
            new Bucket(21, 30, 1)      // 1% - long pause
    );

    /**
     * ITEM_DROP - Noticing an unwanted item appeared in inventory
     * Very wide distribution - highly depends on focus and inventory management style
     * Some drop immediately, others batch drop later
     */
    public static final Profile ITEM_DROP = new Profile(
            "Item Drop Delay",
            new Bucket(1, 1, 15),      // 15% - immediate drop (very focused)
            new Bucket(2, 2, 20),      // 20% - quick drop
            new Bucket(3, 4, 25),      // 25% - noticed, will drop
            new Bucket(5, 8, 20),      // 20% - drop when convenient
            new Bucket(9, 15, 12),     // 12% - batch dropping later
            new Bucket(16, 25, 6),     // 6% - long delay
            new Bucket(26, 40, 2)      // 2% - very delayed drop
    );

    /**
     * BANK_DEPOSIT_COMPLETE - After finishing depositing items
     * Players pause to plan, check bank, organize
     * Wide distribution with significant variance
     */
    public static final Profile BANK_DEPOSIT_COMPLETE = new Profile(
            "Bank Deposit Complete",
            new Bucket(1, 2, 18),      // 18% - efficient, routine action
            new Bucket(3, 4, 30),      // 30% - normal banking rhythm
            new Bucket(5, 7, 25),      // 25% - checking/planning
            new Bucket(8, 12, 15),     // 15% - reviewing bank
            new Bucket(13, 20, 8),     // 8% - organizing/counting
            new Bucket(21, 35, 4)      // 4% - long pause/distraction
    );

    /**
     * POST_ACTIVITY_RESUME - Resuming activity after banking/depositing
     * Mix of muscle memory (fast) and reorientation (slower)
     * Moderate variance with occasional long pauses
     */
    public static final Profile POST_ACTIVITY_RESUME = new Profile(
            "Post-Activity Resume",
            new Bucket(1, 1, 20),      // 20% - muscle memory
            new Bucket(2, 3, 35),      // 35% - typical resume
            new Bucket(4, 6, 25),      // 25% - reorienting
            new Bucket(7, 10, 12),     // 12% - checking surroundings
            new Bucket(11, 18, 6),     // 6% - slower resume
            new Bucket(19, 30, 2)      // 2% - long delay
    );

    /**
     * INTERACTION_COOLDOWN - Delay between repetitive interactions
     * Still needs variance - humans don't have perfect rhythm
     * Peak at 1 tick but significant 2-3 tick occurrences
     */
    public static final Profile INTERACTION_COOLDOWN = new Profile(
            "Interaction Cooldown",
            new Bucket(1, 1, 30),      // 60% - fast rhythm
            new Bucket(2, 2, 50),      // 30% - normal rhythm
            new Bucket(3, 3, 18),       // 8% - slight hesitation
            new Bucket(4, 6, 2)        // 2% - micro-pause
    );

    // ========== CONVENIENCE METHODS ==========

    /**
     * Generate a delay using the specified profile
     */
    public static int generate(Profile profile) {
        return profile.generateDelay();
    }

    /**
     * Print statistics for testing - generates N delays and shows distribution
     */
    public static void printDistribution(Profile profile, int samples) {
        System.out.println("=== Distribution Test: " + profile.name + " ===");
        System.out.println("Sample size: " + samples);
        System.out.println();

        int[] counts = new int[profile.buckets.length];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;

        for (int i = 0; i < samples; i++) {
            int delay = profile.generateDelay();
            sum += delay;
            min = Math.min(min, delay);
            max = Math.max(max, delay);

            // Count which bucket this fell into (by generating range for this bucket)
            for (int j = 0; j < profile.buckets.length; j++) {
                Bucket bucket = profile.buckets[j];
                // For ms buckets, convert to ticks for comparison
                int minTicks = bucket.isMilliseconds ? msToTicks(bucket.minValue) : bucket.minValue;
                int maxTicks = bucket.isMilliseconds ? msToTicks(bucket.maxValue) : bucket.maxValue;
                if (delay >= minTicks && delay <= maxTicks) {
                    counts[j]++;
                    break;
                }
            }
        }

        double average = (double) sum / samples;

        System.out.println("Results:");
        System.out.printf("  Min: %d ticks\n", min);
        System.out.printf("  Max: %d ticks\n", max);
        System.out.printf("  Average: %.2f ticks\n", average);
        System.out.println();

        System.out.println("Bucket distribution:");
        for (int i = 0; i < profile.buckets.length; i++) {
            Bucket bucket = profile.buckets[i];
            double actualPercent = (counts[i] * 100.0) / samples;
            double expectedPercent = (bucket.weight * 100.0) / profile.totalWeight;
            if (bucket.isMilliseconds) {
                int minTicks = msToTicks(bucket.minValue);
                int maxTicks = msToTicks(bucket.maxValue);
                System.out.printf("  %d-%dms (%d-%dt): %.1f%% (expected: %.1f%%)\n",
                        bucket.minValue, bucket.maxValue, minTicks, maxTicks, actualPercent, expectedPercent);
            } else {
                System.out.printf("  %d-%d ticks: %.1f%% (expected: %.1f%%)\n",
                        bucket.minValue, bucket.maxValue, actualPercent, expectedPercent);
            }
        }
        System.out.println();
    }

    /**
     * Print tick distribution as histogram - shows what Jagex actually sees
     */
    public static void printTickHistogram(Profile profile, int samples) {
        System.out.println("=== TICK HISTOGRAM: " + profile.name + " ===");
        System.out.println("(This is what Jagex ML algorithms see)");
        System.out.println();

        // Generate samples and count occurrences of each tick value
        int[] tickCounts = new int[25]; // Support up to 25 ticks
        int total = 0;

        for (int i = 0; i < samples; i++) {
            int ticks = profile.generateDelay();
            if (ticks < tickCounts.length) {
                tickCounts[ticks]++;
                total++;
            }
        }

        // Find max for scaling
        int maxCount = 0;
        for (int count : tickCounts) {
            maxCount = Math.max(maxCount, count);
        }

        // Print histogram
        for (int tick = 1; tick < tickCounts.length; tick++) {
            if (tickCounts[tick] > 0) {
                double percent = (tickCounts[tick] * 100.0) / total;
                int barLength = (int) ((tickCounts[tick] * 50.0) / maxCount);
                String bar = "*".repeat(barLength);
                System.out.printf("%2d ticks | %5.1f%% | %s\n", tick, percent, bar);
            }
        }
        System.out.println();
    }

    /**
     * Test harness - shows both bucket distribution and what Jagex sees
     */
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("HUMAN-LIKE DELAY DISTRIBUTION ANALYSIS");
        System.out.println("Based on real human reaction time data");
        System.out.println("===============================================\n");

        // Show detailed analysis for RESOURCE_DEPLETION (most important)
        printTickHistogram(RESOURCE_DEPLETION, 100000);
        printDistribution(RESOURCE_DEPLETION, 10000);

        // Show tick histograms for all profiles
        printTickHistogram(INVENTORY_FULL, 100000);
        printTickHistogram(NAVIGATION_DELAY, 100000);
        printTickHistogram(ITEM_DROP, 100000);
        printTickHistogram(BANK_DEPOSIT_COMPLETE, 100000);
        printTickHistogram(POST_ACTIVITY_RESUME, 100000);
        printTickHistogram(INTERACTION_COOLDOWN, 100000);
    }
}
