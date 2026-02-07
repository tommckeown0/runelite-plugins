package com.example.MotherlodeMine;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import java.awt.*;
import java.util.List;

public class MotherlodeMineOverlay extends Overlay {
    private final MotherlodeMinePlugin plugin;
    private final Client client;
    private final MotherlodeMineConfig config;

    MotherlodeMineOverlay(Client client, MotherlodeMinePlugin plugin, MotherlodeMineConfig config) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.highlightDeposits()) {
            return null;
        }

        if (client.getLocalPlayer() == null) {
            return null;
        }

        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        // Get all cached nearby veins
        List<TileObject> veins = plugin.getCachedNearbyVeins();
        TileObject currentTarget = plugin.getCurrentTarget();

        // Draw all veins
        for (TileObject vein : veins) {
            WorldPoint veinLocation = vein.getWorldLocation();
            int distance = playerLocation.distanceTo(veinLocation);

            // Determine if this is the current target
            boolean isTarget = (currentTarget != null &&
                              currentTarget.getId() == vein.getId() &&
                              currentTarget.getWorldLocation().equals(veinLocation));

            // Use different colors for current target vs other veins
            Color color = isTarget ? Color.GREEN : config.highlightColor();
            Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 50);

            // Draw the tile highlight
            drawTile(graphics, veinLocation, color, fillColor);

            // Draw label with ID and distance
            String label = "ID: " + vein.getId();
            if (config.showDistances()) {
                label += " (" + distance + "t)";
            }
            if (isTarget) {
                label = ">>> " + label + " <<<";
            }

            drawLabel(graphics, veinLocation, label, color);
        }

        // Draw info panel
        drawInfoPanel(graphics);

        return null;
    }

    /**
     * Draw a tile highlight
     */
    private void drawTile(Graphics2D graphics, WorldPoint point, Color borderColor, Color fillColor) {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        // Don't render if too far away
        if (point.distanceTo(playerLocation) >= 32) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, borderColor, fillColor, new BasicStroke(2.0f));
        }
    }

    /**
     * Draw a text label on a tile
     */
    private void drawLabel(Graphics2D graphics, WorldPoint point, String label, Color color) {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= 32) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null) {
            return;
        }

        Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
        if (canvasTextLocation != null) {
            Font originalFont = graphics.getFont();
            graphics.setFont(new Font("Arial", Font.BOLD, 14));

            // Draw shadow for better readability
            graphics.setColor(Color.BLACK);
            graphics.drawString(label, canvasTextLocation.getX() + 1, canvasTextLocation.getY() + 1);

            // Draw actual text
            graphics.setColor(color);
            graphics.drawString(label, canvasTextLocation.getX(), canvasTextLocation.getY());

            graphics.setFont(originalFont);
        }
    }

    /**
     * Draw an info panel with current status
     */
    private void drawInfoPanel(Graphics2D graphics) {
        if (!config.debugLogging()) {
            return; // Only show panel when debug logging is enabled
        }

        int x = 10;
        int y = 200;
        int lineHeight = 15;

        graphics.setFont(new Font("Arial", Font.PLAIN, 12));

        // Background
        int panelWidth = 300;
        int panelHeight = 130;
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(x - 5, y - 15, panelWidth, panelHeight);

        // Border
        graphics.setColor(config.highlightColor());
        graphics.drawRect(x - 5, y - 15, panelWidth, panelHeight);

        // Title
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("Arial", Font.BOLD, 12));
        graphics.drawString("Motherlode Mine Debug Info", x, y);
        y += lineHeight + 5;

        graphics.setFont(new Font("Arial", Font.PLAIN, 11));

        // Status info
        List<TileObject> veins = plugin.getCachedNearbyVeins();
        TileObject target = plugin.getCurrentTarget();
        int animation = plugin.getCurrentAnimation();

        graphics.setColor(Color.CYAN);
        graphics.drawString("Veins detected: " + veins.size(), x, y);
        y += lineHeight;

        graphics.setColor(target != null ? Color.GREEN : Color.RED);
        graphics.drawString("Current target: " + (target != null ? "ID " + target.getId() : "None"), x, y);
        y += lineHeight;

        graphics.setColor(Color.YELLOW);
        graphics.drawString("Animation: " + animation, x, y);
        y += lineHeight;

        int emptySlots = plugin.getEmptySlotsForOverlay();
        graphics.setColor(emptySlots > 0 ? Color.GREEN : Color.RED);
        graphics.drawString("Inventory: " + (28 - emptySlots) + "/28 (Empty: " + emptySlots + ")", x, y);
        y += lineHeight;

        String state = plugin.getCurrentStateForOverlay();
        Color stateColor = Color.WHITE;
        if (state.equals("TRAVELING_TO_HOPPER")) {
            stateColor = Color.ORANGE;
        } else if (state.equals("DEPOSITING")) {
            stateColor = Color.MAGENTA;
        } else if (state.equals("RETURNING")) {
            stateColor = Color.CYAN;
        }
        graphics.setColor(stateColor);
        graphics.drawString("State: " + state, x, y);
        y += lineHeight;

        graphics.setColor(Color.WHITE);
        graphics.drawString("Auto-mining: " + (config.enableAutoMining() ? "ON" : "OFF"), x, y);
    }
}
