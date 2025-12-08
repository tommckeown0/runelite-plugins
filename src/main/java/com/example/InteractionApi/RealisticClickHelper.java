package com.example.InteractionApi;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper class to generate realistic click coordinates for game entities.
 * Provides methods to get canvas coordinates with human-like randomization.
 *
 * Key principles:
 * - Clicks should be within the visible bounds of the target
 * - Add gaussian distribution bias toward center (humans aim for middle)
 * - Occasional "edge" clicks (humans aren't perfectly accurate)
 * - Different targets have different click patterns
 */
public class RealisticClickHelper {

    private static final Client client = RuneLite.getInjector().getInstance(Client.class);
    private static boolean loggingEnabled = false;

    /**
     * Enable or disable coordinate logging for debugging
     */
    public static void setLoggingEnabled(boolean enabled) {
        loggingEnabled = enabled;
    }

    private static void log(String message) {
        if (loggingEnabled) {
            System.out.println("[RealisticClickHelper] " + message);
        }
    }

    /**
     * Get a realistic click point for an NPC.
     * Uses the NPC's convex hull (visible shape on screen) to determine clickable area.
     *
     * @param npc The NPC to click
     * @return Point with canvas coordinates, or null if NPC not visible
     */
    public static java.awt.Point getNPCClickPoint(NPC npc) {
        return getNPCClickPoint(npc, false);
    }

    /**
     * Get a realistic click point for an NPC, optionally rotating camera if off-screen
     * @param npc The NPC to click
     * @param rotateIfNeeded If true, will rotate camera to bring NPC into view
     * @return Point with canvas coordinates, or null if NPC not visible
     */
    public static java.awt.Point getNPCClickPoint(NPC npc, boolean rotateIfNeeded) {
        if (npc == null) {
            log("getNPCClickPoint: npc is null");
            return null;
        }

        String npcName = npc.getName() != null ? npc.getName() : "Unknown";
        int npcId = npc.getId();

        // Try to get the NPC's convex hull (its visible shape on screen)
        Shape hull = npc.getConvexHull();
        if (hull != null) {
            Rectangle bounds = hull.getBounds();
            java.awt.Point point = getRandomPointInShape(hull, 0.7); // 70% center bias
            log(String.format("NPC Click → \"%s\" (ID: %d) | Convex Hull | Bounds: [%d,%d %dx%d] | Click: (%d, %d)",
                    npcName, npcId, bounds.x, bounds.y, bounds.width, bounds.height, point.x, point.y));
            return point;
        }

        // Fallback: use the NPC's tile location
        LocalPoint lp = npc.getLocalLocation();
        if (lp != null) {
            Polygon tilePoly = Perspective.getCanvasTilePoly(client, lp);
            if (tilePoly != null) {
                Rectangle bounds = tilePoly.getBounds();
                java.awt.Point point = getRandomPointInShape(tilePoly, 0.6); // Less bias for tile clicks
                log(String.format("NPC Click → \"%s\" (ID: %d) | Tile Poly | Bounds: [%d,%d %dx%d] | Click: (%d, %d)",
                        npcName, npcId, bounds.x, bounds.y, bounds.width, bounds.height, point.x, point.y));
                return point;
            }
        }

        // NPC is off-screen - rotate camera if requested
        if (rotateIfNeeded && npc.getWorldLocation() != null) {
            log(String.format("getNPCClickPoint: \"%s\" (ID: %d) is off-screen, rotating camera...", npcName, npcId));
            if (CameraController.ensureEntityVisible(npc.getWorldLocation())) {
                // Camera rotated, wait a moment for rendering
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Retry getting click point
                return getNPCClickPoint(npc, false); // Don't recurse infinitely
            }
        }

        log(String.format("getNPCClickPoint: Could not get clickable area for \"%s\" (ID: %d)", npcName, npcId));
        return null;
    }

    /**
     * Get a realistic click point for a Widget (inventory item, interface button, etc.).
     * Uses the widget's canvas bounds.
     *
     * @param widget The widget to click
     * @return Point with canvas coordinates, or null if widget not visible
     */
    public static java.awt.Point getWidgetClickPoint(Widget widget) {
        if (widget == null || widget.isHidden()) {
            log("getWidgetClickPoint: widget is null or hidden");
            return null;
        }

        Rectangle bounds = widget.getBounds();
        if (bounds == null) {
            log("getWidgetClickPoint: widget bounds are null");
            return null;
        }

        // Get widget info for logging
        String widgetName = widget.getName() != null && !widget.getName().isEmpty() ? widget.getName() : "Unknown";
        int itemId = widget.getItemId();
        int widgetIndex = widget.getIndex();

        // For small widgets (like inventory items), use strong center bias
        // For large widgets (like buttons), use less bias
        double centerBias = bounds.width < 40 ? 0.75 : 0.6;

        java.awt.Point point = getRandomPointInShape(bounds, centerBias);

        if (itemId != -1) {
            log(String.format("Widget Click → \"%s\" (ItemID: %d, Slot: %d) | Bounds: [%d,%d %dx%d] | Click: (%d, %d) | Bias: %.0f%%",
                    widgetName, itemId, widgetIndex, bounds.x, bounds.y, bounds.width, bounds.height, point.x, point.y, centerBias * 100));
        } else {
            log(String.format("Widget Click → \"%s\" (Slot: %d) | Bounds: [%d,%d %dx%d] | Click: (%d, %d) | Bias: %.0f%%",
                    widgetName, widgetIndex, bounds.x, bounds.y, bounds.width, bounds.height, point.x, point.y, centerBias * 100));
        }

        return point;
    }

    /**
     * Get a realistic click point for a TileObject (game objects, ground items, etc.).
     *
     * @param tileObject The tile object to click
     * @return Point with canvas coordinates, or null if not visible
     */
    public static java.awt.Point getTileObjectClickPoint(TileObject tileObject) {
        if (tileObject == null) {
            log("getTileObjectClickPoint: tileObject is null");
            return null;
        }

        int objectId = tileObject.getId();

        // Use tile polygon for TileObjects
        LocalPoint lp = tileObject.getLocalLocation();
        if (lp != null) {
            Polygon tilePoly = Perspective.getCanvasTilePoly(client, lp);
            if (tilePoly != null) {
                Rectangle bounds = tilePoly.getBounds();
                java.awt.Point point = getRandomPointInShape(tilePoly, 0.6);
                log(String.format("TileObject Click → (ID: %d) | Tile Poly | Bounds: [%d,%d %dx%d] | Click: (%d, %d)",
                        objectId, bounds.x, bounds.y, bounds.width, bounds.height, point.x, point.y));
                return point;
            }
        }

        log(String.format("getTileObjectClickPoint: Could not get tile poly for object (ID: %d)", objectId));
        return null;
    }

    /**
     * Generate a random point within a shape with gaussian distribution bias toward center.
     *
     * @param shape The shape to click within
     * @param centerBias How strongly to bias toward center (0.0 = uniform, 1.0 = always center)
     * @return Random point within the shape
     */
    private static java.awt.Point getRandomPointInShape(Shape shape, double centerBias) {
        Rectangle bounds = shape.getBounds();
        if (bounds.width == 0 || bounds.height == 0) {
            return new java.awt.Point(bounds.x, bounds.y);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Try up to 50 times to find a point inside the shape
        // (for complex shapes, bounding box might include non-clickable areas)
        for (int attempt = 0; attempt < 50; attempt++) {
            int x, y;

            if (random.nextDouble() < centerBias) {
                // Gaussian distribution toward center (human tendency)
                x = (int) Math.round(bounds.getCenterX() + random.nextGaussian() * bounds.width * 0.2);
                y = (int) Math.round(bounds.getCenterY() + random.nextGaussian() * bounds.height * 0.2);
            } else {
                // Uniform distribution (occasional edge clicks)
                x = random.nextInt(bounds.x, bounds.x + bounds.width);
                y = random.nextInt(bounds.y, bounds.y + bounds.height);
            }

            // Clamp to bounds
            x = Math.max(bounds.x, Math.min(bounds.x + bounds.width - 1, x));
            y = Math.max(bounds.y, Math.min(bounds.y + bounds.height - 1, y));

            // Check if point is actually inside the shape
            if (shape.contains(x, y)) {
                return new java.awt.Point(x, y);
            }
        }

        // Fallback: return center of bounds
        return new java.awt.Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /**
     * Add small random offset to a point (for "micro-adjustments" or slight inaccuracy).
     * Useful for simulating hand tremor or slight mouse drift.
     *
     * @param point Original point
     * @param maxOffset Maximum offset in pixels (typically 1-3)
     * @return New point with slight offset
     */
    public static java.awt.Point addJitter(java.awt.Point point, int maxOffset) {
        if (point == null || maxOffset <= 0) {
            return point;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int offsetX = random.nextInt(-maxOffset, maxOffset + 1);
        int offsetY = random.nextInt(-maxOffset, maxOffset + 1);

        return new java.awt.Point(point.x + offsetX, point.y + offsetY);
    }

    /**
     * Check if an NPC is visible on screen (has a valid convex hull or tile polygon).
     *
     * @param npc The NPC to check
     * @return true if NPC is visible and clickable
     */
    public static boolean isNPCVisible(NPC npc) {
        return getNPCClickPoint(npc) != null;
    }

    /**
     * Check if a Widget is visible on screen.
     *
     * @param widget The widget to check
     * @return true if widget is visible and clickable
     */
    public static boolean isWidgetVisible(Widget widget) {
        return getWidgetClickPoint(widget) != null;
    }

    /**
     * Check if a TileObject is visible on screen.
     *
     * @param tileObject The tile object to check
     * @return true if object is visible and clickable
     */
    public static boolean isTileObjectVisible(TileObject tileObject) {
        return getTileObjectClickPoint(tileObject) != null;
    }
}
