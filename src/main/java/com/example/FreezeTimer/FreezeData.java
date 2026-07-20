package com.example.FreezeTimer;

import net.runelite.api.NPC;

import java.awt.*;

/**
 * Tracks freeze spell data for a specific NPC
 */
public class FreezeData {
    private final NPC npc;
    private final FreezeSpell spell;
    private final long freezeStartTime;
    private final long freezeEndTime;
    private final int freezeDurationMs;

    public FreezeData(NPC npc, FreezeSpell spell) {
        this.npc = npc;
        this.spell = spell;
        this.freezeStartTime = System.currentTimeMillis();
        this.freezeDurationMs = spell.getDurationMs();
        this.freezeEndTime = this.freezeStartTime + this.freezeDurationMs;
    }

    public NPC getNpc() {
        return npc;
    }

    public FreezeSpell getSpell() {
        return spell;
    }

    public long getFreezeStartTime() {
        return freezeStartTime;
    }

    public long getFreezeEndTime() {
        return freezeEndTime;
    }

    public int getFreezeDurationMs() {
        return freezeDurationMs;
    }

    /**
     * Get remaining freeze time in milliseconds
     */
    public long getRemainingMs() {
        long remaining = freezeEndTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Get remaining freeze time in seconds (rounded up)
     */
    public int getRemainingSec() {
        return (int) Math.ceil(getRemainingMs() / 1000.0);
    }

    /**
     * Check if freeze has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= freezeEndTime;
    }

    /**
     * Get color based on remaining time (green → yellow → red)
     */
    public Color getTimerColor() {
        long remainingMs = getRemainingMs();
        long totalMs = freezeDurationMs;
        double percentRemaining = (double) remainingMs / totalMs;

        if (percentRemaining > 0.5) {
            return Color.GREEN;
        } else if (percentRemaining > 0.25) {
            return Color.YELLOW;
        } else {
            return Color.RED;
        }
    }

    /**
     * Enum representing all freeze spells in OSRS
     */
    public enum FreezeSpell {
        ICE_BARRAGE("Ice Barrage", 20000, 1979),
        ICE_BLITZ("Ice Blitz", 15000, 1978),
        ICE_BURST("Ice Burst", 10000, 1978),
        ICE_RUSH("Ice Rush", 5000, 1978),
        ENTANGLE("Entangle", 15000, 710),
        SNARE("Snare", 10000, 710),
        BIND("Bind", 5000, 710);

        private final String displayName;
        private final int durationMs;
        private final int animationId;

        FreezeSpell(String displayName, int durationMs, int animationId) {
            this.displayName = displayName;
            this.durationMs = durationMs;
            this.animationId = animationId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getDurationMs() {
            return durationMs;
        }

        public int getAnimationId() {
            return animationId;
        }

        /**
         * Detect freeze spell from animation ID
         * Returns the longest duration spell for shared animations (safer default)
         * Returns null if animation doesn't match a freeze spell
         */
        public static FreezeSpell fromAnimation(int animationId) {
            switch (animationId) {
                case 1979:
                    return ICE_BARRAGE;
                case 1978:
                    // Ice Blitz/Burst/Rush share animation - default to Blitz (15s)
                    return ICE_BLITZ;
                case 710:
                    // Entangle/Snare/Bind share animation - default to Entangle (15s)
                    return ENTANGLE;
                default:
                    return null;
            }
        }
    }
}
