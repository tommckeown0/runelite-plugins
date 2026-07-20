package com.example.FreezeTimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.*;

@ConfigGroup("FreezeTimer")
public interface FreezeTimerConfig extends Config {

    @ConfigSection(
            name = "Display Settings",
            description = "Configure timer display options",
            position = 0
    )
    String displaySection = "display";

    @ConfigItem(
            keyName = "enableWorldOverlay",
            name = "Show Timers Above NPCs",
            description = "Display freeze timers above frozen NPC heads in the game world",
            section = displaySection
    )
    default boolean enableWorldOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "enableSidePanel",
            name = "Show Side Panel",
            description = "Display freeze timer list in a side panel",
            section = displaySection
    )
    default boolean enableSidePanel() {
        return true;
    }

    @ConfigItem(
            keyName = "showSpellName",
            name = "Show Spell Name",
            description = "Display the freeze spell name along with timer",
            section = displaySection
    )
    default boolean showSpellName() {
        return false;
    }

    @ConfigItem(
            keyName = "fontSize",
            name = "Font Size",
            description = "Font size for timer text",
            section = displaySection
    )
    default int fontSize() {
        return 14;
    }

    @ConfigSection(
            name = "Color Settings",
            description = "Customize timer colors",
            position = 1
    )
    String colorSection = "color";

    @ConfigItem(
            keyName = "useGradientColors",
            name = "Use Gradient Colors",
            description = "Change timer color from green → yellow → red as time runs out",
            section = colorSection
    )
    default boolean useGradientColors() {
        return true;
    }

    @ConfigItem(
            keyName = "timerColor",
            name = "Timer Color (Fixed)",
            description = "Timer color when gradient is disabled",
            section = colorSection
    )
    default Color timerColor() {
        return Color.CYAN;
    }

    @ConfigItem(
            keyName = "shadowColor",
            name = "Text Shadow Color",
            description = "Color for text shadow (improves readability)",
            section = colorSection
    )
    default Color shadowColor() {
        return Color.BLACK;
    }

    @ConfigSection(
            name = "Advanced",
            description = "Advanced settings",
            position = 2
    )
    String advancedSection = "advanced";

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Enable debug logging to console",
            section = advancedSection
    )
    default boolean debugLogging() {
        return false;
    }

    @ConfigItem(
            keyName = "cleanupInterval",
            name = "Cleanup Interval (ticks)",
            description = "How often to clean up expired freeze timers",
            section = advancedSection
    )
    default int cleanupInterval() {
        return 5;
    }

    @ConfigItem(
            keyName = "maxRenderDistance",
            name = "Max Render Distance",
            description = "Maximum distance (tiles) to render freeze timers",
            section = advancedSection
    )
    default int maxRenderDistance() {
        return 32;
    }
}
