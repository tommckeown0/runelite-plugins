package com.example;

import com.example.CrashedStar.CrashedStarPlugin;
import com.example.DemonicGorilla.DemonicGorillaPlugin;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.GearSwitcher.GearSwitcherPlugin;
import com.example.PacketUtils.PacketUtilsPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(
                EthanApiPlugin.class,
                PacketUtilsPlugin.class,
                CrashedStarPlugin.class,
                DemonicGorillaPlugin.class,
                GearSwitcherPlugin.class
        );
        RuneLite.main(args);
    }
}
