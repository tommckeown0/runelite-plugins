package com.example.SalamanderHunting;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.TileItems;
import com.example.EthanApiPlugin.Collections.TileObjects;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

import java.util.*;
import java.util.function.Consumer;

import static com.example.SalamanderHunting.SalamanderHuntingConstants.*;

/**
 * Manages the state of all four trap locations and provides event-based priority ordering.
 */
public class TrapManager {
    private final Map<TrapLocation, TrapState> trapStates = new EnumMap<>(TrapLocation.class);
    private final Consumer<String> logger;

    public TrapManager(Consumer<String> logger) {
        this.logger = logger;
        // Initialize all trap states
        for (TrapLocation location : TrapLocation.values()) {
            trapStates.put(location, new TrapState(location));
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    /**
     * Update the state of all traps based on current game objects.
     */
    public void updateTrapStates() {
        for (TrapLocation location : TrapLocation.values()) {
            TrapState state = trapStates.get(location);
            TrapState.TrapStatus newStatus = detectStatusAtLocation(location);

            if (state.getStatus() != newStatus) {
                log(String.format("Trap %s changed: %s -> %s",
                    location.name(), state.getStatus(), newStatus));

                // Log detailed info on state change
                if (newStatus == TrapState.TrapStatus.NO_TRAP &&
                    state.getStatus() == TrapState.TrapStatus.TRAP_SET) {
                    logDetailedTrapInfo(location);
                }
            }
            state.setStatus(newStatus);
        }
    }

    /**
     * Log detailed information about what objects exist near a trap location.
     */
    private void logDetailedTrapInfo(TrapLocation location) {
        WorldPoint treePoint = location.getTreeLocation();
        List<WorldPoint> adjacentTiles = location.getAdjacentTiles();

        log(String.format("=== DETAILED DEBUG for %s (tree at %s) ===", location.name(), treePoint));

        // Log what's at the tree location
        List<TileObject> atTree = TileObjects.search()
            .atLocation(treePoint)
            .result();
        log(String.format("  At tree location (%s): %d objects", treePoint, atTree.size()));
        for (TileObject obj : atTree) {
            log(String.format("    ID: %d", obj.getId()));
        }

        // Log what's at each adjacent tile
        for (WorldPoint tile : adjacentTiles) {
            List<TileObject> atTile = TileObjects.search()
                .atLocation(tile)
                .result();
            log(String.format("  At adjacent tile (%s): %d objects", tile, atTile.size()));
            for (TileObject obj : atTile) {
                log(String.format("    ID: %d", obj.getId()));
            }
        }

        // Log ALL trap-related objects in the entire TileObjects collection
        log("  Searching for trap IDs globally:");
        List<TileObject> caughtTraps = TileObjects.search()
            .filter(obj -> obj.getId() == TRAP_LIZARD_CAUGHT)
            .result();
        log(String.format("    Caught traps (ID %d) found: %d", TRAP_LIZARD_CAUGHT, caughtTraps.size()));
        for (TileObject obj : caughtTraps) {
            log(String.format("      At: %s", obj.getWorldLocation()));
        }

        List<TileObject> setTraps = TileObjects.search()
            .filter(obj -> obj.getId() == TRAP_SET)
            .result();
        log(String.format("    Set traps (ID %d) found: %d", TRAP_SET, setTraps.size()));
        for (TileObject obj : setTraps) {
            log(String.format("      At: %s", obj.getWorldLocation()));
        }

        log("=== END DETAILED DEBUG ===");
    }

    /**
     * Debug method to log all objects near a tree location.
     */
    public void debugLogObjectsNearTree(TrapLocation location) {
        WorldPoint treePoint = location.getTreeLocation();
        log(String.format("=== DEBUG: Objects near %s tree at %s ===", location.name(), treePoint));

        // Search for all objects within 2 tiles of the tree
        List<net.runelite.api.TileObject> nearbyObjects = TileObjects.search()
            .filter(obj -> {
                WorldPoint objLoc = obj.getWorldLocation();
                int dx = Math.abs(objLoc.getX() - treePoint.getX());
                int dy = Math.abs(objLoc.getY() - treePoint.getY());
                return dx <= 2 && dy <= 2 && objLoc.getPlane() == treePoint.getPlane();
            })
            .result();

        for (net.runelite.api.TileObject obj : nearbyObjects) {
            WorldPoint objLoc = obj.getWorldLocation();
            log(String.format("  Object ID: %d at %s", obj.getId(), objLoc));
        }

        if (nearbyObjects.isEmpty()) {
            log("  No objects found within 2 tiles of tree");
        }
        log("=== END DEBUG ===");
    }

    /**
     * Helper to check if an object ID is a caught trap.
     */
    private boolean isCaughtTrap(int objectId) {
        for (int id : CAUGHT_TRAP_IDS) {
            if (objectId == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detect the current status at a trap location by checking game objects.
     * Caught traps can spawn at the tree location OR on adjacent tiles.
     */
    private TrapState.TrapStatus detectStatusAtLocation(TrapLocation location) {
        WorldPoint treePoint = location.getTreeLocation();
        List<WorldPoint> adjacentTiles = location.getAdjacentTiles();
        TrapState currentState = trapStates.get(location);

        // Check for caught salamander trap AT THE TREE LOCATION (this is common!)
        Optional<TileObject> caughtAtTree = TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> isCaughtTrap(obj.getId()))
            .first();
        if (caughtAtTree.isPresent()) {
            return TrapState.TrapStatus.SALAMANDER_CAUGHT;
        }

        // Check for caught salamander trap on any adjacent tile
        for (WorldPoint tile : adjacentTiles) {
            Optional<TileObject> caughtTrap = TileObjects.search()
                .atLocation(tile)
                .filter(obj -> isCaughtTrap(obj.getId()))
                .first();
            if (caughtTrap.isPresent()) {
                return TrapState.TrapStatus.SALAMANDER_CAUGHT;
            }
        }

        // Check for set trap on any adjacent tile
        for (WorldPoint tile : adjacentTiles) {
            Optional<TileObject> setTrap = TileObjects.search()
                .atLocation(tile)
                .filter(obj -> obj.getId() == TRAP_SET)
                .first();
            if (setTrap.isPresent()) {
                return TrapState.TrapStatus.TRAP_SET;
            }
        }

        // Check for tree being set (transitional state)
        Optional<TileObject> treeSetting = TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> obj.getId() == YOUNG_TREE_SETTING)
            .first();
        if (treeSetting.isPresent()) {
            return TrapState.TrapStatus.TRAP_BEING_SET;
        }

        // Check for tree with trap set (tree state after trap placed)
        Optional<TileObject> treeWithTrap = TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> obj.getId() == YOUNG_TREE_TRAP_SET)
            .first();
        if (treeWithTrap.isPresent()) {
            // Tree shows trap was set - the trap object should be on an adjacent tile
            // If we get here, the trap object wasn't found above, so assume it's set
            return TrapState.TrapStatus.TRAP_SET;
        }

        // Check for ground items (rope/net) on any adjacent tile - indicates fallen trap
        for (WorldPoint tile : adjacentTiles) {
            boolean hasGroundItems = TileItems.search()
                .withinDistanceToPoint(0, tile)
                .filter(item -> item.getTileItem().getId() == ROPE || item.getTileItem().getId() == SMALL_FISHING_NET)
                .first()
                .isPresent();
            if (hasGroundItems) {
                return TrapState.TrapStatus.TRAP_FALLEN;
            }
        }

        // Check for unset young tree
        Optional<TileObject> unsetTree = TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> obj.getId() == YOUNG_TREE_UNSET)
            .first();
        if (unsetTree.isPresent()) {
            return TrapState.TrapStatus.NO_TRAP;
        }

        // If we were previously TRAP_SET but now can't find anything, log what we see
        if (currentState != null && currentState.getStatus() == TrapState.TrapStatus.TRAP_SET) {
            log(String.format("WARN: %s was TRAP_SET but now can't find trap objects. Checking nearby:", location.name()));
            List<TileObject> nearbyObjects = TileObjects.search()
                .filter(obj -> {
                    WorldPoint objLoc = obj.getWorldLocation();
                    int dx = Math.abs(objLoc.getX() - treePoint.getX());
                    int dy = Math.abs(objLoc.getY() - treePoint.getY());
                    return dx <= 2 && dy <= 2;
                })
                .result();
            if (nearbyObjects.isEmpty()) {
                log("  No objects found within 2 tiles!");
            } else {
                for (TileObject obj : nearbyObjects) {
                    log(String.format("  Found ID %d at %s", obj.getId(), obj.getWorldLocation()));
                }
            }
        }

        // Default - no trap
        return TrapState.TrapStatus.NO_TRAP;
    }

    /**
     * Get traps that need attention, sorted by when they changed state (oldest first).
     * This implements the event-based priority - handle traps in the order they changed.
     */
    public List<TrapState> getTrapsNeedingAttention() {
        List<TrapState> needingAttention = new ArrayList<>();

        for (TrapState state : trapStates.values()) {
            if (state.needsAttention()) {
                needingAttention.add(state);
            }
        }

        // Sort by state change time (oldest first = highest priority)
        needingAttention.sort(Comparator.comparingLong(TrapState::getLastStateChangeTime));

        return needingAttention;
    }

    /**
     * Get traps that urgently need attention (caught salamander or fallen trap),
     * sorted by when they changed state (oldest first).
     */
    public List<TrapState> getUrgentTraps() {
        List<TrapState> urgent = new ArrayList<>();

        for (TrapState state : trapStates.values()) {
            if (state.needsUrgentAttention()) {
                urgent.add(state);
            }
        }

        // Sort by state change time (oldest first = highest priority)
        urgent.sort(Comparator.comparingLong(TrapState::getLastStateChangeTime));

        return urgent;
    }

    /**
     * Get the highest priority trap that needs attention.
     */
    public Optional<TrapState> getNextTrapToHandle() {
        // First check for urgent traps (caught or fallen)
        List<TrapState> urgent = getUrgentTraps();
        if (!urgent.isEmpty()) {
            return Optional.of(urgent.get(0));
        }

        // Then check for traps that need to be set
        List<TrapState> needsAttention = getTrapsNeedingAttention();
        if (!needsAttention.isEmpty()) {
            return Optional.of(needsAttention.get(0));
        }

        return Optional.empty();
    }

    /**
     * Get the state for a specific location.
     */
    public TrapState getState(TrapLocation location) {
        return trapStates.get(location);
    }

    /**
     * Get all trap states.
     */
    public Collection<TrapState> getAllStates() {
        return trapStates.values();
    }

    /**
     * Find a trap object (caught or set) at the tree location or any adjacent tile.
     */
    public Optional<TileObject> findTrapObjectAtLocation(TrapLocation location) {
        WorldPoint treePoint = location.getTreeLocation();
        List<WorldPoint> adjacentTiles = location.getAdjacentTiles();

        // First check for caught traps at tree location (highest priority)
        Optional<TileObject> caughtAtTree = TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> isCaughtTrap(obj.getId()))
            .first();
        if (caughtAtTree.isPresent()) {
            return caughtAtTree;
        }

        // Then check for caught traps on adjacent tiles
        for (WorldPoint tile : adjacentTiles) {
            Optional<TileObject> caughtTrap = TileObjects.search()
                .atLocation(tile)
                .filter(obj -> isCaughtTrap(obj.getId()))
                .first();
            if (caughtTrap.isPresent()) {
                return caughtTrap;
            }
        }

        // Then check for set traps on adjacent tiles
        for (WorldPoint tile : adjacentTiles) {
            Optional<TileObject> setTrap = TileObjects.search()
                .atLocation(tile)
                .filter(obj -> obj.getId() == TRAP_SET)
                .first();
            if (setTrap.isPresent()) {
                return setTrap;
            }
        }

        return Optional.empty();
    }

    /**
     * Find the young tree at a given location.
     */
    public Optional<TileObject> findTreeAtLocation(TrapLocation location) {
        WorldPoint treePoint = location.getTreeLocation();
        return TileObjects.search()
            .atLocation(treePoint)
            .filter(obj -> obj.getId() == YOUNG_TREE_UNSET)
            .first();
    }

    /**
     * Find ground items (rope/net) on any adjacent tile for a given tree location.
     */
    public Optional<ETileItem> findGroundItemsAtLocation(TrapLocation location) {
        List<WorldPoint> adjacentTiles = location.getAdjacentTiles();

        for (WorldPoint tile : adjacentTiles) {
            Optional<ETileItem> item = TileItems.search()
                .withinDistanceToPoint(0, tile)
                .filter(i -> i.getTileItem().getId() == ROPE || i.getTileItem().getId() == SMALL_FISHING_NET)
                .first();
            if (item.isPresent()) {
                return item;
            }
        }

        return Optional.empty();
    }

    /**
     * Count how many traps are currently set and active.
     */
    public int getActiveTrapsCount() {
        int count = 0;
        for (TrapState state : trapStates.values()) {
            if (state.getStatus() == TrapState.TrapStatus.TRAP_SET ||
                state.getStatus() == TrapState.TrapStatus.TRAP_BEING_SET) {
                count++;
            }
        }
        return count;
    }
}
