package com.example.ATormentedDemon;

import com.example.EthanApiPlugin.Collections.Equipment;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.GearSwitcher.GearSwitcherConfig;
import com.example.InteractionApi.MenuActionInteractions;
import com.example.InteractionApi.PrayerInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@PluginDescriptor(
        name = "A Tormented Demon Helper",
        description = "Reactive prayer flicking and attack-style mismatch warnings for tormented demons",
        tags = {"tormented", "demon", "prayer", "combat", "ghorrock"},
        enabledByDefault = false
)
public class ATormentedDemonPlugin extends Plugin {

    // Ghorrock temple monsters only — excludes the Dragon Slayer II quest/cutscene variants
    // (TORMENTED_DEMON_13601/13602) and their invisible collision copies (13603-13606).
    private static final Set<Integer> TORMENTED_DEMON_IDS = Set.of(
            NpcID.TORMENTED_DEMON, NpcID.TORMENTED_DEMON_13600
    );

    // Confirmed from [PFDiag] logs across 3 kills: each repeats every ~6 ticks and reliably
    // produces a BLOCK_ME (type=12, 0 dmg) hitsplat iff the matching protect prayer was active
    // at that exact tick, and a DAMAGE_ME (type=16) hitsplat otherwise.
    private static final int ANIM_MELEE = 11392;
    private static final int ANIM_MAGIC = 11388;
    private static final int ANIM_RANGED = 11389;
    // TBD — not observed in the diagnostic session (fights were too short to hit the
    // ~60-tick fire bomb timer). Re-run the diagnostic in a longer fight to capture it.
    private static final int ANIM_FIREBOMB = -1;

    // Non-attack animations, logged for visibility but never trigger a prayer switch.
    // 11387 precedes every new attack burst (melee/magic/ranged) by ~6 ticks with no hitsplat
    // of its own - a style-switch telegraph, not an attack. 11394 appears once ~18 ticks before
    // death (pre-death stagger); 11395 is the death animation (confirmed on all 3 kills).
    private static final int ANIM_STYLE_SWITCH_TELEGRAPH = 11387;
    private static final int ANIM_DEFEATED_STAGGER = 11394;
    private static final int ANIM_DEATH = 11395;

    // Confirmed from two diagnostic sessions (7 occurrences total): the fire bomb is two
    // ground-targeted (no Actor target) Projectiles with this id, fired by the demon, landing
    // ~2 ticks after spawn — one on the tile the player was standing on, one on a random
    // adjacent tile. No distinct NPC animation exists for this (it rides on the shared
    // ANIM_STYLE_SWITCH_TELEGRAPH), so detection goes through ProjectileMoved instead.
    private static final int FIREBOMB_PROJECTILE_ID = 2855;

    private static final int DARK_CRAB_ITEM_ID = ItemID.DARK_CRAB;

    // Confirmed via net.runelite.api.gameval.NpcID - not present in the older net.runelite.api.NpcID
    // this file otherwise uses for TORMENTED_DEMON_IDS.
    private static final int GREATER_GHOST_THRALL_ID = 10880;
    // "Is this thrall mine" heuristic: other players' thralls can share this NPC id in the same
    // temple, but thralls stick close to their owner, so nearest-within-N-tiles is a reasonable
    // proxy (there's no varbit/varplayer exposing thrall ownership).
    private static final int THRALL_PROXIMITY_TILES = 4;
    // Re-cast is a no-op if the spell is unavailable (reagents/quest/level) - retry every few
    // ticks instead of every tick to avoid spamming the cast click uselessly.
    private static final int THRALL_CAST_RETRY_TICKS = 5;

    @Inject
    private Client client;

    @Inject
    private ATormentedDemonConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ATormentedDemonOverlay overlay;

    @Inject
    private ConfigManager configManager;

    // Not injected via Guice - each RuneLite plugin gets its own child injector, so a
    // GearSwitcherConfig @Provides binding from GearSwitcherPlugin's module isn't visible here.
    // ConfigManager#getConfig builds the same config proxy directly, regardless of whether
    // A Gear Switcher is even enabled.
    private GearSwitcherConfig gearSwitcherConfig;

    private int lastAnimation = -1;
    private HeadIcon lastAlertedProtection = null;

    private final Set<Projectile> processedFireBombProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<WorldPoint> fireBombDangerTiles = new ArrayList<>();
    private List<WorldPoint> fireBombSafeTiles = Collections.emptyList();
    private int fireBombWarningExpiryTick = -1;
    private int fireBombSourceNpcIndex = -1;

    // Cached from the last successful findTormentedDemon() lookup - used to re-attack after an
    // eat/drink/gear-switch, since those interrupt the interaction (findTormentedDemon() would
    // return null right when we need it most).
    private int currentTargetNpcIndex = -1;
    private int thrallCastCooldown = 0;

    @Provides
    ATormentedDemonConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ATormentedDemonConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[ATormentedDemon] " + message);
        }
    }

    @Override
    protected void startUp() {
        resetState();
        gearSwitcherConfig = configManager.getConfig(GearSwitcherConfig.class);
        overlayManager.add(overlay);
        log("started");
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        resetState();
        log("stopped");
    }

    private void resetState() {
        lastAnimation = -1;
        lastAlertedProtection = null;
        processedFireBombProjectiles.clear();
        fireBombDangerTiles.clear();
        fireBombSafeTiles = Collections.emptyList();
        fireBombWarningExpiryTick = -1;
        fireBombSourceNpcIndex = -1;
        currentTargetNpcIndex = -1;
        thrallCastCooldown = 0;
    }

    private NPC findTormentedDemon() {
        Player player = client.getLocalPlayer();
        return client.getNpcs().stream()
                .filter(npc -> TORMENTED_DEMON_IDS.contains(npc.getId()))
                .filter(npc -> npc.isInteracting() && npc.getInteracting() == player)
                .findFirst()
                .orElse(null);
    }

    // Reactive defensive flicking: the demon's attack order isn't predictable (unlike GWD
    // minions' fixed cycle), so we can only react to the animation as it starts, same approach
    // as DemonicGorillaPlugin.
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!config.enablePrayerFlicking() || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (!(event.getActor() instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (!TORMENTED_DEMON_IDS.contains(npc.getId())) {
            return;
        }
        if (!npc.isInteracting() || npc.getInteracting() != client.getLocalPlayer()) {
            return;
        }

        int animation = npc.getAnimation();
        if (animation == lastAnimation || animation == -1) {
            return;
        }
        lastAnimation = animation;

        if (animation == ANIM_MELEE) {
            log("MELEE attack detected - Protect from Melee");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MELEE, true);
        } else if (animation == ANIM_MAGIC) {
            log("MAGIC attack detected - Protect from Magic");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MAGIC, true);
        } else if (animation == ANIM_RANGED) {
            log("RANGED attack detected - Protect from Missiles");
            PrayerInteraction.setPrayerState(Prayer.PROTECT_FROM_MISSILES, true);
        } else if (animation == ANIM_FIREBOMB) {
            // Dodge/warning logic not implemented yet - just confirm detection for now.
            log("FIREBOMB animation detected (anim=" + animation + ") idx=" + npc.getIndex());
        } else if (animation == ANIM_STYLE_SWITCH_TELEGRAPH) {
            log("Style switch telegraph (anim=" + animation + ") - next attack style unknown until it lands");
        } else if (animation == ANIM_DEFEATED_STAGGER || animation == ANIM_DEATH) {
            log("Demon idx=" + npc.getIndex() + " defeated (anim=" + animation + ")");
        } else {
            log("Unrecognised animation " + animation + " on tormented demon idx=" + npc.getIndex());
        }
    }

    // Offensive side: the demon changes its own protection prayer every ~150hp lost. Warn
    // and/or auto-switch gear when that protection is blocking the style we're currently
    // equipped for, same HeadIcon read EthanApiPlugin/HunllefPlugin already use.
    @Subscribe
    public void onGameTick(GameTick event) {
        int tick = client.getTickCount();
        processedFireBombProjectiles.removeIf(p -> p.getRemainingCycles() <= 0);

        boolean dodgedThisTick = false;
        if (fireBombWarningExpiryTick >= 0) {
            dodgedThisTick = handleFireBombDodge();

            if (tick > fireBombWarningExpiryTick) {
                fireBombDangerTiles.clear();
                fireBombSafeTiles = Collections.emptyList();
                fireBombWarningExpiryTick = -1;
                handleFireBombReattack();
            }
        }

        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        NPC demon = findTormentedDemon();
        if (demon != null) {
            currentTargetNpcIndex = demon.getIndex();
        }

        // Every action below sends its own immediate menuAction/widget packet, same as the dodge
        // walk just did - the server only honours the last packet it receives in a tick, so a
        // second action here risks silently overriding the dodge. Give movement sole use of the
        // tick instead.
        if (dodgedThisTick) {
            return;
        }

        handleConsumables(demon != null);
        handleThrall(demon != null);

        if (!config.enableStyleMismatchAlert() && !config.enableAutoGearSwitch()) {
            return;
        }

        if (demon == null || demon.isDead() || demon.getHealthRatio() == 0) {
            lastAlertedProtection = null;
            return;
        }

        HeadIcon protection = EthanApiPlugin.getHeadIcon(demon);
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
            String message = "Tormented demon is praying " + protection + " - switch to "
                    + (usingRanged ? "MELEE" : "RANGED") + "!";
            log(message);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "ATormentedDemon", message, null);
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

    // toRanged=true switches to A Gear Switcher's rangedGear loadout + Eagle Eye, false switches
    // to its meleeGear loadout + Piety - the exact same items/prayers GearSwitcherPlugin's own
    // F9/F10 hotkeys use, just triggered automatically off the demon's protection prayer.
    private void switchAttackStyle(boolean toRanged) {
        if (gearSwitcherConfig == null) {
            return;
        }
        if (toRanged) {
            log("Auto-switching to RANGED gear (A Gear Switcher's rangedGear list) + Eagle Eye");
            equipLoadout(gearSwitcherConfig.rangedGear(), Prayer.EAGLE_EYE);
        } else {
            log("Auto-switching to MELEE gear (A Gear Switcher's meleeGear list) + Piety");
            equipLoadout(gearSwitcherConfig.meleeGear(), Prayer.PIETY);
        }
        // Equipping gear drops our attack interaction, same as eating/drinking - resume it.
        reattackDemon("Gear switched");
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

    // Prayer potion below threshold, and dark crabs below an HP threshold that depends on
    // whether a demon is currently engaged - out of combat we can afford to wait for a bigger
    // top-up (may take multiple crabs; re-evaluated every tick so it just keeps eating until
    // above threshold, same retry-until-satisfied pattern as the fire bomb dodge).
    private void handleConsumables(boolean inCombat) {
        boolean acted = false;

        if (config.enableAutoDrinkPrayerPotion()
                && client.getBoostedSkillLevel(Skill.PRAYER) < config.prayerPotionThreshold()) {
            acted |= drinkPrayerPotion();
        }

        if (config.enableAutoEatDarkCrab()) {
            int hpThreshold = inCombat ? config.combatEatThreshold() : config.outOfCombatEatThreshold();
            if (client.getBoostedSkillLevel(Skill.HITPOINTS) < hpThreshold) {
                acted |= eatDarkCrab();
            }
        }

        if (acted) {
            reattackDemon("Ate/drank");
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

    private boolean eatDarkCrab() {
        Optional<Widget> crab = Inventory.search().withId(DARK_CRAB_ITEM_ID).first();
        if (!crab.isPresent()) {
            return false;
        }
        log("HP below threshold - eating dark crab");
        MousePackets.queueClickPacket();
        WidgetPackets.queueWidgetAction(crab.get(), "Eat");
        return true;
    }

    // Eating, drinking, and switching gear all drop the current attack interaction. Re-attacks
    // via the cached currentTargetNpcIndex rather than findTormentedDemon(), since the whole
    // point is that the interaction just got cleared by the action we took.
    private void reattackDemon(String reason) {
        if (currentTargetNpcIndex < 0) {
            return;
        }
        NPC demon = client.getNpcs().stream()
                .filter(npc -> npc.getIndex() == currentTargetNpcIndex)
                .findFirst()
                .orElse(null);
        if (demon == null || demon.isDead() || demon.getHealthRatio() == 0) {
            return;
        }
        log(reason + " - re-attacking demon idx=" + demon.getIndex());
        MenuActionInteractions.interactNpc(demon, "Attack");
    }

    // Keep a Greater Ghost thrall up via the Arceuus spellbook, same widget-click pattern
    // PrayerInteraction uses for prayers (op 1 = left-click/cast on the spellbook icon).
    // Only while actually fighting a TD - not worth summoning/reagents outside combat.
    private void handleThrall(boolean inCombat) {
        if (!config.enableAutoSummonThrall() || !inCombat) {
            return;
        }
        if (thrallCastCooldown > 0) {
            thrallCastCooldown--;
            return;
        }
        if (hasNearbyGreaterGhostThrall()) {
            return;
        }
        log("No greater ghost thrall nearby - casting Resurrect Greater Ghost");
        MousePackets.queueClickPacket();
        WidgetPackets.queueWidgetActionPacket(1, InterfaceID.MagicSpellbook.RESURRECT_GREATER_GHOST, -1, -1);
        thrallCastCooldown = THRALL_CAST_RETRY_TICKS;
    }

    private boolean hasNearbyGreaterGhostThrall() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }
        WorldPoint pos = player.getWorldLocation();
        return client.getNpcs().stream()
                .anyMatch(npc -> npc.getId() == GREATER_GHOST_THRALL_ID
                        && npc.getWorldLocation() != null
                        && npc.getWorldLocation().distanceTo(pos) <= THRALL_PROXIMITY_TILES);
    }

    // Walk off a marked danger tile onto a marked safe one. Reissued every tick we're still
    // standing on a danger tile (rather than once) since the bind may reject the first click(s)
    // while it's active - MenuActionInteractions.walkTo just re-queues the move packet.
    private boolean handleFireBombDodge() {
        if (!config.enableAutoDodgeFireBomb() || fireBombDangerTiles.isEmpty()) {
            return false;
        }
        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }
        WorldPoint pos = player.getWorldLocation();
        if (!fireBombDangerTiles.contains(pos) || fireBombSafeTiles.isEmpty()) {
            return false;
        }
        WorldPoint dodgeTile = fireBombSafeTiles.get(0);
        log("Auto-dodging fire bomb - walking from " + pos + " to " + dodgeTile);
        MenuActionInteractions.walkTo(dodgeTile);
        return true;
    }

    // Fired once, right as the fire bomb warning expires (bombs have landed and resolved).
    // Looks the demon up by index rather than via findTormentedDemon()'s isInteracting() check,
    // since dodging away can drop its interaction with us before it re-targets.
    private void handleFireBombReattack() {
        if (!config.enableAutoReattackAfterFireBomb() || fireBombSourceNpcIndex < 0) {
            return;
        }
        NPC demon = client.getNpcs().stream()
                .filter(npc -> npc.getIndex() == fireBombSourceNpcIndex)
                .findFirst()
                .orElse(null);
        if (demon == null || demon.isDead() || demon.getHealthRatio() == 0) {
            return;
        }
        log("Fire bomb resolved - re-attacking demon idx=" + demon.getIndex());
        MenuActionInteractions.interactNpc(demon, "Attack");
    }

    // Fire bomb: two ground-targeted FIREBOMB_PROJECTILE_ID projectiles spawn on the same tick,
    // ~2 ticks before landing. Their exact target tiles come straight from the projectile, so no
    // tile-guessing is needed - just wait for both and mark everything else in the 3x3 as safe.
    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!config.enableFireBombWarning() || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        Projectile proj = event.getProjectile();
        if (proj.getId() != FIREBOMB_PROJECTILE_ID || processedFireBombProjectiles.contains(proj)) {
            return;
        }
        if (proj.getInteracting() != null) {
            return; // actor-targeted, not the ground-targeted fire bomb
        }
        Actor source = proj.getSourceActor();
        if (!(source instanceof NPC) || !TORMENTED_DEMON_IDS.contains(((NPC) source).getId())) {
            return;
        }
        processedFireBombProjectiles.add(proj);
        fireBombSourceNpcIndex = ((NPC) source).getIndex();

        LocalPoint targetLp = proj.getTarget();
        if (targetLp == null) {
            return;
        }
        WorldPoint landingTile = WorldPoint.fromLocal(client, targetLp.getX(), targetLp.getY(), proj.getFloor());

        boolean firstOfPair = fireBombDangerTiles.isEmpty();
        if (!fireBombDangerTiles.contains(landingTile)) {
            fireBombDangerTiles.add(landingTile);
        }

        int tick = client.getTickCount();
        int ticksToLand = Math.max(1, (proj.getRemainingCycles() + 29) / 30);
        fireBombWarningExpiryTick = tick + ticksToLand;
        recomputeSafeTiles();

        if (firstOfPair) {
            log("FIREBOMB incoming - first landing tile " + landingTile);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "ATormentedDemon",
                    "Fire bomb incoming - move off the marked tiles!", null);
        } else {
            log("FIREBOMB second landing tile " + landingTile);
        }
    }

    private void recomputeSafeTiles() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            fireBombSafeTiles = Collections.emptyList();
            return;
        }
        WorldPoint anchor = player.getWorldLocation();
        List<WorldPoint> safe = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue; // the anchor tile is always one of the two danger tiles
                }
                WorldPoint candidate = anchor.dx(dx).dy(dy);
                if (!fireBombDangerTiles.contains(candidate) && canWalkTo(anchor, candidate)) {
                    safe.add(candidate);
                }
            }
        }
        // Prefer cardinal steps (Manhattan distance 1) over diagonal ones (distance 2) - simpler,
        // more reliable single-tile moves.
        safe.sort(Comparator.comparingInt(wp -> Math.abs(wp.getX() - anchor.getX()) + Math.abs(wp.getY() - anchor.getY())));
        fireBombSafeTiles = safe;
    }

    // Filters out tiles blocked by a wall/object/off-scene tile, and diagonal moves that would
    // cut a corner (both orthogonal neighbours must also be clear) - per user report, the naive
    // "any tile not marked as a bomb landing spot" pick occasionally chose an actually-blocked
    // tile the demon can't be walked to.
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
            // Diagonal step - both orthogonal neighbours must also be clear, or we'd be cutting
            // through the corner of two walls that don't individually block the diagonal flag.
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

    public boolean hasActiveFireBombWarning() {
        return !fireBombDangerTiles.isEmpty();
    }

    public List<WorldPoint> getFireBombDangerTiles() {
        return fireBombDangerTiles;
    }

    public List<WorldPoint> getSafeTiles() {
        return fireBombSafeTiles;
    }
}
