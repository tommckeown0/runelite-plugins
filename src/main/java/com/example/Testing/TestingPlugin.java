package com.example.Testing;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Clean widget discovery tool for debugging UI interactions
 * Logs all visible widgets and their properties to help discover widget IDs
 */
@PluginDescriptor(
    name = "A Testing",
    description = "Widget discovery and logging tool",
    tags = {"testing", "widgets", "discovery"},
    hidden = false,
    enabledByDefault = false
)
public class TestingPlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private TestingPluginConfig config;

    private int tickCounter = 0;
    private boolean lastScanState = false;

    @Provides
    TestingPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TestingPluginConfig.class);
    }

    @Override
    protected void startUp() {
        log("========================================");
        log("Widget Discovery Tool STARTED");
        log("========================================");
        tickCounter = 0;
    }

    @Override
    protected void shutDown() {
        log("========================================");
        log("Widget Discovery Tool STOPPED");
        log("========================================");
    }

    /**
     * Logging helper with timestamp
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "][WidgetTool] " + message);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        tickCounter++;

        // Check if scan was just enabled
        boolean scanEnabled = config.scanWidgets();
        if (scanEnabled && !lastScanState) {
            log(">>> SCAN TRIGGERED - Analyzing all visible widgets...");
            scanAllWidgets();
        }
        lastScanState = scanEnabled;
    }

    /**
     * Comprehensive widget scan - logs all visible widgets with detailed information
     */
    private void scanAllWidgets() {
        log("========================================");
        log("FULL WIDGET SCAN - All Visible Widgets");
        log("========================================");

        int totalVisibleGroups = 0;
        int groupsWithItems = 0;
        int totalWidgetsScanned = 0;

        // Scan all widget groups (0-1000)
        for (int groupId = 0; groupId < 1000; groupId++) {
            Widget parent = client.getWidget(groupId, 0);

            if (parent == null || parent.isHidden()) {
                continue;
            }

            totalVisibleGroups++;
            boolean hasItems = false;
            int itemsInGroup = 0;

            // Check for items in children
            Widget[] children = parent.getChildren();
            if (children != null && children.length > 0) {
                for (Widget child : children) {
                    if (child != null && !child.isHidden() && child.getItemId() > 0) {
                        hasItems = true;
                        itemsInGroup++;
                    }
                }
            }

            // Log groups with items first (most relevant for cargo hold)
            if (hasItems) {
                groupsWithItems++;
                logWidgetGroup(groupId, parent, true);
            } else if (config.logAllWidgets()) {
                // Only log empty groups if config option is enabled
                logWidgetGroup(groupId, parent, false);
            }

            totalWidgetsScanned++;
        }

        log("========================================");
        log("SCAN SUMMARY:");
        log("  Total visible widget groups: " + totalVisibleGroups);
        log("  Groups containing items: " + groupsWithItems);
        log("  Total widgets scanned: " + totalWidgetsScanned);
        log("========================================");
        log(">>> SCAN COMPLETE - Toggle off 'Scan Widgets' to stop");
    }

    /**
     * Log detailed information about a widget group
     */
    private void logWidgetGroup(int groupId, Widget parent, boolean hasItems) {
        log("");
        log(">>> WIDGET GROUP " + groupId + (hasItems ? " [HAS ITEMS]" : ""));
        log("  Parent ID: " + parent.getId());
        log("  Parent Type: " + parent.getType());
        log("  Parent Text: '" + (parent.getText() != null ? parent.getText() : "") + "'");
        log("  Parent Name: '" + parent.getName() + "'");

        // Log children
        Widget[] children = parent.getChildren();
        if (children == null) {
            log("  Children: NULL");
        } else if (children.length == 0) {
            log("  Children: EMPTY (length=0)");
        } else {
            log("  Children: " + children.length + " total");
            logChildren(children, "Children");
        }

        // Log dynamic children
        Widget[] dynamicChildren = parent.getDynamicChildren();
        if (dynamicChildren != null && dynamicChildren.length > 0) {
            log("  Dynamic Children: " + dynamicChildren.length + " total");
            logChildren(dynamicChildren, "DynamicChildren");
        }

        // Log static children
        Widget[] staticChildren = parent.getStaticChildren();
        if (staticChildren != null && staticChildren.length > 0) {
            log("  Static Children: " + staticChildren.length + " total");
            logChildren(staticChildren, "StaticChildren");
        }

        // Log nested children (for deeply nested widgets)
        if (config.logNestedWidgets()) {
            // Check children for nested widgets
            if (children != null) {
                for (int i = 0; i < children.length && i < 5; i++) {
                    Widget child = children[i];
                    if (child != null && child.getChildren() != null && child.getChildren().length > 0) {
                        log("  Child [" + i + "] has " + child.getChildren().length + " nested children");
                        logChildren(child.getChildren(), "NestedChildren[" + i + "]");
                    }
                }
            }

            // Check static children for nested widgets
            if (staticChildren != null) {
                for (int i = 0; i < staticChildren.length && i < 10; i++) {
                    Widget child = staticChildren[i];
                    if (child != null) {
                        Widget[] nested = child.getChildren();
                        if (nested != null && nested.length > 0) {
                            log("  StaticChild [" + i + "] has " + nested.length + " nested children");
                            logChildren(nested, "StaticChild[" + i + "].Children");

                            // Go one more level deep for container widgets (Type 3)
                            for (int j = 0; j < nested.length && j < 5; j++) {
                                Widget nestedChild = nested[j];
                                if (nestedChild != null && nestedChild.getType() == 3) {
                                    Widget[] deepNested = nestedChild.getChildren();
                                    if (deepNested != null && deepNested.length > 0) {
                                        log("    StaticChild [" + i + "].Children[" + j + "] has " + deepNested.length + " deeply nested children");
                                        logChildren(deepNested, "StaticChild[" + i + "].Children[" + j + "].Children");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Log array of child widgets with detailed information
     */
    private void logChildren(Widget[] children, String label) {
        int visibleCount = 0;
        int itemCount = 0;
        int maxToLog = config.maxWidgetsToLog();

        for (int i = 0; i < children.length; i++) {
            Widget child = children[i];

            if (child == null) {
                continue;
            }

            boolean hidden = child.isHidden();
            boolean hasItem = child.getItemId() > 0;

            if (!hidden) {
                visibleCount++;

                if (hasItem) {
                    itemCount++;
                }

                // Only log up to maxToLog visible widgets, prioritize ones with items
                if ((hasItem || visibleCount <= maxToLog)) {
                    logChildWidget(child, i, label);
                }
            }
        }

        log("    [" + label + " Summary] Visible: " + visibleCount + "/" + children.length +
            " | Items: " + itemCount);
    }

    /**
     * Log a single child widget with all its properties
     */
    private void logChildWidget(Widget child, int index, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("    [").append(label).append(" ").append(index).append("] ");
        sb.append("ID=").append(child.getId());

        // Item information (most important for cargo hold)
        if (child.getItemId() > 0) {
            sb.append(" | ItemID=").append(child.getItemId());
            sb.append(" | Qty=").append(child.getItemQuantity());
        }

        // Text content
        String text = child.getText();
        if (text != null && !text.isEmpty() && text.length() <= 100) {
            sb.append(" | Text='").append(text).append("'");
        }

        // Widget type
        sb.append(" | Type=").append(child.getType());

        // Actions available
        String[] actions = child.getActions();
        if (actions != null && actions.length > 0) {
            sb.append(" | Actions=").append(java.util.Arrays.toString(actions));
        }

        // Position and size (useful for clickable widgets)
        if (config.logWidgetPosition()) {
            sb.append(" | Pos=(").append(child.getOriginalX()).append(",").append(child.getOriginalY()).append(")");
            sb.append(" | Size=(").append(child.getWidth()).append("x").append(child.getHeight()).append(")");
        }

        // Name
        if (child.getName() != null && !child.getName().isEmpty()) {
            sb.append(" | Name='").append(child.getName()).append("'");
        }

        log(sb.toString());
    }
}
