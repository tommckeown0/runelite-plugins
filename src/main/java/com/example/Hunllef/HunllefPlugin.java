package com.example.Hunllef;

import com.example.EthanApiPlugin.Collections.Equipment;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.InteractionApi.InventoryInteraction;
import com.example.InteractionApi.PrayerInteraction;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Optional;
import java.util.Set;

@PluginDescriptor(
        name = "A Hunllef Prayer Helper",
        description = "Crystalline/Corrupted Hunllef Helper",
        tags = {"hunllef", "gauntlet", "prayer", "combat"},
        hidden = false
)
public class HunllefPlugin extends Plugin {

    // Hunllef animation IDs for style switches
    private static final int HUNLLEF_SWITCH_TO_MAGIC = 8754;
    private static final int HUNLLEF_SWITCH_TO_RANGED = 8755;

    // All Hunllef NPC IDs
    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            NpcID.CORRUPTED_HUNLLEF, NpcID.CORRUPTED_HUNLLEF_9036,
            NpcID.CORRUPTED_HUNLLEF_9037, NpcID.CORRUPTED_HUNLLEF_9038,
            NpcID.CRYSTALLINE_HUNLLEF, NpcID.CRYSTALLINE_HUNLLEF_9022,
            NpcID.CRYSTALLINE_HUNLLEF_9023, NpcID.CRYSTALLINE_HUNLLEF_9024
    );

    // Gauntlet varbits
    private static final int VARBIT_IN_GAUNTLET = 9177;
    private static final int VARBIT_HUNLLEF_FIGHT = 9178;

    @Inject
    private Client client;

    @Inject
    private HunllefConfig config;

    // Current attack style (true = ranged, false = magic)
    // Hunllef always starts with ranged
    private boolean isRangedStyle = true;

    // Track the expected prayer for restoration
    private Prayer expectedPrayer = Prayer.PROTECT_FROM_MISSILES;

    @Provides
    HunllefConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HunllefConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[Hunllef] " + message);
        }
    }

    @Override
    protected void startUp() {
        log("Hunllef Prayer Helper started");
        resetState();
    }

    @Override
    protected void shutDown() {
        log("Hunllef Prayer Helper stopped");
        resetState();
    }

    private void resetState() {
        isRangedStyle = true;
        expectedPrayer = Prayer.PROTECT_FROM_MISSILES;
    }

    private boolean isInHunllefFight() {
        return client.getVarbitValue(VARBIT_HUNLLEF_FIGHT) == 1;
    }

    private NPC findHunllef() {
        return client.getNpcs().stream()
                .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Reset state when not in Hunllef fight
        if (!isInHunllefFight()) {
            if (isRangedStyle == false || expectedPrayer != Prayer.PROTECT_FROM_MISSILES) {
                log("Left Hunllef fight, resetting state");
                resetState();
            }
            return;
        }

        // Re-enable prayers if they got turned off
        if (config.enablePrayerRestore()) {
            restorePrayerIfDisabled();
        }

        NPC hunllef = findHunllef();
        if (hunllef == null || hunllef.isDead() || hunllef.getHealthRatio() == 0) {
            return;
        }

        // Handle weapon switching based on Hunllef's overhead prayer
        if (config.enableWeaponSwitching()) {
            handleWeaponSwitching(hunllef);
        }

        // Handle offensive prayer switching based on equipped weapon
        if (config.enableOffensivePrayers()) {
            handleOffensivePrayers();
        }
    }

    private String getEquippedWeaponType() {
        if (!Equipment.search().matchesWildCardNoCase("*staff*").empty()) {
            return "staff";
        }
        if (!Equipment.search().matchesWildCardNoCase("*bow*").empty()) {
            return "bow";
        }
        if (!Equipment.search().matchesWildCardNoCase("*halberd*").empty()) {
            return "halberd";
        }
        return "";
    }

    private void handleWeaponSwitching(NPC hunllef) {
        HeadIcon headIcon = EthanApiPlugin.getHeadIcon(hunllef);
        String equippedWeapon = getEquippedWeaponType();

        log("HeadIcon: " + headIcon + ", Equipped: " + equippedWeapon);

        if (headIcon == null) {
            return;
        }

        // If Hunllef is praying Magic, switch to bow or halberd
        if (headIcon == HeadIcon.MAGIC && !equippedWeapon.equals("bow") && !equippedWeapon.equals("halberd")) {
            Optional<Widget> bow = Inventory.search().matchesWildCardNoCase("*bow*").first();
            Optional<Widget> halberd = Inventory.search().matchesWildCardNoCase("*halberd*").first();
            if (bow.isPresent()) {
                InventoryInteraction.useItem(bow.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Magic - switching to bow");
            } else if (halberd.isPresent()) {
                InventoryInteraction.useItem(halberd.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Magic - switching to halberd");
            }
        }

        // If Hunllef is praying Ranged, switch to staff or halberd
        if (headIcon == HeadIcon.RANGED && !equippedWeapon.equals("staff") && !equippedWeapon.equals("halberd")) {
            Optional<Widget> staff = Inventory.search().matchesWildCardNoCase("*staff*").first();
            Optional<Widget> halberd = Inventory.search().matchesWildCardNoCase("*halberd*").first();
            if (staff.isPresent()) {
                InventoryInteraction.useItem(staff.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Ranged - switching to staff");
            } else if (halberd.isPresent()) {
                InventoryInteraction.useItem(halberd.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Ranged - switching to halberd");
            }
        }

        // If Hunllef is praying Melee, switch to staff or bow
        if (headIcon == HeadIcon.MELEE && !equippedWeapon.equals("staff") && !equippedWeapon.equals("bow")) {
            Optional<Widget> staff = Inventory.search().matchesWildCardNoCase("*staff*").first();
            Optional<Widget> bow = Inventory.search().matchesWildCardNoCase("*bow*").first();
            if (staff.isPresent()) {
                InventoryInteraction.useItem(staff.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Melee - switching to staff");
            } else if (bow.isPresent()) {
                InventoryInteraction.useItem(bow.get(), "Equip", "Wear", "Wield");
                log("Hunllef praying Melee - switching to bow");
            }
        }
    }

    private void handleOffensivePrayers() {
        String equippedWeapon = getEquippedWeaponType();

        if (equippedWeapon.equals("bow")) {
            // Use Rigour if unlocked, otherwise Eagle Eye
            if (rigourUnlocked()) {
                if (!client.isPrayerActive(Prayer.RIGOUR)) {
                    PrayerInteraction.setPrayerState(Prayer.RIGOUR, true);
                    log("Bow equipped - activating Rigour");
                }
            } else {
                if (!client.isPrayerActive(Prayer.EAGLE_EYE)) {
                    PrayerInteraction.setPrayerState(Prayer.EAGLE_EYE, true);
                    log("Bow equipped - activating Eagle Eye");
                }
            }
        } else if (equippedWeapon.equals("staff")) {
            // Use Augury if unlocked, otherwise Mystic Might
            if (auguryUnlocked()) {
                if (!client.isPrayerActive(Prayer.AUGURY)) {
                    PrayerInteraction.setPrayerState(Prayer.AUGURY, true);
                    log("Staff equipped - activating Augury");
                }
            } else {
                if (!client.isPrayerActive(Prayer.MYSTIC_MIGHT)) {
                    PrayerInteraction.setPrayerState(Prayer.MYSTIC_MIGHT, true);
                    log("Staff equipped - activating Mystic Might");
                }
            }
        } else if (equippedWeapon.equals("halberd")) {
            // Use Piety if unlocked, otherwise Ultimate Strength
            if (pietyUnlocked()) {
                if (!client.isPrayerActive(Prayer.PIETY)) {
                    PrayerInteraction.setPrayerState(Prayer.PIETY, true);
                    log("Halberd equipped - activating Piety");
                }
            } else {
                if (!client.isPrayerActive(Prayer.ULTIMATE_STRENGTH)) {
                    PrayerInteraction.setPrayerState(Prayer.ULTIMATE_STRENGTH, true);
                    log("Halberd equipped - activating Ultimate Strength");
                }
            }
        }
    }

    private boolean rigourUnlocked() {
        return client.getVarbitValue(5451) != 0
                && client.getRealSkillLevel(Skill.PRAYER) >= 74
                && client.getRealSkillLevel(Skill.DEFENCE) >= 70;
    }

    private boolean auguryUnlocked() {
        return client.getVarbitValue(5452) != 0
                && client.getRealSkillLevel(Skill.PRAYER) >= 77
                && client.getRealSkillLevel(Skill.DEFENCE) >= 70;
    }

    private boolean pietyUnlocked() {
        return client.getVarbitValue(3909) == 8
                && client.getRealSkillLevel(Skill.PRAYER) >= 70
                && client.getRealSkillLevel(Skill.DEFENCE) >= 70;
    }

    private void restorePrayerIfDisabled() {
        if (!client.isPrayerActive(expectedPrayer)) {
            log("Prayer was disabled! Re-enabling " + expectedPrayer);
            PrayerInteraction.setPrayerState(expectedPrayer, true);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        try {
            if (!config.enablePrayerSwitching() || client.getGameState() != GameState.LOGGED_IN) {
                return;
            }

            if (!(event.getActor() instanceof NPC)) {
                return;
            }

            NPC npc = (NPC) event.getActor();

            if (!HUNLLEF_IDS.contains(npc.getId())) {
                return;
            }

            int animation = npc.getAnimation();

            // Detect style switch animations
            if (animation == HUNLLEF_SWITCH_TO_MAGIC) {
                isRangedStyle = false;
                expectedPrayer = Prayer.PROTECT_FROM_MAGIC;
                log("Hunllef switched to MAGIC! Activating Protect from Magic");
                PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MAGIC, true);
            } else if (animation == HUNLLEF_SWITCH_TO_RANGED) {
                isRangedStyle = true;
                expectedPrayer = Prayer.PROTECT_FROM_MISSILES;
                log("Hunllef switched to RANGED! Activating Protect from Missiles");
                PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MISSILES, true);
            }

        } catch (Exception e) {
            log("Exception in onAnimationChanged: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
