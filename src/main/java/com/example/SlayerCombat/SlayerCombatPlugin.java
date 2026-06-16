package com.example.SlayerCombat;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.TileItems;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.NPCQuery;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.InteractionApi.InventoryInteraction;
import com.example.InteractionApi.MenuActionInteractions;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@PluginDescriptor(
        name = "A Slayer Combat",
        description = "Slayer helper",
        tags = {"slayer", "combat", "npc", "prayer"},
        hidden = false,
        enabledByDefault = false
)
public class SlayerCombatPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private SlayerCombatConfig config;

    @Inject
    private ItemManager itemManager;

    private int tickDelay = 0;

    // Prayer potion item IDs (4-dose through 1-dose)
    private static final Set<Integer> PRAYER_POTION_IDS = Set.of(2434, 139, 141, 143);

    // Cannon mode: names the assembled cannon object and the inventory part we "Set-up". The
    // assembled object's real name is "Dwarf multicannon" (confirmed via the debug dump); we detect
    // it by id anyway (name is only a fallback).
    private static final String CANNON_OBJECT_NAME = "Dwarf multicannon";
    private static final String CANNON_BASE_NAME = "Cannon base";
    // The assembled, firing cannon is object DWARF_MULTICANNON1 (id 6). Assembly steps are
    // base 7 -> stand 8 -> barrels 9 -> 6. We match by id rather than name+"Fire" action because the
    // old name+"Fire" search missed the placed cannon (race/transform) and looped on Set-up.
    private static final int ASSEMBLED_CANNON_ID = 6;
    private static final int CANNON_BASE_ITEM_ID = 6;   // "Cannon base" inventory item
    private static final int CANNONBALL_ITEM_ID = 2;    // "Cannonball"

    // Latches so the idle/stopped states log once rather than every tick.
    private boolean loggedNoCannon = false;
    private boolean loggedNoCannonballs = false;
    private boolean loggedCannonActions = false;
    // A freshly assembled cannon auto-loads but sits IDLE until it's Fired once to start it. The
    // threshold refill misses this whenever the loaded count is already high, so after each Set-up we
    // force exactly one "begin firing" Fire (harmless on an already-firing cannon). Set on Set-up,
    // cleared once we've fired the placed cannon.
    private boolean pendingInitialFire = false;

    @Provides
    SlayerCombatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SlayerCombatConfig.class);
    }

    @Override
    protected void startUp() {
        tickDelay = 0;
        loggedNoCannon = false;
        loggedNoCannonballs = false;
        loggedCannonActions = false;
        pendingInitialFire = false;
        log("SlayerCombat started");
    }

    @Override
    protected void shutDown() {
        tickDelay = 0;
        log("SlayerCombat stopped");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.enabled() || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        if (config.enablePrayerPots()) {
            int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
            if (currentPrayer < config.prayerThreshold()) {
                log("Prayer low (" + currentPrayer + "), drinking prayer potion");
                if (InventoryInteraction.useItem(PRAYER_POTION_IDS, "Drink")) {
                    tickDelay = 3;
                    return;
                }
            }
        }

        // If the character is already walking somewhere (e.g. running to loot we just
        // clicked), don't issue any fresh click — let it arrive. This is what stops the
        // plugin spamming "Attack" on a Kurask while we're on our way to pick something up.
        if (MenuActionInteractions.isMoving()) {
            return;
        }

        // Cannon mode (e.g. Mountain trolls): manage the cannon + positioning + looting only.
        // We never manually attack — auto-retaliate does the fighting.
        if (cannonModeActive()) {
            cannonModeTick();
            return;
        }

        if (isInCombat()) {
            return;
        }

        // Loot before attacking the next target: once a kill is done and we're standing
        // still, grab any valuable drops first.
        if (config.enableLooting() && lootNearestValuable()) {
            // Picking an item up only takes a tick or two once we're on/next to it.
            tickDelay = 2;
            return;
        }

        attackNearestTarget();
        tickDelay = 3; // always wait 3 ticks after an attack attempt, whether or not a target was found
    }

    /**
     * Whether cannon mode is active: the config control overrides the selected monster's database
     * flag (AUTO follows the monster, ON/OFF force it).
     */
    private boolean cannonModeActive() {
        switch (config.cannonControl()) {
            case ON:
                return true;
            case OFF:
                return false;
            default:
                return config.monster().cannonMode;
        }
    }

    /**
     * Cannon-mode loop (Mountain trolls): keep a Dwarven multicannon set up and fed, stand on the
     * fight tile so the cannon aggros monsters onto us, and loot when it's safe. No manual
     * attacking — auto-retaliate handles the kills.
     */
    private void cannonModeTick() {
        WorldPoint setupTile = new WorldPoint(config.cannonSetupX(), config.cannonSetupY(), client.getPlane());
        WorldPoint fightTile = new WorldPoint(config.cannonFightX(), config.cannonFightY(), client.getPlane());
        WorldPoint pos = client.getLocalPlayer().getWorldLocation();

        // Locate our assembled cannon by object id (DWARF_MULTICANNON1). Don't filter on the "Fire"
        // action here — see ASSEMBLED_CANNON_ID note.
        Optional<TileObject> cannon = findCannon();

        // Authoritative "is a cannon up?" signal: RuneLite's built-in Cannon plugin flips
        // cannonPlaced on the "You add the furnace." message. Fall back to the scene object so we
        // still work if that plugin happens to be disabled (then cballsLeft() is unavailable too).
        boolean cannonPlaced = CannonTracker.cannonPlaced() || cannon.isPresent();

        // 1. No cannon up → set one up, but only if we still have a Cannon base. Once set up, the
        //    base leaves the inventory, so a missing base with no cannon means we're out of parts:
        //    idle instead of hammering a dead Set-up.
        if (!cannonPlaced) {
            if (Inventory.getItemAmount(CANNON_BASE_ITEM_ID) == 0) {
                if (!loggedNoCannon) {
                    log("No cannon placed and no '" + CANNON_BASE_NAME + "' in inventory — nothing to set up; idling");
                    loggedNoCannon = true;
                }
                return;
            }
            loggedNoCannon = false;
            if (!pos.equals(setupTile)) {
                log("Cannon not placed; walking to setup tile " + setupTile);
                MenuActionInteractions.walkTo(setupTile);
                tickDelay = 2;
                return;
            }
            log("On setup tile; setting up cannon base");
            if (InventoryInteraction.useItem(CANNON_BASE_ITEM_ID, "Set-up")) {
                // Assembling + auto-filling takes a few seconds; don't poke it meanwhile. Once it's
                // up it'll be loaded but idle, so queue a one-off Fire to start it.
                pendingInitialFire = true;
                tickDelay = 8;
            } else {
                log("No '" + CANNON_BASE_NAME + "' with a 'Set-up' action in inventory");
            }
            return;
        }
        loggedNoCannon = false;

        // 2. Refill when the loaded count is low. 'Fire' on the cannon tops it up from inventory.
        //    (A freshly set-up cannon auto-loads to max, so right after setup balls is high and no
        //    Fire is needed — that's expected, not a bug.)
        int balls = CannonTracker.cballsLeft();
        // Per-tick cannon-state trace — noisy, so off by default. Uncomment to debug cannon issues.
        // log("Cannon up (obj id=" + (cannon.isPresent() ? cannon.get().getId() : "off-scene")
        //         + "); loaded=" + balls + " threshold=" + config.cannonballThreshold()
        //         + (pendingInitialFire ? " [pending initial Fire]" : ""));

        // 2a. Just set up: the cannon assembles, then AUTO-FILLS from inventory, then sits idle until
        //     Fired once to begin. We must wait for the auto-fill (loaded > 0) before that start
        //     Fire — firing at loaded=0 (mid-assembly/pre-fill) is ignored by the game, which is why
        //     it used to need a manual click. Once loaded, Fire once to start it, then clear.
        if (pendingInitialFire) {
            if (cannon.isPresent() && balls > 0) {
                log("Starting cannon (initial Fire)");
                if (fireCannon(cannon.get())) {
                    pendingInitialFire = false;
                } else {
                    log("Could not fire the cannon" + describeCannon(cannon.get()));
                }
                tickDelay = 2;
            }
            // else: cannon still assembling/auto-filling (loaded=0) or off-scene — wait, keep flag.
            return;
        }

        if (balls < 0) {
            log("Cannonball count unavailable — is the built-in Cannon plugin enabled? fields=["
                    + CannonTracker.describeFields() + "]");
        } else if (balls < config.cannonballThreshold()) {
            if (Inventory.getItemAmount(CANNONBALL_ITEM_ID) == 0) {
                if (!loggedNoCannonballs) {
                    log("Out of cannonballs (loaded=" + balls + ", none in inventory) — stopping refill; idling");
                    loggedNoCannonballs = true;
                }
            } else if (cannon.isPresent()) {
                loggedNoCannonballs = false;
                log("Cannonballs low (" + balls + " < " + config.cannonballThreshold() + "); firing/refilling");
                if (!fireCannon(cannon.get())) {
                    log("Could not fire the cannon" + describeCannon(cannon.get()));
                }
                tickDelay = 2;
                return;
            } else {
                log("Cannonballs low but the cannon object isn't on the loaded scene; can't refill this tick");
            }
        } else {
            loggedNoCannonballs = false;
        }

        // 3. Get back onto the fight tile if we've drifted (e.g. after looting), but don't
        //    reposition mid-fight — let auto-retaliate keep working.
        if (!pos.equals(fightTile) && !isInCombat()) {
            log("Returning to fight tile " + fightTile);
            MenuActionInteractions.walkTo(fightTile);
            tickDelay = 2;
            return;
        }

        // 4. Loot valuable drops when we're not actively being hit.
        if (config.enableLooting() && !isInCombat() && lootNearestValuable()) {
            tickDelay = 2;
            return;
        }

        // Otherwise idle on the fight tile and let the cannon + auto-retaliate do the work.
    }

    /** Finds our set-up cannon in the scene: by assembled-cannon id first, name as a fallback. */
    private Optional<TileObject> findCannon() {
        Optional<TileObject> byId = TileObjects.search().withId(ASSEMBLED_CANNON_ID).nearestToPlayer();
        if (byId.isPresent()) {
            return byId;
        }
        return TileObjects.search().withName(CANNON_OBJECT_NAME).nearestToPlayer();
    }

    /**
     * Fires (refills) the cannon. Tries the resolved "Fire" object action; if that action isn't on
     * the composition (owner-only options the cache doesn't list), falls back to the first object
     * option, which is the owned cannon's left-click "Fire". Logs the real name/actions once.
     */
    private boolean fireCannon(TileObject cannon) {
        if (config.debugLogging() && !loggedCannonActions) {
            log("Cannon object" + describeCannon(cannon));
            loggedCannonActions = true;
        }
        if (MenuActionInteractions.interactObject(cannon, "Fire")) {
            return true;
        }
        // "Fire" not in the static composition — dispatch the first object option directly.
        return MenuActionInteractions.interactObjectOption(cannon, 1, "Fire");
    }

    private String describeCannon(TileObject cannon) {
        ObjectComposition comp = TileObjectQuery.getObjectComposition(cannon);
        return " id=" + cannon.getId() + " name='" + (comp != null ? comp.getName() : "?")
                + "' actions=" + Arrays.toString(comp != null ? comp.getActions() : null);
    }

    /**
     * Picks up the nearest ground-item stack worth more than the configured threshold.
     * Returns true if a loot action was issued (so the caller skips attacking this tick).
     */
    private boolean lootNearestValuable() {
        final int threshold = config.lootValueThreshold();
        final Set<Integer> whitelistIds = new HashSet<>();
        final Set<String> whitelistNames = new HashSet<>();
        parseItemList(config.lootWhitelist(), whitelistIds, whitelistNames);
        final Set<Integer> blacklistIds = new HashSet<>();
        final Set<String> blacklistNames = new HashSet<>();
        parseItemList(config.lootBlacklist(), blacklistIds, blacklistNames);

        Optional<ETileItem> target = TileItems.search()
                .withinDistance(config.maxLootDistance())
                .filter(it -> it.isMine() || it.getTileItem().getOwnership() == TileItem.OWNERSHIP_NONE)
                .filter(it -> {
                    int id = it.getTileItem().getId();
                    String name = itemName(id).toLowerCase();
                    // Blacklist wins over everything — never pick these up.
                    if (blacklistIds.contains(id) || blacklistNames.contains(name)) {
                        return false;
                    }
                    // Whitelisted items are taken regardless of (GE) value — covers untradeables.
                    if (whitelistIds.contains(id) || whitelistNames.contains(name)) {
                        return true;
                    }
                    return it.getTileItem().getQuantity() * itemManager.getItemPrice(id) > threshold;
                })
                .nearestToPlayer();

        if (!target.isPresent()) {
            return false;
        }

        ETileItem item = target.get();
        int itemId = item.getTileItem().getId();
        int qty = item.getTileItem().getQuantity();
        int stackValue = qty * itemManager.getItemPrice(itemId);

        if (!MenuActionInteractions.takeGroundItem(item)) {
            return false; // item is off the loaded scene
        }
        log("Looting '" + itemName(itemId) + "' x" + qty + " (value=" + stackValue + ") at " + item.getLocation());
        return true;
    }

    private String itemName(int itemId) {
        return itemManager.getItemComposition(itemId).getName();
    }

    private static void parseItemList(String cfg, Set<Integer> ids, Set<String> names) {
        if (cfg == null) {
            return;
        }
        for (String raw : cfg.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.chars().allMatch(Character::isDigit)) {
                ids.add(Integer.parseInt(entry));
            } else {
                names.add(entry.toLowerCase());
            }
        }
    }

    private boolean isInCombat() {
        Actor interacting = client.getLocalPlayer().getInteracting();
        if (!(interacting instanceof NPC)) {
            return false;
        }
        NPC npc = (NPC) interacting;
        // healthRatio == 0 means dead; -1 means no health bar shown (untouched NPC, still alive)
        return config.monster().matchesId(npc.getId()) && npc.getHealthRatio() != 0;
    }

    private void attackNearestTarget() {
        SlayerCombatConfig.Monster monster = config.monster();
        Player local = client.getLocalPlayer();

        // Prefer a monster that is already attacking us. It's the real threat, it's
        // adjacent/reachable, and locking onto it stops us thrashing toward a different
        // (possibly unreachable) NPC while one is already on us. Fall back to nearest.
        Optional<NPC> target = NPCs.search()
                .idInList(monster.npcIdList())
                .alive()
                .filter(npc -> npc.getInteracting() == local)
                .nearestToPlayer();

        if (!target.isPresent()) {
            target = NPCs.search()
                    .idInList(monster.npcIdList())
                    .alive()
                    .nearestToPlayer();
        }

        if (!target.isPresent()) {
            log("No alive target with id in " + monster.npcIdList() + " found nearby");
            return;
        }

        NPC npc = target.get();

        // Resolve which menu option index the attack action is, then dispatch via the
        // shared menuAction helper (don't assume NPC_FIRST_OPTION — e.g. Kurask's Attack
        // is the second option).
        int optionIndex = MenuActionInteractions.npcOptionIndex(npc, monster.attackAction);
        if (optionIndex < 1 || optionIndex > 5) {
            NPCComposition comp = NPCQuery.getNPCComposition(npc);
            log("Could not resolve '" + monster.attackAction + "' on " + npc.getName()
                    + " actions=" + Arrays.toString(comp != null ? comp.getActions() : null));
            return;
        }

        log("Attacking " + npc.getName() + " index=" + npc.getIndex()
                + " op=" + optionIndex + " (" + MenuActionInteractions.npcOptionMenuAction(optionIndex) + ")");
        MenuActionInteractions.interactNpc(npc, monster.attackAction);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[SlayerCombat] " + message);
        }
    }
}
