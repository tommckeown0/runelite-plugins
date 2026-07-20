package com.example.FreezeTimer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Side panel overlay showing list of all active freeze timers
 */
public class FreezeTimerPanel extends Overlay {
    private final FreezeTimerPlugin plugin;
    private final Client client;
    private final FreezeTimerConfig config;

    public FreezeTimerPanel(Client client, FreezeTimerPlugin plugin, FreezeTimerConfig config) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Check if side panel is enabled
        if (!config.enableSidePanel()) {
            return null;
        }

        Map<NPC, FreezeData> activeFreezes = plugin.getActiveFreezes();

        // Don't render empty panel
        if (activeFreezes.isEmpty()) {
            return null;
        }

        int x = 10;
        int y = 100;
        int lineHeight = 18;
        int panelWidth = 220;

        // Sort freeze entries by remaining time (descending)
        List<Map.Entry<NPC, FreezeData>> sortedEntries = new ArrayList<>(activeFreezes.entrySet());
        sortedEntries.sort((a, b) ->
                Long.compare(b.getValue().getRemainingMs(), a.getValue().getRemainingMs())
        );

        // Filter out expired entries
        List<Map.Entry<NPC, FreezeData>> validEntries = new ArrayList<>();
        for (Map.Entry<NPC, FreezeData> entry : sortedEntries) {
            if (!entry.getValue().isExpired() && entry.getKey().getName() != null) {
                validEntries.add(entry);
            }
        }

        // Don't render if no valid entries after filtering
        if (validEntries.isEmpty()) {
            return null;
        }

        // Calculate panel height based on number of entries
        int contentHeight = validEntries.size() * lineHeight + 40;

        // Draw background
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(x - 5, y - 20, panelWidth, contentHeight);

        // Draw border
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x - 5, y - 20, panelWidth, contentHeight);

        // Draw title
        graphics.setFont(new Font("Arial", Font.BOLD, 14));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Freeze Timers", x, y);
        y += lineHeight + 5;

        // Draw separator line
        graphics.drawLine(x - 5, y - 8, x + panelWidth - 5, y - 8);

        // Draw each freeze entry
        graphics.setFont(new Font("Arial", Font.PLAIN, 12));

        for (Map.Entry<NPC, FreezeData> entry : validEntries) {
            NPC npc = entry.getKey();
            FreezeData freezeData = entry.getValue();

            // Build entry text
            String npcName = npc.getName() != null ? npc.getName() : "Unknown";
            int remainingSec = freezeData.getRemainingSec();
            String spellName = freezeData.getSpell().getDisplayName();

            String entryText;
            if (config.showSpellName()) {
                entryText = String.format("%s [%s]: %ds", npcName, spellName, remainingSec);
            } else {
                entryText = String.format("%s: %ds", npcName, remainingSec);
            }

            // Determine color
            Color textColor;
            if (config.useGradientColors()) {
                textColor = freezeData.getTimerColor();
            } else {
                textColor = config.timerColor();
            }

            graphics.setColor(textColor);
            graphics.drawString(entryText, x, y);
            y += lineHeight;
        }

        return new Dimension(panelWidth, contentHeight);
    }
}
