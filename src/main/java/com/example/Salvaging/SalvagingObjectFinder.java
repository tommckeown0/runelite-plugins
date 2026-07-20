package com.example.Salvaging;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.TileObject;

import java.util.*;

import static com.example.Salvaging.SalvagingConstants.*;

/**
 * Helper class for finding salvaging-related game objects
 * Uses event-based tracking instead of TileObjects.search() since that doesn't work in non-top-level worlds
 */
public class SalvagingObjectFinder {

    private final Client client;
    private final SalvagingConfig config;

    // Store tracked objects (populated by GameObjectSpawned events in main plugin)
    private final Set<GameObject> salvagingHooks = new HashSet<>();
    private final Set<GameObject> sortingStations = new HashSet<>();
    private final Set<GameObject> allBoatObjects = new HashSet<>();

    public SalvagingObjectFinder(Client client, SalvagingConfig config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Add a game object to tracking (called from plugin's GameObjectSpawned event)
     */
    public void addObject(GameObject obj) {
        int id = obj.getId();

        // Check if it's a salvaging hook
        for (int[] tierHooks : ALL_HOOK_TIERS) {
            for (int hookId : tierHooks) {
                if (id == hookId) {
                    salvagingHooks.add(obj);
                    allBoatObjects.add(obj);
                    return;
                }
            }
        }

        // Check if it's a sorting station
        for (int stationId : SORTING_STATION_IDS) {
            if (id == stationId) {
                sortingStations.add(obj);
                allBoatObjects.add(obj);
                return;
            }
        }

        // Add to general tracking for discovery
        allBoatObjects.add(obj);
    }

    /**
     * Find the cargo hold on the boat
     * @return Optional containing the cargo hold, or empty if none found
     */
    public Optional<TileObject> findCargoHold() {
        // Search for cargo hold IDs (both closed and open states)
        for (GameObject obj : allBoatObjects) {
            for (int cargoId : CARGO_HOLD_ALL) {
                if (obj.getId() == cargoId) {
                    return Optional.of(obj);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Remove a game object from tracking (called from plugin's GameObjectDespawned event)
     */
    public void removeObject(GameObject obj) {
        salvagingHooks.remove(obj);
        sortingStations.remove(obj);
        allBoatObjects.remove(obj);
    }

    /**
     * Clear all tracked objects
     */
    public void clearAll() {
        salvagingHooks.clear();
        sortingStations.clear();
        allBoatObjects.clear();
    }

    /**
     * Scan and populate all existing objects in the scene
     * Called when plugin starts to catch objects that spawned before plugin was enabled
     */
    public void scanExistingObjects() {
        if (!isOnSailingBoat()) {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        // Get scene tiles and scan for game objects
        net.runelite.api.Scene scene = client.getScene();
        net.runelite.api.Tile[][][] tiles = scene.getTiles();

        int plane = client.getPlane();

        for (int x = 0; x < 104; x++) {
            for (int y = 0; y < 104; y++) {
                net.runelite.api.Tile tile = tiles[plane][x][y];
                if (tile == null) {
                    continue;
                }

                // Check all game objects on this tile
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject obj : gameObjects) {
                        if (obj != null) {
                            // Add to tracking (will be categorized by addObject method)
                            addObject(obj);
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the nearest salvaging hook on the boat
     * Searches through tracked hooks
     * @return Optional containing the nearest hook, or empty if none found
     */
    public Optional<TileObject> findNearestSalvagingHook() {
        if (salvagingHooks.isEmpty()) {
            return Optional.empty();
        }

        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        // Hardcoded to prefer hook ID 60506 (left side of sloop)
        for (GameObject hook : salvagingHooks) {
            if (hook.getId() == 60506) {
                return Optional.of(hook);
            }
        }

        // Fallback to any hook if 60506 not found
        return Optional.of(salvagingHooks.iterator().next());
    }

    /**
     * Find the sorting station on the boat
     * @return Optional containing the sorting station, or empty if none found
     */
    public Optional<TileObject> findSortingStation() {
        if (sortingStations.isEmpty()) {
            return Optional.empty();
        }

        // Return first station
        return Optional.of(sortingStations.iterator().next());
    }

    /**
     * Check if player is on a sailing boat
     * Uses WorldView detection: player is sailing when not on top-level world
     * @return true if player is on a boat, false otherwise
     */
    public boolean isOnSailingBoat() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        // WorldView detection from LlemonDuck/sailing plugin
        // When on a boat, the player is in a non-top-level world view
        return !player.getWorldView().isTopLevel();
    }

    /**
     * Get all tracked boat objects for debugging
     * @return List of all tracked game objects
     */
    public List<GameObject> getAllTrackedObjects() {
        return new ArrayList<>(allBoatObjects);
    }

    /**
     * Get count of tracked objects by type
     */
    public String getTrackedObjectsDebugInfo() {
        return String.format("Hooks: %d, Stations: %d, Total: %d",
            salvagingHooks.size(), sortingStations.size(), allBoatObjects.size());
    }

    /**
     * Check if there are active shipwrecks within 10 tiles of the player
     * Active shipwrecks should be prioritized for salvaging
     * @return true if active shipwrecks are nearby, false otherwise
     */
    public boolean hasActiveShipwrecksNearby() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        // Check all tracked objects for active shipwrecks
        for (GameObject obj : allBoatObjects) {
            if (obj.getId() == SalvagingConstants.MERCENARY_WRECK_ACTIVE) {
                int distance = obj.getWorldLocation().distanceTo(player.getWorldLocation());
                if (distance <= 10) {
                    return true;
                }
            }
        }

        return false;
    }
}
