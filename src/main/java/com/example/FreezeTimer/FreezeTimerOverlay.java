package com.example.FreezeTimer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.Map;

/**
 * Renders freeze timers above NPCs in the game world
 */
public class FreezeTimerOverlay extends Overlay {
    private final FreezeTimerPlugin plugin;
    private final Client client;
    private final FreezeTimerConfig config;

    public FreezeTimerOverlay(Client client, FreezeTimerPlugin plugin, FreezeTimerConfig config) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Check if world overlay is enabled
        if (!config.enableWorldOverlay()) {
            return null;
        }

        // Ensure player exists
        if (client.getLocalPlayer() == null) {
            return null;
        }

        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        // Render each active freeze timer
        for (Map.Entry<NPC, FreezeData> entry : plugin.getActiveFreezes().entrySet()) {
            NPC npc = entry.getKey();
            FreezeData freezeData = entry.getValue();

            // Skip if expired
            if (freezeData.isExpired()) {
                continue;
            }

            // Skip if NPC is null or invalid
            if (npc == null || npc.getName() == null) {
                continue;
            }

            // Skip if too far away (performance optimization)
            WorldPoint npcLocation = npc.getWorldLocation();
            if (npcLocation.distanceTo(playerLocation) > config.maxRenderDistance()) {
                continue;
            }

            // Render timer above NPC
            renderNpcTimer(graphics, npc, freezeData);
        }

        return null;
    }

    /**
     * Render a timer above a specific NPC
     */
    private void renderNpcTimer(Graphics2D graphics, NPC npc, FreezeData freezeData) {
        LocalPoint lp = npc.getLocalLocation();
        if (lp == null) {
            return;
        }

        // Build timer text
        int remainingSec = freezeData.getRemainingSec();
        String timerText = remainingSec + "s";

        if (config.showSpellName()) {
            timerText = freezeData.getSpell().getDisplayName() + ": " + timerText;
        }

        // Get canvas position above NPC (with offset for height)
        int zOffset = npc.getLogicalHeight() + 40;
        Point canvasPoint = Perspective.getCanvasTextLocation(
                client, graphics, lp, timerText, zOffset
        );

        if (canvasPoint == null) {
            return;
        }

        // Set font
        Font originalFont = graphics.getFont();
        graphics.setFont(new Font("Arial", Font.BOLD, config.fontSize()));

        // Determine color
        Color textColor;
        if (config.useGradientColors()) {
            textColor = freezeData.getTimerColor();
        } else {
            textColor = config.timerColor();
        }

        // Draw shadow for readability
        graphics.setColor(config.shadowColor());
        graphics.drawString(timerText, canvasPoint.getX() + 1, canvasPoint.getY() + 1);

        // Draw timer text
        graphics.setColor(textColor);
        graphics.drawString(timerText, canvasPoint.getX(), canvasPoint.getY());

        // Restore original font
        graphics.setFont(originalFont);
    }
}
