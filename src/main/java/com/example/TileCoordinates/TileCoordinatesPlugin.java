package com.example.TileCoordinates;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

@PluginDescriptor(
        name = "Tile Coordinates",
        description = "Shows the coordinates of the tile under the mouse (and optionally the player); hotkey logs/copies the hovered tile",
        tags = {"tile", "coordinates", "location", "debug", "developer"},
        enabledByDefault = false
)
public class TileCoordinatesPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private TileCoordinatesConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private TileCoordinatesOverlay overlay;

    private final HotkeyListener logHotkey = new HotkeyListener(() -> config.logHotkey()) {
        @Override
        public void hotkeyPressed() {
            logHoveredTile();
        }
    };

    @Provides
    TileCoordinatesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TileCoordinatesConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(logHotkey);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(logHotkey);
    }

    private void logHoveredTile() {
        Tile hovered = client.getSelectedSceneTile();
        if (hovered == null) {
            return;
        }
        WorldPoint wp = hovered.getWorldLocation();
        String coords = "new WorldPoint(" + wp.getX() + ", " + wp.getY() + ", " + wp.getPlane() + ")";
        System.out.println("[TileCoordinates] " + coords
                + "  (region " + wp.getRegionID() + " " + wp.getRegionX() + "," + wp.getRegionY() + ")");
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(coords), null);
        } catch (Exception e) {
            System.out.println("[TileCoordinates] Failed to copy to clipboard: " + e.getMessage());
        }
    }
}
