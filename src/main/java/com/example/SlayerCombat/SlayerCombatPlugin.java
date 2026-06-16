package com.example.SlayerCombat;

import com.example.EthanApiPlugin.Collections.ETileItem;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.TileItems;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.NPCQuery;
import com.example.InteractionApi.InventoryInteraction;
import com.example.InteractionApi.MenuActionInteractions;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
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

    // Cannon mode: names the assembled cannon object and the inventory part we "Set-up".
    private static final String CANNON_OBJECT_NAME = "Dwarven multicannon";
    private static final String CANNON_BASE_NAME = "Cannon base";

    @Provides
    SlayerCombatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SlayerCombatConfig.class);
    }

    @Override
    protected void startUp() {
        tickDelay = 0;
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
        SlayerCombatConfig.Monster monster = config.monster();
        WorldPoint setupTile = new WorldPoint(config.cannonSetupX(), config.cannonSetupY(), client.getPlane());
        WorldPoint fightTile = new WorldPoint(config.cannonFightX(), config.cannonFightY(), client.getPlane());
        WorldPoint pos = client.getLocalPlayer().getWorldLocation();

        // 1. Locate our assembled cannon (it's the only object offering "Fire").
        Optional<TileObject> cannon = TileObjects.search()
                .withName(CANNON_OBJECT_NAME)
                .withAction("Fire")
                .nearestToPlayer();

        // 2. No cannon up yet → get on the setup tile and "Set-up" the base from the inventory.
        if (!cannon.isPresent()) {
            if (!pos.equals(setupTile)) {
                log("Cannon not placed; walking to setup tile " + setupTile);
                MenuActionInteractions.walkTo(setupTile);
                tickDelay = 2;
                return;
            }
            log("On setup tile; setting up cannon base");
            if (InventoryInteraction.useItem(CANNON_BASE_NAME, "Set-up")) {
                // Assembling + auto-filling takes a few seconds; don't poke it meanwhile.
                tickDelay = 8;
            } else {
                log("No '" + CANNON_BASE_NAME + "' with a 'Set-up' action in inventory");
            }
            return;
        }

        // 3. Refill when the loaded count is low. 'Fire' on the cannon tops it up from inventory.
        int balls = CannonTracker.cballsLeft();
        if (balls < 0) {
            log("Cannonball count unavailable — is the built-in Cannon plugin enabled? fields=["
                    + CannonTracker.describeFields() + "]");
        } else if (balls < config.cannonballThreshold()) {
            log("Cannonballs low (" + balls + " < " + config.cannonballThreshold() + "); firing/refilling");
            if (MenuActionInteractions.interactObject(cannon.get(), "Fire")) {
                tickDelay = 2;
            }
            return;
        }

        // 4. Get back onto the fight tile if we've drifted (e.g. after looting), but don't
        //    reposition mid-fight — let auto-retaliate keep working.
        if (!pos.equals(fightTile) && !isInCombat()) {
            log("Returning to fight tile " + fightTile);
            MenuActionInteractions.walkTo(fightTile);
            tickDelay = 2;
            return;
        }

        // 5. Loot valuable drops when we're not actively being hit.
        if (config.enableLooting() && !isInCombat() && lootNearestValuable()) {
            tickDelay = 2;
            return;
        }

        // Otherwise idle on the fight tile and let the cannon + auto-retaliate do the work.
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
