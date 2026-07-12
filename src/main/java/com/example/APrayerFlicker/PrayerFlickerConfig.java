package com.example.APrayerFlicker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

import java.awt.event.KeyEvent;

@ConfigGroup("prayerflicker")
public interface PrayerFlickerConfig extends Config {

    @ConfigItem(
            keyName = "toggleHotkey",
            name = "Toggle Hotkey",
            description = "Press this key to enable/disable auto prayer flicking on the fly",
            position = 0
    )
    default Keybind toggleHotkey() {
        return new Keybind(KeyEvent.VK_F7, 0);
    }
}
