package com.example.Testing;

import com.example.EthanApiPlugin.Collections.DepositBox;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import com.example.Testing.OreEntry;
import net.runelite.client.plugins.PluginDescriptor;

import static com.example.MotherlodeMine.MotherlodeMineConstants.*;

@PluginDescriptor(
        name = "A Testing",
        description = "Testing plugin",
        tags = {"testing"},
        hidden = false,
        enabledByDefault = false
)

public class TestingPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private TestingPluginConfig config;

    public static final int GOLD_ORE_ID = 444;
    public static final int MITHRIL_ORE_ID = 447;
    public static final int ADAMANTITE_ORE_ID = 449;
    public static final int COAL_ID = 453;
    public static final int GOLD_NUGGET_ID = 12012;

    private static final OreEntry[] ORES = {
            new OreEntry(GOLD_ORE_ID, "Gold ore"),
            new OreEntry(MITHRIL_ORE_ID, "Mithril ore"),
            new OreEntry(ADAMANTITE_ORE_ID, "Adamantite ore"),
            new OreEntry(COAL_ID, "Coal"),
            new OreEntry(GOLD_NUGGET_ID, "Gold nugget")
    };

    @Override
    protected void startUp() throws Exception
    {
        System.out.println("TestingPlugin started!");
    }

    @Override
    protected void shutDown() throws Exception
    {
        System.out.println("TestingPlugin stopped!");
    }

    @Provides
    TestingPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TestingPluginConfig.class);
    }

    private boolean hasOres() {
        return Inventory.search().withId(GOLD_ORE_ID).first().isPresent() ||
                Inventory.search().withId(MITHRIL_ORE_ID).first().isPresent() ||
                Inventory.search().withId(ADAMANTITE_ORE_ID).first().isPresent() ||
                Inventory.search().withId(COAL_ID).first().isPresent() ||
                Inventory.search().withId(GOLD_NUGGET_ID).first().isPresent();
    }

    private void depositItems(){
//        Widget goldOre = DepositBox.search().withId(444).first().orElse(null);
//        if (goldOre != null) {
//            System.out.println("Gold ore is not null");
//            MousePackets.queueClickPacket();
//            WidgetPackets.queueWidgetAction(goldOre, "Deposit-All");
//        }
        System.out.println(hasOres());
        for (OreEntry ore : ORES)
        {
            Widget widget = DepositBox.search()
                    .withId(ore.id)
                    .first()
                    .orElse(null);

            if (widget != null)
            {
                System.out.println("Depositing: " + ore.name);
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(widget, "Deposit-All");
            }
        }
        System.out.println("Ores deposited");
    }

    @Subscribe
    public void onGameTick(GameTick event){
        System.out.println("Attempting to deposit gold ore");
        depositItems();
    }
}
