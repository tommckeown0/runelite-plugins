package com.example.TileCoordinates;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;

public class TileCoordinatesOverlay extends Overlay {
    private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 13);

    private final Client client;
    private final TileCoordinatesConfig config;

    @Inject
    TileCoordinatesOverlay(Client client, TileCoordinatesConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        graphics.setFont(LABEL_FONT);

        if (config.showPlayerTile()) {
            Player player = client.getLocalPlayer();
            if (player != null) {
                renderTileLabel(graphics, player.getWorldLocation(), Color.CYAN);
            }
        }

        if (config.showHoveredTile()) {
            Tile hovered = client.getSelectedSceneTile();
            if (hovered != null) {
                WorldPoint wp = hovered.getWorldLocation();
                outlineTile(graphics, hovered.getLocalLocation(), Color.YELLOW);
                renderCursorLabel(graphics, wp);
            }
        }

        return null;
    }

    private void outlineTile(Graphics2D graphics, LocalPoint lp, Color color) {
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 25),
                    new BasicStroke(1f));
        }
    }

    private void renderTileLabel(Graphics2D graphics, WorldPoint wp, Color color) {
        if (wp == null) {
            return;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        String label = format(wp);
        Point loc = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
        if (loc != null) {
            OverlayUtil.renderTextLocation(graphics, loc, label, color);
        }
    }

    private void renderCursorLabel(Graphics2D graphics, WorldPoint wp) {
        if (wp == null) {
            return;
        }
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) {
            return;
        }
        String label = format(wp);
        Point loc = new Point(mouse.getX() + 12, mouse.getY() - 8);
        OverlayUtil.renderTextLocation(graphics, loc, label, Color.YELLOW);
    }

    private String format(WorldPoint wp) {
        StringBuilder sb = new StringBuilder();
        sb.append(wp.getX()).append(", ").append(wp.getY()).append(", ").append(wp.getPlane());
        if (config.showRegion()) {
            sb.append("  [region ").append(wp.getRegionID())
                    .append(" ").append(wp.getRegionX())
                    .append(",").append(wp.getRegionY()).append("]");
        }
        return sb.toString();
    }
}
