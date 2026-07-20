package com.example.APrayerFlicker;

import com.example.InteractionApi.PrayerInteraction;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@PluginDescriptor(
        name = "A Prayer Flicker",
        description = "Prayer flicks against GWD minions",
        tags = {"prayer", "flick", "gwd"},
        enabledByDefault = false
)
public class PrayerFlickerPlugin extends Plugin {

    // Priority: higher = protect this style first when attacks overlap.
    // Melee (3) > Magic (2) > Ranged (1) — most damaging to least.
    // Attack animation IDs for K'ril minions are TBD; fill in after running diagnostic in the room.
    private enum NpcSpec {
        ZAKLNG_GRITCH   (3131, Prayer.PROTECT_FROM_MISSILES, 5, 3, Set.of(7077)),
        TSTANON_KARLAK  (3130, Prayer.PROTECT_FROM_MELEE,    5, 2, Set.of(64)),
        BALFRUG_KREEYATH(3132, Prayer.PROTECT_FROM_MAGIC,    5, 1, Set.of(4630)),
        SPIRITUAL_MAGE  (3161, Prayer.PROTECT_FROM_MAGIC,    4, 2, Set.of(811));

        final int npcId;
        final Prayer prayer;
        final int attackSpeedTicks;
        final int priority;
        final Set<Integer> attackAnimations;

        NpcSpec(int npcId, Prayer prayer, int attackSpeedTicks, int priority, Set<Integer> attackAnimations) {
            this.npcId = npcId;
            this.prayer = prayer;
            this.attackSpeedTicks = attackSpeedTicks;
            this.priority = priority;
            this.attackAnimations = attackAnimations;
        }

        static NpcSpec forId(int id) {
            for (NpcSpec s : values()) {
                if (s.npcId == id) return s;
            }
            return null;
        }
    }

    private static class NpcTracker {
        final NpcSpec spec;
        int nextExpectedAttack = -1;

        NpcTracker(NpcSpec spec) {
            this.spec = spec;
        }
    }

    @Inject private Client client;
    @Inject private PrayerFlickerConfig config;
    @Inject private KeyManager keyManager;

    private final Map<Integer, NpcTracker> trackers = new HashMap<>();

    private boolean flickEnabled = true;
    private int prayerActivatedTick = -1;
    private Prayer activatedPrayer = null;

    private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            flickEnabled = !flickEnabled;
            System.out.println("[PrayerFlicker] flicking " + (flickEnabled ? "ENABLED" : "DISABLED"));
        }
    };

    @Provides
    PrayerFlickerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PrayerFlickerConfig.class);
    }

    @Override
    protected void startUp() {
        trackers.clear();
        prayerActivatedTick = -1;
        activatedPrayer = null;
        flickEnabled = true;
        keyManager.registerKeyListener(toggleListener);
        System.out.println("[PrayerFlicker] started — press " + config.toggleHotkey() + " to toggle flicking");
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(toggleListener);
        trackers.clear();
        prayerActivatedTick = -1;
        activatedPrayer = null;
        System.out.println("[PrayerFlicker] stopped");
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        trackers.remove(event.getNpc().getIndex());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        int tick = client.getTickCount();

        // Drop trackers whose expected attack is overdue by a full cycle (NPC reset or left area).
        trackers.entrySet().removeIf(e -> {
            NpcTracker t = e.getValue();
            return t.nextExpectedAttack >= 0 && tick > t.nextExpectedAttack + t.spec.attackSpeedTicks;
        });

        if (!flickEnabled) {
            return;
        }

        // Find the highest-priority NPC attacking next tick and pre-activate that prayer.
        NpcSpec best = null;
        for (NpcTracker tracker : trackers.values()) {
            if (tracker.nextExpectedAttack < 0) continue;
            if (tick != tracker.nextExpectedAttack - 1) continue;
            if (best == null || tracker.spec.priority > best.priority) {
                best = tracker.spec;
            }
        }

        if (best != null) {
            System.out.println("[PrayerFlicker][Tick " + tick + "] PRE_ACTIVATE " + best.prayer
                    + " for " + best.name() + " attacking at " + (tick + 1));
            PrayerInteraction.setPrayerState(best.prayer, true);
            prayerActivatedTick = tick;
            activatedPrayer = best.prayer;
        }

        // Deactivate 1 tick after activation (server-side prayer check has already happened).
        // Pre-activation above sets prayerActivatedTick = tick, so tick > tick is false —
        // this correctly skips deactivation on the same tick we activated.
        if (activatedPrayer != null && prayerActivatedTick >= 0 && tick > prayerActivatedTick) {
            System.out.println("[PrayerFlicker][Tick " + tick + "] FLICK_OFF " + activatedPrayer);
            PrayerInteraction.setPrayerState(activatedPrayer, false);
            prayerActivatedTick = -1;
            activatedPrayer = null;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) actor;
        NpcSpec spec = NpcSpec.forId(npc.getId());
        if (spec == null) {
            return;
        }
        int anim = npc.getAnimation();
        if (!spec.attackAnimations.contains(anim)) {
            return;
        }

        int tick = client.getTickCount();
        NpcTracker tracker = trackers.computeIfAbsent(npc.getIndex(), i -> new NpcTracker(spec));
        boolean predicted = tracker.nextExpectedAttack >= 0 && tick == tracker.nextExpectedAttack;

        System.out.println("[PrayerFlicker][Tick " + tick + "] ATTACK " + spec.name()
                + " anim=" + anim + " idx=" + npc.getIndex()
                + (predicted ? " (predicted)" : " (first/drift — next will be blocked)"));

        if (!predicted && flickEnabled) {
            // Can't block this one due to 1-tick activation lag, but activate as fallback
            // and establish the cycle so subsequent attacks are predicted.
            PrayerInteraction.setPrayerState(spec.prayer, true);
            prayerActivatedTick = tick;
            activatedPrayer = spec.prayer;
        }

        tracker.nextExpectedAttack = tick + spec.attackSpeedTicks;
    }
}
