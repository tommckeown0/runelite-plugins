package com.example.MotherlodeMine;

import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.MotherlodeMine.MotherlodeMineConstants.*;

/**
 * Handles finding all game objects for the Motherlode Mine plugin
 */
public class MotherlodeMineObjectFinder {

    private final Client client;
    private final MotherlodeMineConfig config;

    public MotherlodeMineObjectFinder(Client client, MotherlodeMineConfig config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Find all nearby pay-dirt veins
     */
    public List<TileObject> findAllVeins() {
        List<TileObject> veins = new ArrayList<>();

        for (int veinId : PAYDIRT_VEIN_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> foundVeins = query.withId(veinId).result();
            veins.addAll(foundVeins);
        }

        return veins;
    }

    /**
     * Check if a point is within the configured mining region
     */
    public boolean isInMiningRegion(WorldPoint point) {
        if (!config.useMiningRegion()) {
            return true; // Region check disabled
        }

        int x = point.getX();
        int y = point.getY();
        int minX = config.regionMinX();
        int maxX = config.regionMaxX();
        int minY = config.regionMinY();
        int maxY = config.regionMaxY();

        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    /**
     * Find the nearest pay-dirt vein within the mining region
     */
    public Optional<TileObject> findNearestVein() {
        TileObject nearest = null;
        int minDistance = Integer.MAX_VALUE;

        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();

        for (int veinId : PAYDIRT_VEIN_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> veins = query.withId(veinId).result();

            for (TileObject vein : veins) {
                WorldPoint veinLoc = vein.getWorldLocation();
                int distance = playerLoc.distanceTo(veinLoc);

                // Skip if outside mining region
                if (!isInMiningRegion(veinLoc)) {
                    continue;
                }

                // Track nearest valid vein
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = vein;
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Find the nearest dark tunnel
     */
    public Optional<TileObject> findNearestTunnel() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();
        TileObject nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (int tunnelId : DARK_TUNNEL_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> tunnels = query.withId(tunnelId).result();

            for (TileObject tunnel : tunnels) {
                WorldPoint tunnelLoc = tunnel.getWorldLocation();
                int distance = playerLoc.distanceTo(tunnelLoc);

                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = tunnel;
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Find the hopper at the exact configured coordinates
     */
    public Optional<TileObject> findNearestHopper() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint targetHopperLoc = new WorldPoint(config.hopperX(), config.hopperY(), playerLoc.getPlane());
        TileObject exactHopper = null;

        for (int hopperId : HOPPER_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> hoppers = query.withId(hopperId).result();

            for (TileObject hopper : hoppers) {
                WorldPoint hopperLoc = hopper.getWorldLocation();

                // Prioritize hopper at exact coordinates
                if (hopperLoc.getX() == targetHopperLoc.getX() &&
                    hopperLoc.getY() == targetHopperLoc.getY() &&
                    hopperLoc.getPlane() == targetHopperLoc.getPlane()) {
                    return Optional.of(hopper);
                }

                // Fallback to nearest if exact not found
                if (exactHopper == null) {
                    exactHopper = hopper;
                }
            }
        }

        return Optional.ofNullable(exactHopper);
    }

    /**
     * Find the sack at the exact configured coordinates
     */
    public Optional<TileObject> findNearestSack() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint targetSackLoc = new WorldPoint(config.sackX(), config.sackY(), playerLoc.getPlane());

        for (int sackId : SACK_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> sacks = query.withId(sackId).result();

            for (TileObject sack : sacks) {
                WorldPoint sackLoc = sack.getWorldLocation();

                // Find sack at exact coordinates
                if (sackLoc.getX() == targetSackLoc.getX() &&
                    sackLoc.getY() == targetSackLoc.getY() &&
                    sackLoc.getPlane() == targetSackLoc.getPlane()) {
                    return Optional.of(sack);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find the deposit box at the exact configured coordinates
     */
    public Optional<TileObject> findNearestDepositBox() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint targetBoxLoc = new WorldPoint(config.depositBoxX(), config.depositBoxY(), playerLoc.getPlane());

        for (int boxId : DEPOSIT_BOX_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> boxes = query.withId(boxId).result();

            for (TileObject box : boxes) {
                WorldPoint boxLoc = box.getWorldLocation();

                // Find deposit box at exact coordinates
                if (boxLoc.getX() == targetBoxLoc.getX() &&
                    boxLoc.getY() == targetBoxLoc.getY() &&
                    boxLoc.getPlane() == targetBoxLoc.getPlane()) {
                    return Optional.of(box);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find the nearest broken strut at exact configured coordinates (for auto-repair functionality)
     */
    public Optional<TileObject> findNearestBrokenStrut() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return Optional.empty();
        }

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint topStrutLoc = new WorldPoint(config.topStrutX(), config.topStrutY(), playerLoc.getPlane());
        WorldPoint bottomStrutLoc = new WorldPoint(config.bottomStrutX(), config.bottomStrutY(), playerLoc.getPlane());

        TileObject nearest = null;
        int minDistance = Integer.MAX_VALUE;
        int strutsFound = 0;

        for (int strutId : BROKEN_STRUT_IDS) {
            TileObjectQuery query = TileObjects.search();
            List<TileObject> struts = query.withId(strutId).result();
            strutsFound = struts.size();

            for (TileObject strut : struts) {
                WorldPoint strutLoc = strut.getWorldLocation();

                // Check if this is at one of the exact configured coordinates
                boolean isTopStrut = (strutLoc.getX() == topStrutLoc.getX() &&
                                     strutLoc.getY() == topStrutLoc.getY() &&
                                     strutLoc.getPlane() == topStrutLoc.getPlane());

                boolean isBottomStrut = (strutLoc.getX() == bottomStrutLoc.getX() &&
                                        strutLoc.getY() == bottomStrutLoc.getY() &&
                                        strutLoc.getPlane() == bottomStrutLoc.getPlane());

                if (isTopStrut || isBottomStrut) {
                    int distance = playerLoc.distanceTo(strutLoc);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = strut;
                    }
                }
            }
        }

        return Optional.ofNullable(nearest);
    }
}
