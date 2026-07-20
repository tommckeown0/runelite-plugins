package com.example.SalamanderHunting;

/**
 * Represents the current state of a trap at a specific location.
 */
public class TrapState {
    private final TrapLocation location;
    private TrapStatus status;
    private long lastStateChangeTime;

    public enum TrapStatus {
        NO_TRAP,            // No trap set - young tree is unset
        TRAP_BEING_SET,     // Player is setting trap (animation in progress)
        TRAP_SET,           // Trap is set and waiting
        SALAMANDER_CAUGHT,  // Salamander caught in trap
        TRAP_FALLEN         // Trap fell apart - items on ground
    }

    public TrapState(TrapLocation location) {
        this.location = location;
        this.status = TrapStatus.NO_TRAP;
        this.lastStateChangeTime = 0;
    }

    public TrapLocation getLocation() {
        return location;
    }

    public TrapStatus getStatus() {
        return status;
    }

    public void setStatus(TrapStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.lastStateChangeTime = System.currentTimeMillis();
        }
    }

    public long getLastStateChangeTime() {
        return lastStateChangeTime;
    }

    /**
     * Check if this trap needs attention (caught salamander, fallen, or needs to be set).
     */
    public boolean needsAttention() {
        return status == TrapStatus.SALAMANDER_CAUGHT
            || status == TrapStatus.TRAP_FALLEN
            || status == TrapStatus.NO_TRAP;
    }

    /**
     * Check if this trap has urgent attention needed (caught or fallen - should be prioritized).
     */
    public boolean needsUrgentAttention() {
        return status == TrapStatus.SALAMANDER_CAUGHT || status == TrapStatus.TRAP_FALLEN;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", location.name(), status.name());
    }
}
