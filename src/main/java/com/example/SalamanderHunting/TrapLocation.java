package com.example.SalamanderHunting;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the four trap locations for red salamander hunting.
 * Each location has a young tree. The trap can spawn on any adjacent tile (N/E/S/W)
 * depending on where the player was standing when setting the trap.
 */
public enum TrapLocation {
    NORTH(2449, 3228),
    WEST(2447, 3225),
    EAST(2451, 3225),
    SOUTH(2453, 3219);

    private final int treeX;
    private final int treeY;

    TrapLocation(int treeX, int treeY) {
        this.treeX = treeX;
        this.treeY = treeY;
    }

    public WorldPoint getTreeLocation() {
        return new WorldPoint(treeX, treeY, 0);
    }

    /**
     * Get all possible adjacent tiles where a trap could spawn (N/E/S/W of tree).
     */
    public List<WorldPoint> getAdjacentTiles() {
        List<WorldPoint> tiles = new ArrayList<>();
        tiles.add(new WorldPoint(treeX, treeY + 1, 0)); // North
        tiles.add(new WorldPoint(treeX, treeY - 1, 0)); // South
        tiles.add(new WorldPoint(treeX + 1, treeY, 0)); // East
        tiles.add(new WorldPoint(treeX - 1, treeY, 0)); // West
        return tiles;
    }

    public int getTreeX() {
        return treeX;
    }

    public int getTreeY() {
        return treeY;
    }
}
