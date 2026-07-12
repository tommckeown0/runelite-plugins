package com.example.PrayerFlickDiagnostic;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@PluginDescriptor(
        name = "A Prayer Flick Diagnostic",
        description = "Logs NPC attack animations, projectiles, and hitsplats with tick numbers to understand prayer flick timing",
        tags = {"prayer", "flick", "diagnostic", "debug"},
        enabledByDefault = false
)
public class PrayerFlickDiagnosticPlugin extends Plugin {

    // Spiritual mage 3161; K'ril minions: Tstanon Karlak (melee) 3130, Zakl'n Gritch (ranged) 3131, Balfrug Kreeyath (magic) 3132.
    // Tormented demon (Ghorrock temple) 13599, 13600.
    // Demonic gorilla (Crash Site Cavern) - 8 cosmetic-variant ids, all the same monster.
    private static final Set<Integer> TRACKED_IDS = Set.of(3161, 3130, 3131, 3132, 13599, 13600,
            7144, 7145, 7146, 7147, 7148, 7149, 7152);

    // GraphicsObjectCreated has no source-actor link, so we can't filter it by TRACKED_IDS -
    // instead log anything spawning within this many tiles of the player, on the assumption the
    // boss room has little unrelated visual noise (used to catch the gorilla boulder's warning
    // shadow, which has no NPC animation or projectile of its own per the wiki).
    private static final int GRAPHICS_OBJECT_LOG_RADIUS = 15;

    @Inject
    private Client client;

    private final Map<Projectile, Integer> seenProjectiles = new IdentityHashMap<>();

    @Override
    protected void startUp() {
        seenProjectiles.clear();
        System.out.println("[PFDiag] started — watching NPC IDs: " + TRACKED_IDS);
    }

    @Override
    protected void shutDown() {
        seenProjectiles.clear();
        System.out.println("[PFDiag] stopped");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        seenProjectiles.keySet().removeIf(p -> p.getRemainingCycles() <= 0);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) actor;
        if (!TRACKED_IDS.contains(npc.getId())) {
            return;
        }
        int anim = npc.getAnimation();
        String prayerState = activePrayerSummary();
        if (anim == -1) {
            System.out.println("[PFDiag][Tick " + client.getTickCount() + "] ANIM_RESET: NPC " + npc.getId()
                    + " (idx=" + npc.getIndex() + ") returned to idle | prayers=" + prayerState);
            return;
        }
        System.out.println("[PFDiag][Tick " + client.getTickCount() + "] ANIM: NPC " + npc.getId()
                + " (idx=" + npc.getIndex() + ") animation=" + anim
                + " | prayers=" + prayerState);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile proj = event.getProjectile();
        int remaining = proj.getRemainingCycles();
        int tick = client.getTickCount();

        if (!seenProjectiles.containsKey(proj)) {
            seenProjectiles.put(proj, tick);
            Actor target = proj.getInteracting();
            String targetDesc = target == null ? "none"
                    : (target instanceof Player ? "LOCAL_PLAYER" : "NPC:" + ((NPC) target).getId());
            boolean targetIsMe = target == client.getLocalPlayer();

            Actor source = proj.getSourceActor();
            String sourceDesc = source == null ? "unknown"
                    : (source instanceof NPC ? "NPC:" + ((NPC) source).getId() + "(idx=" + ((NPC) source).getIndex() + ")" : "player");
            WorldPoint targetPoint = proj.getTarget() == null ? null
                    : WorldPoint.fromLocal(client, proj.getTarget().getX(), proj.getTarget().getY(), proj.getFloor());

            // Ground-targeted projectile (no Actor target) fired by a tracked NPC — matches the
            // tormented demon fire bomb pattern (two of these + a player-targeted bind projectile).
            boolean groundTargetedFromTrackedNpc = target == null && source instanceof NPC && TRACKED_IDS.contains(((NPC) source).getId());

            System.out.println("[PFDiag][Tick " + tick + "] PROJ_NEW: id=" + proj.getId()
                    + " source=" + sourceDesc
                    + " target=" + targetDesc
                    + " targetPoint=" + targetPoint
                    + " remainingCycles=" + remaining
                    + " (~" + (remaining / 30) + " ticks)"
                    + (targetIsMe ? " <<< TARGETING YOU" : "")
                    + (groundTargetedFromTrackedNpc ? " <<< GROUND_TARGETED (possible fire bomb)" : ""));
        }

        if (proj.getInteracting() == client.getLocalPlayer() && remaining <= 30) {
            System.out.println("[PFDiag][Tick " + tick + "] PROJ_LANDING: id=" + proj.getId()
                    + " remainingCycles=" + remaining
                    + " | prayers=" + activePrayerSummary());
        }
    }

    // Candidate detector for the demonic gorilla boulder: wiki says a shadow appears at the
    // player's position when the attack is initiated, ~2 ticks before it lands, with no
    // dedicated NPC animation or projectile of its own. GraphicsObject is the most likely event
    // type for a ground-anchored visual warning like this.
    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }
        GraphicsObject obj = event.getGraphicsObject();
        LocalPoint lp = obj.getLocation();
        if (lp == null) {
            return;
        }
        WorldPoint objPoint = WorldPoint.fromLocal(client, lp.getX(), lp.getY(), obj.getZ());
        WorldPoint playerPoint = player.getWorldLocation();
        int distance = objPoint.distanceTo(playerPoint);
        if (distance > GRAPHICS_OBJECT_LOG_RADIUS) {
            return;
        }
        System.out.println("[PFDiag][Tick " + client.getTickCount() + "] GFX_OBJECT: id=" + obj.getId()
                + " at=" + objPoint
                + " distanceFromPlayer=" + distance
                + " onPlayerTile=" + objPoint.equals(playerPoint)
                + " startCycle=" + obj.getStartCycle()
                + " | prayers=" + activePrayerSummary());
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        Actor actor = event.getActor();
        boolean isTrackedNpc = actor instanceof NPC && TRACKED_IDS.contains(((NPC) actor).getId());
        boolean isLocalPlayer = actor == client.getLocalPlayer();
        if (!isTrackedNpc && !isLocalPlayer) {
            return;
        }

        int graphic = actor.getGraphic();
        String who = isLocalPlayer ? "LOCAL_PLAYER" : "NPC:" + ((NPC) actor).getId() + "(idx=" + ((NPC) actor).getIndex() + ")";
        System.out.println("[PFDiag][Tick " + client.getTickCount() + "] GRAPHIC: " + who
                + " graphic=" + graphic + " | prayers=" + activePrayerSummary());
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }
        Hitsplat hit = event.getHitsplat();
        System.out.println("[PFDiag][Tick " + client.getTickCount() + "] HIT: amount=" + hit.getAmount()
                + " type=" + hit.getHitsplatType()
                + " | prayers=" + activePrayerSummary());
    }

    private String activePrayerSummary() {
        StringBuilder sb = new StringBuilder();
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))    sb.append("MELEE ");
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))    sb.append("MAGIC ");
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)) sb.append("RANGED ");
        return sb.length() == 0 ? "none" : sb.toString().trim();
    }
}
