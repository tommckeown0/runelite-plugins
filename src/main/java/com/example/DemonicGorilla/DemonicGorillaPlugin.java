package com.example.DemonicGorilla;

import com.example.InteractionApi.PrayerInteraction;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = "A Demonic Gorilla Helper",
        description = "Loud warnings when Gorillas switch prayers",
        tags = {"demonic", "gorilla", "prayer", "combat"},
        hidden = false
)
public class DemonicGorillaPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private DemonicGorillaConfig config;

    // Track last gorilla animation to avoid spam
    private int lastGorillaAnimation = -1;

    // Track gorilla attack style counts
    private int magicAttackCount = 0;
    private int rangedAttackCount = 0;
    private int meleeAttackCount = 0;

    // Track current prayer for style switching
    private Prayer currentGorillaPrayer = null;

    @Provides
    DemonicGorillaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DemonicGorillaConfig.class);
    }

    /**
     * Conditional logging helper - only logs when debug logging is enabled in config
     */
    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[DemonicGorilla] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("Demonic Gorilla Helper started");
        resetState();
    }

    /**
     * Resets the plugin state
     */
    private void resetState() {
        lastGorillaAnimation = -1;
        magicAttackCount = 0;
        rangedAttackCount = 0;
        meleeAttackCount = 0;
        currentGorillaPrayer = null;
    }

    private Prayer getNextPrayer(Prayer current) {
        if (current == Prayer.PROTECT_FROM_MELEE) {
            return Prayer.PROTECT_FROM_MISSILES;
        }
        if (current == Prayer.PROTECT_FROM_MISSILES) {
            return Prayer.PROTECT_FROM_MAGIC;
        }
        if (current == Prayer.PROTECT_FROM_MAGIC) {
            return Prayer.PROTECT_FROM_MISSILES;
        }

        // Default if somehow null or other state
        return Prayer.PROTECT_FROM_MELEE;
    }


    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event) {
        try {
            if (!config.enablePrayerSwitching() || client.getGameState() != GameState.LOGGED_IN) {
                return;
            }

            // Check if it's an NPC
            if (!(event.getActor() instanceof NPC)) {
                return;
            }

            NPC npc = (NPC) event.getActor();

            // Check if it's a demonic gorilla
            if (npc.getName() == null || !npc.getName().toLowerCase().contains("demonic gorilla")) {
                return;
            }

            String overheadText = event.getOverheadText();
            log("Gorilla overhead text: " + overheadText);

            // Detect the style switch shout
            if (overheadText != null && overheadText.contains("Rhaaaaaaa")) {
                log("Gorilla switching styles! Current prayer: " + currentGorillaPrayer);

                // Rotate prayer
                Prayer nextPrayer = getNextPrayer(currentGorillaPrayer);
                log("Pre-emptively switching prayer to: " + nextPrayer);

                PrayerInteraction.setPrayerState(nextPrayer, true);
                currentGorillaPrayer = nextPrayer;

                // Reset attack counters when switching
                log("Resetting attack counters - Magic: " + magicAttackCount + ", Ranged: " + rangedAttackCount + ", Melee: " + meleeAttackCount);
                magicAttackCount = 0;
                rangedAttackCount = 0;
                meleeAttackCount = 0;
            }

        } catch (Exception e) {
            log("Exception in onOverheadTextChanged: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        try {
            if (!config.enablePrayerSwitching() || client.getGameState() != GameState.LOGGED_IN) {
                return;
            }

            // Check if the event is for an NPC
            if (!(event.getActor() instanceof NPC)) {
                return;
            }

            NPC npc = (NPC) event.getActor();

            // Check if it's a demonic gorilla
            if (npc.getName() == null || !npc.getName().toLowerCase().contains("demonic gorilla")) {
                return;
            }

            // Check if the gorilla is attacking the player
            if (!npc.isInteracting() || npc.getInteracting() != client.getLocalPlayer()) {
                return;
            }

            log("Gorilla animation changed - " + npc.getName() + " (ID: " + npc.getId() + ")");

            int animation = npc.getAnimation();
            log("Animation ID: " + animation);

            // Only react if animation changed (to avoid spam)
            if (animation == lastGorillaAnimation || animation == -1) {
                return;
            }

            lastGorillaAnimation = animation;

            // Magic attack - animation 7225
            if (animation == 7225) {
                magicAttackCount++;
                log("Gorilla MAGIC attack! (Total: " + magicAttackCount + ")");
                PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MAGIC, true);
                currentGorillaPrayer = Prayer.PROTECT_FROM_MAGIC;
            }
            // Ranged attack - animation 7227
            else if (animation == 7227) {
                rangedAttackCount++;
                log("Gorilla RANGED attack! (Total: " + rangedAttackCount + ")");
                PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MISSILES, true);
                currentGorillaPrayer = Prayer.PROTECT_FROM_MISSILES;
            }
            // Melee attack - animation 7226
            else if (animation == 7226) {
                meleeAttackCount++;
                log("Gorilla MELEE attack! (Total: " + meleeAttackCount + ")");
                PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MELEE, true);
                currentGorillaPrayer = Prayer.PROTECT_FROM_MELEE;
            } else {
                log("Unknown gorilla animation: " + animation);
            }

        } catch (Exception e) {
            log("Exception in onAnimationChanged: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void shutDown() {
        log("Demonic Gorilla Helper stopped");
        resetState();
    }
}
