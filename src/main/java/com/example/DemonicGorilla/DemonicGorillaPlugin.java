package com.example.DemonicGorilla;

import com.example.EthanApiPlugin.Collections.Equipment;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.GearSwitcher.GearSwitcherConfig;
import com.example.InteractionApi.MenuActionInteractions;
import com.example.InteractionApi.PrayerInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@PluginDescriptor(
        name = "A Demonic Gorilla Helper",
        description = "Reactive prayer flicking, attack-style mismatch warnings, and boulder dodging for demonic gorillas",
        tags = {"demonic", "gorilla", "prayer", "combat"},
        hidden = false
)
public class DemonicGorillaPlugin extends Plugin {

    // Confirmed from two [PFDiag] diagnostic sessions (log6.txt/log7.txt) across dozens of
    // correlated BLOCK_ME (type=12) hitsplats: each style reliably produces a block iff the
    // matching protection prayer was active at the exact tick the animation started.
    private static final int ANIM_MELEE = 7226;
    private static final int ANIM_MAGIC = 7225;
    private static final int ANIM_RANGED = 7227;

    // Generic ~1-tick pre-attack telegraph - appears before all three styles equally (confirmed:
    // seen immediately preceding melee, ranged AND magic attacks in the logs), so it carries no
    // style information and is only useful as a "something is about to happen" signal.
    private static final int ANIM_PRE_ATTACK_TELEGRAPH = 7224;

    // Chest-beat animation. Originally used as the boulder trigger, but it fires ~3x more often
    // than real boulders (it seems to double as a windup/flex shared with normal attacks), which
    // caused constant false dodges - log-only now, the projectile below is the real signal.
    private static final int ANIM_CHEST_BEAT = 7228;

    // THE actual boulder, confirmed across log7/log8/log9: a ground-targeted projectile (no Actor
    // target) with this id spawns ~1 tick after the chest-beat, remainingCycles=135 (~4.5 ticks),
    // and every genuine boulder hit (36/25/24 damage samples) came exactly at its landing tick on
    // exactly its target tile - while none of the false-positive chest-beats spawned one. It has
    // no source actor (the boulder falls from the cavern ceiling), which is why the earlier
    // "ground-targeted projectile fired by a gorilla" diagnostic filter missed it. Its targetPoint
    // is the exact danger tile and its remainingCycles give the exact landing tick, so detection,
    // tile-marking and expiry all key off it directly.
    private static final int BOULDER_PROJECTILE_ID = 856;

    // Other players' gorillas drop 856 boulders too (Crash Site Cavern is shared - log6 shows
    // them landing 15+ tiles away): only react to ones landing near us. Radius 2 covers "targeted
    // our tile but we already stepped away" without picking up someone else's fight.
    private static final int BOULDER_NEARBY_RADIUS = 2;

    @Inject
    private Client client;

    @Inject
    private DemonicGorillaConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DemonicGorillaOverlay overlay;

    @Inject
    private ConfigManager configManager;

    // Not injected via Guice - each RuneLite plugin gets its own child injector, so a
    // GearSwitcherConfig @Provides binding from GearSwitcherPlugin's module isn't visible here.
    private GearSwitcherConfig gearSwitcherConfig;

    private int lastAnimation = -1;
    private HeadIcon lastAlertedProtection = null;

    // Multiple boulders can be in flight at once (a repeat cast can land while the previous one
    // is still pending) - a single-slot danger tile silently clobbers the earlier one when that
    // happens, so track every active threat separately.
    private final List<BoulderThreat> boulderThreats = new ArrayList<>();

    // ProjectileMoved fires every cycle of a projectile's flight - dedupe so each boulder spawns
    // exactly one threat (same pattern as ATormentedDemonPlugin's fire bomb handling).
    private final Set<Projectile> processedBoulderProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());

    private static class BoulderThreat {
        final WorldPoint dangerTile;
        final WorldPoint safeTile;
        final int sourceNpcIndex;
        final int expiryTick;
        // Set once we've left the danger tile (or the window lapsed) and re-attacked - stops
        // active dodging/re-triggering, but the tile stays excluded from new safe-tile candidates
        // until expiryTick, since the real boulder may still be in flight and hasn't necessarily
        // landed yet just because we stopped standing on it.
        boolean resolved;

        BoulderThreat(WorldPoint dangerTile, WorldPoint safeTile, int sourceNpcIndex, int expiryTick) {
            this.dangerTile = dangerTile;
            this.safeTile = safeTile;
            this.sourceNpcIndex = sourceNpcIndex;
            this.expiryTick = expiryTick;
        }
    }

    // Cached from the last successful findGorilla() lookup - used to re-attack after a
    // dodge/eat/drink/gear-switch, since those interrupt the interaction.
    private int currentTargetNpcIndex = -1;

    @Provides
    DemonicGorillaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DemonicGorillaConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[DemonicGorilla] " + message);
        }
    }

    @Override
    protected void startUp() {
        resetState();
        gearSwitcherConfig = configManager.getConfig(GearSwitcherConfig.class);
        overlayManager.add(overlay);
        log("Demonic Gorilla Helper started");
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        resetState();
        log("Demonic Gorilla Helper stopped");
    }

    private void resetState() {
        lastAnimation = -1;
        lastAlertedProtection = null;
        boulderThreats.clear();
        processedBoulderProjectiles.clear();
        currentTargetNpcIndex = -1;
    }

    private NPC findGorilla() {
        Player player = client.getLocalPlayer();
        return client.getNpcs().stream()
                .filter(npc -> npc.getName() != null && npc.getName().toLowerCase().contains("demonic gorilla"))
                .filter(npc -> npc.isInteracting() && npc.getInteracting() == player)
                .findFirst()
                .orElse(null);
    }

    // Reactive defensive flicking only - the gorilla's attack style isn't a fixed rotation
    // (confirmed: pre-emptively guessing the next style off the "Rhaaaaaaa!" shout produced a
    // fully unblocked hit in log7.txt when the guess was wrong, worse than the ~1-tick lag that
    // reacting to the animation directly already costs on the first hit of a new style).
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (!(event.getActor() instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (npc.getName() == null || !npc.getName().toLowerCase().contains("demonic gorilla")) {
            return;
        }
        if (!npc.isInteracting() || npc.getInteracting() != client.getLocalPlayer()) {
            return;
        }

        int animation = npc.getAnimation();
        if (animation == lastAnimation) {
            return;
        }
        lastAnimation = animation;
        if (animation == -1) {
            return;
        }

        if (animation == ANIM_CHEST_BEAT) {
            // Not a reliable boulder signal on its own (~3x more chest-beats than boulders) -
            // the real trigger is the 856 projectile in onProjectileMoved, ~1 tick later.
            log("Chest-beat (anim=" + animation + ") - possible boulder, waiting for projectile confirmation");
            return;
        }

        if (!config.enablePrayerSwitching()) {
            return;
        }

        if (animation == ANIM_MELEE) {
            log("MELEE attack detected - Protect from Melee");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MELEE, true);
        } else if (animation == ANIM_MAGIC) {
            log("MAGIC attack detected - Protect from Magic");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MAGIC, true);
        } else if (animation == ANIM_RANGED) {
            log("RANGED attack detected - Protect from Missiles");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MISSILES, true);
        } else if (animation == ANIM_PRE_ATTACK_TELEGRAPH) {
            log("Pre-attack telegraph (anim=" + animation + ") - style unknown until it lands");
        } else {
            log("Unrecognised animation " + animation + " on gorilla idx=" + npc.getIndex());
        }
    }

    // The boulder itself: a ground-targeted 856 projectile with no source actor (it falls from
    // the ceiling, the gorilla doesn't "fire" it). Gives us the exact landing tile and landing
    // tick, unlike the old chest-beat-animation trigger which guessed both and false-positived
    // on ~2 of 3 casts.
    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        Projectile proj = event.getProjectile();
        if (proj.getId() != BOULDER_PROJECTILE_ID || processedBoulderProjectiles.contains(proj)) {
            return;
        }
        if (proj.getInteracting() != null) {
            return; // actor-targeted - not the falling boulder
        }
        Player player = client.getLocalPlayer();
        LocalPoint targetLp = proj.getTarget();
        if (player == null || targetLp == null) {
            return;
        }
        processedBoulderProjectiles.add(proj);

        WorldPoint dangerTile = WorldPoint.fromLocal(client, targetLp.getX(), targetLp.getY(), proj.getFloor());
        if (dangerTile.distanceTo(player.getWorldLocation()) > BOULDER_NEARBY_RADIUS) {
            return; // another player's boulder elsewhere in the shared cavern
        }

        NPC gorilla = findGorilla();
        int sourceNpcIndex = gorilla != null ? gorilla.getIndex() : currentTargetNpcIndex;
        WorldPoint safeTile = computeBoulderSafeTile(dangerTile, gorilla);
        int ticksToLand = Math.max(1, (proj.getRemainingCycles() + 29) / 30);
        int expiryTick = client.getTickCount() + ticksToLand;
        boulderThreats.add(new BoulderThreat(dangerTile, safeTile, sourceNpcIndex, expiryTick));

        log("BOULDER incoming at " + dangerTile + " (lands in " + ticksToLand + " ticks) - safe tile " + safeTile);
        if (config.enableBoulderWarning()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "DemonicGorilla",
                    "Gorilla is dropping a boulder - move off your tile!", null);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        int tick = client.getTickCount();
        processedBoulderProjectiles.removeIf(p -> p.getRemainingCycles() <= 0);

        boolean dodgedThisTick = false;
        if (!boulderThreats.isEmpty()) {
            Player playerNow = client.getLocalPlayer();
            WorldPoint playerLocation = playerNow == null ? null : playerNow.getWorldLocation();
            int justResolvedNpcIndex = -1;

            for (BoulderThreat threat : boulderThreats) {
                if (threat.resolved) {
                    continue;
                }
                boolean stillOnDangerTile = playerLocation != null && threat.dangerTile.equals(playerLocation);
                if (stillOnDangerTile && tick <= threat.expiryTick) {
                    // Only need to clear the one tile the boulder is aimed at - once we're off it
                    // we don't need to wait for the boulder to actually land before resuming.
                    if (!dodgedThisTick) {
                        dodgedThisTick = handleBoulderDodge(threat);
                    }
                } else {
                    // Safe to resume attacking, but the tile itself stays marked dangerous (see
                    // isActiveDangerTile) until expiryTick - the real boulder may not have landed
                    // yet just because we stopped standing on it, and a new boulder's safe-tile
                    // pick must not be allowed to send us back onto it (confirmed happening: a
                    // second boulder's dodge landed on the still-in-flight first danger tile).
                    threat.resolved = true;
                    justResolvedNpcIndex = threat.sourceNpcIndex;
                }
            }

            if (justResolvedNpcIndex >= 0) {
                handleBoulderReattack(justResolvedNpcIndex);
            }

            boulderThreats.removeIf(threat -> tick > threat.expiryTick);
        }

        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        NPC gorilla = findGorilla();
        if (gorilla != null) {
            currentTargetNpcIndex = gorilla.getIndex();
        }

        // Every action below sends its own immediate menuAction/widget packet, same as the dodge
        // walk just did - only one action per tick is honoured server-side, so give movement sole
        // use of the tick.
        if (dodgedThisTick) {
            return;
        }

        handleConsumables(gorilla != null);

        if (!config.enableStyleMismatchAlert() && !config.enableAutoGearSwitch()) {
            return;
        }

        if (gorilla == null || gorilla.isDead() || gorilla.getHealthRatio() == 0) {
            lastAlertedProtection = null;
            return;
        }

        HeadIcon protection = EthanApiPlugin.getHeadIcon(gorilla);
        if (protection == null) {
            return;
        }

        boolean usingRanged = isUsingRangedGear();
        boolean blocked = (protection == HeadIcon.MELEE && !usingRanged)
                || (protection == HeadIcon.RANGED && usingRanged);

        if (!blocked) {
            lastAlertedProtection = null;
            return;
        }

        if (protection == lastAlertedProtection) {
            return;
        }
        lastAlertedProtection = protection;

        if (config.enableStyleMismatchAlert()) {
            String message = "Gorilla is praying " + protection + " - switch to "
                    + (usingRanged ? "MELEE" : "RANGED") + "!";
            log(message);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "DemonicGorilla", message, null);
        }

        if (config.enableAutoGearSwitch()) {
            switchAttackStyle(!usingRanged);
        }
    }

    private boolean isUsingRangedGear() {
        return !Equipment.search().matchesWildCardNoCase("*bow*").empty()
                || !Equipment.search().matchesWildCardNoCase("*crossbow*").empty()
                || !Equipment.search().matchesWildCardNoCase("*blowpipe*").empty()
                || !Equipment.search().matchesWildCardNoCase("*ballista*").empty();
    }

    // toRanged=true switches to A Gear Switcher's rangedGear loadout + Rigour, false switches
    // to its meleeGear loadout + Piety - Rigour/Piety are the current-tier offensive prayers
    // (Eagle Eye is the pre-Morytania-diary ranged prayer, weaker than Rigour).
    private void switchAttackStyle(boolean toRanged) {
        if (gearSwitcherConfig == null) {
            return;
        }
        if (toRanged) {
            log("Auto-switching to RANGED gear (A Gear Switcher's rangedGear list) + Rigour");
            equipLoadout(gearSwitcherConfig.rangedGear(), Prayer.RIGOUR);
        } else {
            log("Auto-switching to MELEE gear (A Gear Switcher's meleeGear list) + Piety");
            equipLoadout(gearSwitcherConfig.meleeGear(), Prayer.PIETY);
        }
        reattackGorilla("Gear switched");
    }

    private void equipLoadout(String itemIdsCsv, Prayer offensivePrayer) {
        if (itemIdsCsv == null || itemIdsCsv.trim().isEmpty()) {
            return;
        }
        for (String part : itemIdsCsv.split(",")) {
            int itemId;
            try {
                itemId = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (itemId <= 0) {
                continue;
            }
            Widget item = Inventory.search().withId(itemId).first().orElse(null);
            if (item != null) {
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(item, "Wear", "Wield", "Equip");
            }
        }
        PrayerInteraction.setPrayerState(offensivePrayer, true);
    }

    private void handleConsumables(boolean inCombat) {
        boolean acted = false;

        if (config.enableAutoDrinkPrayerPotion()
                && client.getBoostedSkillLevel(Skill.PRAYER) < config.prayerPotionThreshold()) {
            acted |= drinkPrayerPotion();
        }

        if (config.enableAutoEatFood()) {
            int hpThreshold = inCombat ? config.combatEatThreshold() : config.outOfCombatEatThreshold();
            if (client.getBoostedSkillLevel(Skill.HITPOINTS) < hpThreshold) {
                acted |= eatFood();
            }
        }

        if (acted) {
            reattackGorilla("Ate/drank");
        }
    }

    private boolean drinkPrayerPotion() {
        Optional<Widget> potion = Inventory.search().matchesWildCardNoCase("*prayer potion*").first();
        if (!potion.isPresent()) {
            return false;
        }
        log("Prayer below " + config.prayerPotionThreshold() + " - drinking prayer potion");
        MousePackets.queueClickPacket();
        WidgetPackets.queueWidgetAction(potion.get(), "Drink");
        return true;
    }

    private boolean eatFood() {
        String foodNames = config.foodName();
        if (foodNames == null || foodNames.trim().isEmpty()) {
            log("Eat food skipped - food name config is empty");
            return false;
        }
        for (String part : foodNames.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            Optional<Widget> food = findFoodItem(entry);
            if (food.isPresent()) {
                log("HP below threshold - eating " + entry);
                MousePackets.queueClickPacket();
                WidgetPackets.queueWidgetAction(food.get(), "Eat");
                return true;
            }
        }
        log("Eat food failed - no inventory item matching any of '" + foodNames + "' found");
        return false;
    }

    // Each comma-separated entry can be a wildcard-matched name (e.g. "shark") or a literal item
    // ID (e.g. "11936" for dark crab), same dual format equipLoadout() uses for gear.
    private Optional<Widget> findFoodItem(String entry) {
        try {
            int itemId = Integer.parseInt(entry);
            return Inventory.search().withId(itemId).first();
        } catch (NumberFormatException e) {
            return Inventory.search().matchesWildCardNoCase("*" + entry + "*").first();
        }
    }

    // Eating, drinking, dodging and switching gear all drop the current attack interaction.
    // Re-attacks via the cached currentTargetNpcIndex rather than findGorilla(), since the whole
    // point is that the interaction just got cleared by the action we took.
    private void reattackGorilla(String reason) {
        if (currentTargetNpcIndex < 0) {
            return;
        }
        NPC gorilla = client.getNpcs().stream()
                .filter(npc -> npc.getIndex() == currentTargetNpcIndex)
                .findFirst()
                .orElse(null);
        if (gorilla == null || gorilla.isDead() || gorilla.getHealthRatio() == 0) {
            return;
        }
        log(reason + " - re-attacking gorilla idx=" + gorilla.getIndex());
        MenuActionInteractions.interactNpc(gorilla, "Attack");
    }

    // Reissued every tick we're still standing on the danger tile (rather than once) since the
    // walk may be rejected the first click(s) while it's active - MenuActionInteractions.walkTo
    // just re-queues the move packet.
    private boolean handleBoulderDodge(BoulderThreat threat) {
        if (!config.enableAutoDodgeBoulder() || threat.safeTile == null) {
            return false;
        }
        log("Auto-dodging boulder - walking from " + threat.dangerTile + " to " + threat.safeTile);
        MenuActionInteractions.walkTo(threat.safeTile);
        return true;
    }

    // Fired once a threat resolves (player left the danger tile, or it timed out).
    private void handleBoulderReattack(int sourceNpcIndex) {
        if (!config.enableAutoReattackAfterBoulder() || sourceNpcIndex < 0) {
            return;
        }
        NPC gorilla = client.getNpcs().stream()
                .filter(npc -> npc.getIndex() == sourceNpcIndex)
                .findFirst()
                .orElse(null);
        if (gorilla == null || gorilla.isDead() || gorilla.getHealthRatio() == 0) {
            return;
        }
        log("Boulder resolved - re-attacking gorilla idx=" + gorilla.getIndex());
        MenuActionInteractions.interactNpc(gorilla, "Attack");
    }

    // Cardinal offsets tried before diagonal ones - a same-edge sidestep (e.g. top-right attack
    // tile -> top-left attack tile) is always a cardinal move for any square footprint, and avoids
    // the extra corner-cutting collision check a diagonal step needs.
    private static final int[][] CARDINAL_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL_OFFSETS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    // The boulder targets exactly one tile, but the dodge destination must be a valid attack
    // position: we reattack immediately after dodging rather than waiting out the danger window,
    // so if we end up on a non-attack tile (a ring corner, or a tile off the ring entirely) the
    // client's own pathing repositions us - straight back onto a still-dangerous tile if that's
    // the nearest attack tile in that direction (confirmed happening twice, most recently when
    // back-to-back boulders exhausted one edge of the ring and the dodge retreated to a corner).
    // Applied unconditionally, even when using ranged gear - this fight is often played at melee
    // distance regardless of attack style, and a melee-valid tile is always within ranged
    // distance too, so there's no downside to the stricter check.
    //
    // Search is a depth-2 walk: first any single step (cardinal preferred) onto a ring tile, then
    // two-step routes through an intermediate tile - chained boulders can eat every ring tile
    // touching the danger tile, in which case the correct dodge is around the footprint corner
    // onto a different side of the gorilla (a diagonal or two-step move). ~4 ticks of boulder
    // flight time comfortably covers a two-tile walk.
    private WorldPoint computeBoulderSafeTile(WorldPoint anchor, NPC gorilla) {
        List<WorldPoint> ring = validAttackRingTiles(gorilla);

        List<WorldPoint> stepOne = new ArrayList<>();
        for (int[][] offsets : new int[][][]{CARDINAL_OFFSETS, DIAGONAL_OFFSETS}) {
            for (int[] d : offsets) {
                WorldPoint candidate = anchor.dx(d[0]).dy(d[1]);
                if (!canWalkTo(anchor, candidate) || isActiveDangerTile(candidate)) {
                    continue;
                }
                if (ring.contains(candidate)) {
                    return candidate;
                }
                stepOne.add(candidate);
            }
        }

        for (WorldPoint mid : stepOne) {
            for (int[][] offsets : new int[][][]{CARDINAL_OFFSETS, DIAGONAL_OFFSETS}) {
                for (int[] d : offsets) {
                    WorldPoint candidate = mid.dx(d[0]).dy(d[1]);
                    if (candidate.equals(anchor) || !ring.contains(candidate) || isActiveDangerTile(candidate)) {
                        continue;
                    }
                    if (canWalkTo(mid, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        // No reachable attack tile within two steps - any clear neighbour still beats standing
        // under the boulder, even though the post-dodge reattack pathing is then unguarded.
        return stepOne.isEmpty() ? null : stepOne.get(0);
    }

    // Every valid melee attack tile around the gorilla's actual multi-tile footprint
    // (getWorldLocation() is its south-west corner). Melee range in this fight is
    // orthogonal-only: the ring is every tile directly in front of an edge, excluding the 4
    // corners of the (size+2) x (size+2) bounding box (those only touch the footprint
    // diagonally) - e.g. for a 2x2 gorilla:
    //   o x x o
    //   x g g x
    //   x g g x
    //   o x x o
    private List<WorldPoint> validAttackRingTiles(NPC gorilla) {
        List<WorldPoint> ring = new ArrayList<>();
        if (gorilla == null) {
            return ring;
        }
        WorldPoint base = gorilla.getWorldLocation();
        int size = 1;
        if (gorilla.getComposition() != null) {
            size = Math.max(1, gorilla.getComposition().getSize());
        }
        int minX = base.getX();
        int minY = base.getY();
        int maxX = minX + size - 1;
        int maxY = minY + size - 1;
        int plane = base.getPlane();

        for (int x = minX; x <= maxX; x++) {
            ring.add(new WorldPoint(x, minY - 1, plane));
            ring.add(new WorldPoint(x, maxY + 1, plane));
        }
        for (int y = minY; y <= maxY; y++) {
            ring.add(new WorldPoint(minX - 1, y, plane));
            ring.add(new WorldPoint(maxX + 1, y, plane));
        }
        return ring;
    }

    private boolean isActiveDangerTile(WorldPoint tile) {
        for (BoulderThreat threat : boulderThreats) {
            if (threat.dangerTile.equals(tile)) {
                return true;
            }
        }
        return false;
    }

    // Filters out tiles blocked by a wall/object/off-scene tile, and diagonal moves that would cut
    // a corner (both orthogonal neighbours must also be clear) - same check ATormentedDemonPlugin
    // uses for its fire bomb dodge, needed here since sidestepping onto an adjacent edge of the
    // gorilla's footprint (e.g. north edge -> east edge) can require a diagonal step.
    private boolean canWalkTo(WorldPoint from, WorldPoint to) {
        CollisionData[] maps = client.getCollisionMaps();
        if (maps == null) {
            return true; // collision data not loaded - don't block on it
        }
        int plane = to.getPlane();
        if (plane < 0 || plane >= maps.length || maps[plane] == null) {
            return true;
        }
        int[][] flags = maps[plane].getFlags();

        LocalPoint fromLp = LocalPoint.fromWorld(client, from);
        LocalPoint toLp = LocalPoint.fromWorld(client, to);
        if (fromLp == null || toLp == null) {
            return false;
        }

        int dx = Integer.signum(to.getX() - from.getX());
        int dy = Integer.signum(to.getY() - from.getY());

        if (!isStepClear(flags, fromLp.getSceneX(), fromLp.getSceneY(), toLp.getSceneX(), toLp.getSceneY(), dx, dy)) {
            return false;
        }

        if (dx != 0 && dy != 0) {
            boolean horizontalClear = isStepClear(flags, fromLp.getSceneX(), fromLp.getSceneY(),
                    fromLp.getSceneX() + dx, fromLp.getSceneY(), dx, 0);
            boolean verticalClear = isStepClear(flags, fromLp.getSceneX(), fromLp.getSceneY(),
                    fromLp.getSceneX(), fromLp.getSceneY() + dy, 0, dy);
            if (!horizontalClear || !verticalClear) {
                return false;
            }
        }
        return true;
    }

    private boolean isStepClear(int[][] flags, int fromX, int fromY, int toX, int toY, int dx, int dy) {
        if (toX < 0 || toY < 0 || toX >= flags.length || toY >= flags[0].length) {
            return false;
        }
        int destFlag = flags[toX][toY];
        if ((destFlag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) {
            return false;
        }
        int srcFlag = flags[fromX][fromY];
        int towardDest = directionBlockFlag(dx, dy);
        int towardSrc = directionBlockFlag(-dx, -dy);
        return (srcFlag & towardDest) == 0 && (destFlag & towardSrc) == 0;
    }

    // WorldPoint: +X is east, +Y is north.
    private static int directionBlockFlag(int dx, int dy) {
        if (dx == 0 && dy == 1) return CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        if (dx == 0 && dy == -1) return CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
        if (dx == 1 && dy == 0) return CollisionDataFlag.BLOCK_MOVEMENT_EAST;
        if (dx == -1 && dy == 0) return CollisionDataFlag.BLOCK_MOVEMENT_WEST;
        if (dx == 1 && dy == 1) return CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST;
        if (dx == -1 && dy == 1) return CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST;
        if (dx == 1 && dy == -1) return CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST;
        if (dx == -1 && dy == -1) return CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST;
        return 0;
    }

    public List<WorldPoint> getBoulderDangerTiles() {
        List<WorldPoint> tiles = new ArrayList<>();
        for (BoulderThreat threat : boulderThreats) {
            tiles.add(threat.dangerTile);
        }
        return tiles;
    }

    public List<WorldPoint> getBoulderSafeTiles() {
        List<WorldPoint> tiles = new ArrayList<>();
        for (BoulderThreat threat : boulderThreats) {
            if (threat.safeTile != null) {
                tiles.add(threat.safeTile);
            }
        }
        return tiles;
    }
}
