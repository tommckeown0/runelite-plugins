package com.example.DemonicGorilla;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;

public class DemonicGorillaOverlay extends Overlay {

    private static final Color DANGER_FILL = new Color(220, 20, 20, 90);
    private static final Color DANGER_BORDER = new Color(255, 0, 0);
    private static final Color SAFE_FILL = new Color(20, 220, 20, 45);
    private static final Color SAFE_BORDER = new Color(0, 220, 0);

    private final Client client;
    private final DemonicGorillaPlugin plugin;

    @Inject
    DemonicGorillaOverlay(Client client, DemonicGorillaPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (WorldPoint danger : plugin.getBoulderDangerTiles()) {
            outlineTile(graphics, danger, DANGER_BORDER, DANGER_FILL);
        }
        for (WorldPoint safe : plugin.getBoulderSafeTiles()) {
            outlineTile(graphics, safe, SAFE_BORDER, SAFE_FILL);
        }

        return null;
    }

    private void outlineTile(Graphics2D graphics, WorldPoint wp, Color border, Color fill) {
        if (wp == null) {
            return;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, border, fill, new BasicStroke(2f));
        }
    }
}
