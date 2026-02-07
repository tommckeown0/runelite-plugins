package com.example;

import com.example.ANewYews.ANewYewsPlugin;
import com.example.BarbarianFishing.BarbarianFishingPlugin;
import com.example.CookingMonkfish.CookingMonkfishPlugin;
import com.example.CrashedStar.CrashedStarPlugin;
import com.example.DemonicGorilla.DemonicGorillaPlugin;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.FreezeTimer.FreezeTimerPlugin;
import com.example.GearSwitcher.GearSwitcherPlugin;
import com.example.GreenDhideBodies.GreenDhideBodiesPlugin;
import com.example.Hunllef.HunllefPlugin;
import com.example.MotherlodeMine.MotherlodeMinePlugin;
import com.example.NewTrees.NewTreesPlugin;
import com.example.PacketUtils.PacketUtilsPlugin;
import com.example.Prayer.PrayerPlugin;
import com.example.RooftopAgility.RooftopAgilityPlugin;
import com.example.SalamanderHunting.SalamanderHuntingPlugin;
import com.example.Salvaging.SimpleSalvagingPlugin;
import com.example.Testing.TestingPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(
                EthanApiPlugin.class,
                PacketUtilsPlugin.class,
                CrashedStarPlugin.class,
                DemonicGorillaPlugin.class,
                GearSwitcherPlugin.class,
                MotherlodeMinePlugin.class,
                TestingPlugin.class,
                BarbarianFishingPlugin.class,
                NewTreesPlugin.class,
                PrayerPlugin.class,
                FreezeTimerPlugin.class,
                SimpleSalvagingPlugin.class,
                SalamanderHuntingPlugin.class,
                HunllefPlugin.class,
                ANewYewsPlugin.class,
                CookingMonkfishPlugin.class,
                GreenDhideBodiesPlugin.class,
                RooftopAgilityPlugin.class
        );
        RuneLite.main(args);
    }
}
