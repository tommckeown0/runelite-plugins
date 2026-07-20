package com.example.AImpKillerGWD;

import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.InteractionApi.MenuActionInteractions;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Optional;

@PluginDescriptor(
        name = "A Imp Killer GWD",
        description = "Kills imps to farm Zamorak GWD kill count. World hop manually when they're all dead.",
        tags = {"gwd", "zamorak", "imp", "kills"},
        enabledByDefault = false
)
public class AImpKillerGWDPlugin extends Plugin {

    private static final int IMP_ID = 3134;

    @Inject
    private Client client;

    @Inject
    private AImpKillerGWDConfig config;

    private int tickDelay;
    private int killCount;
    private boolean loggedAllDead;

    @Provides
    AImpKillerGWDConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AImpKillerGWDConfig.class);
    }

    @Override
    protected void startUp() {
        tickDelay = 0;
        killCount = 0;
        loggedAllDead = false;
        log("ImpKillerGWD started");
    }

    @Override
    protected void shutDown() {
        System.out.println("[ImpKillerGWD] Stopped. Session kills: " + killCount);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            // Reset after a manual world hop
            loggedAllDead = false;
            tickDelay = 3;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        NPC npc = event.getNpc();
        if (npc.getId() == IMP_ID && npc.getHealthRatio() == 0) {
            killCount++;
            log("Imp killed. Session total: " + killCount
                    + (config.targetKillCount() > 0 ? "/" + config.targetKillCount() : ""));
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        int target = config.targetKillCount();
        if (target > 0 && killCount >= target) {
            log("Target of " + target + " kills reached — you can enter the Zamorak GWD room.");
            return;
        }

        Optional<NPC> imp = NPCs.search().withId(IMP_ID).alive().nearestToPlayer();

        if (!imp.isPresent()) {
            if (!loggedAllDead) {
                System.out.println("[ImpKillerGWD] All imps dead — hop worlds manually. Session kills: " + killCount);
                loggedAllDead = true;
            }
            return;
        }

        loggedAllDead = false;

        if (isInCombatWithImp()) {
            return;
        }

        attackNearestImp();
        tickDelay = 3;
    }

    private boolean isInCombatWithImp() {
        Actor interacting = client.getLocalPlayer().getInteracting();
        if (!(interacting instanceof NPC)) {
            return false;
        }
        NPC npc = (NPC) interacting;
        return npc.getId() == IMP_ID && npc.getHealthRatio() != 0;
    }

    private void attackNearestImp() {
        Player local = client.getLocalPlayer();

        // Prefer an imp already attacking us
        Optional<NPC> target = NPCs.search()
                .withId(IMP_ID)
                .alive()
                .filter(npc -> npc.getInteracting() == local)
                .nearestToPlayer();

        if (!target.isPresent()) {
            target = NPCs.search().withId(IMP_ID).alive().nearestToPlayer();
        }

        if (!target.isPresent()) {
            return;
        }

        NPC npc = target.get();
        log("Attacking imp index=" + npc.getIndex());
        MenuActionInteractions.interactNpc(npc, "Attack");
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[ImpKillerGWD] " + message);
        }
    }
}
