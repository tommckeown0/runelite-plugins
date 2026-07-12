package com.example.Prayer;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.NPCs;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.InteractionApi.HumanLikeDelay;
import com.example.InteractionApi.MenuActionInteractions;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

@PluginDescriptor(
        name = "A Prayer Training",
        description = "Automatically offers bones at gilded altar",
        tags = {"prayer", "training", "gilded", "altar", "bones"},
        hidden = false,
        enabledByDefault = false
)
public class PrayerTrainingPlugin extends Plugin {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private PrayerConfig config;

    // Game object/NPC IDs
    private static final int DRAGON_BONES_ID = 536;           // Unnoted dragon bones
    private static final int NOTED_DRAGON_BONES_ID = 537;     // Noted dragon bones
    private static final int HOUSE_PORTAL_ID = 4525;
    private static final int GILDED_ALTAR_ID = 13197;
    private static final int PHIALS_NPC_ID = 1614;
    private static final int HOUSE_ADVERTISEMENT_ID = 29091;

    // House Advertisement's exact wording isn't verified in-game yet — tried in this order,
    // first one present on the object's composition wins. If none match, the actual action
    // list is logged so the real string can be added here.
    private static final String[] HOUSE_ADVERTISEMENT_TELEPORT_ACTIONS = {"Visit-Last", "Last", "Visit"};

    // Animation IDs
    private static final int OFFERING_ANIMATION = 3705;
    private static final int IDLE_ANIMATION = -1;

    // State machine
    private enum PrayerState {
        IDLE,
        TRAVELING_TO_PHIALS,
        SELECTING_CHAT_OPTION,
        WAITING_AFTER_EXCHANGE,
        TELEPORTING_TO_HOUSE,
        TRAVELING_TO_ALTAR,
        OFFERING_BONES,
        WAITING_AFTER_OFFERING,
        RETURNING_TO_RIMMINGTON
    }

    private PrayerState currentState = PrayerState.IDLE;
    private int ticksInCurrentState = 0;
    private int delayTicksRemaining = 0;
    private int idleTicksWhileOffering = 0;
    private boolean wasOffering = false;
    private boolean needsInitialStateDetection = false;
    private boolean actionExecutedForCurrentState = false;

    @Provides
    PrayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PrayerConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.println("[" + timestamp + "][Prayer] " + message);
        }
    }

    private void logAlways(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "][Prayer] " + message);
    }

    private void errorAndStop(String error) {
        logAlways("ERROR: " + error);
        logAlways("Plugin stopped due to error - please fix and restart");
        currentState = PrayerState.IDLE;
        ticksInCurrentState = 0;
    }

    @Override
    protected void startUp() {
        logAlways("Prayer plugin started");

        // Reset state
        currentState = PrayerState.IDLE;
        ticksInCurrentState = 0;
        delayTicksRemaining = 0;
        needsInitialStateDetection = true;

        logAlways("Will detect initial state on first game tick");
    }

    @Override
    protected void shutDown() {
        logAlways("Prayer plugin stopped");
        currentState = PrayerState.IDLE;
        ticksInCurrentState = 0;
        needsInitialStateDetection = false;
        actionExecutedForCurrentState = false;
    }

    /**
     * Detect which state we should start in based on current game state
     */
    private PrayerState detectCurrentState() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return PrayerState.IDLE;
        }

        WorldPoint location = player.getWorldLocation();
        boolean hasAnyBonesInv = hasAnyBones();
        boolean isChatOpen = isChatDialogOpen();

        log("Initial state detection:");
        log("  Location: " + location);
        log("  Has any bones: " + hasAnyBonesInv);
        log("  Has unnoted bones: " + hasUnnotedBones());
        log("  Has noted bones: " + hasNotedBones());
        log("  Chat open: " + isChatOpen);
        log("  In POH: " + isInPOH());

        // Check conditions in priority order
        if (isChatOpen) {
            log("  -> SELECTING_CHAT_OPTION (chat dialog is open)");
            return PrayerState.SELECTING_CHAT_OPTION;
        }

        if (isInPOH()) {
            if (hasUnnotedBones()) {
                log("  -> TRAVELING_TO_ALTAR (in POH with unnoted bones)");
                return PrayerState.TRAVELING_TO_ALTAR;
            } else {
                log("  -> RETURNING_TO_RIMMINGTON (in POH without unnoted bones)");
                return PrayerState.RETURNING_TO_RIMMINGTON;
            }
        }

        // Assume we're in Rimmington or similar location
        if (hasUnnotedBones()) {
            log("  -> TELEPORTING_TO_HOUSE (have unnoted bones, not in POH)");
            return PrayerState.TELEPORTING_TO_HOUSE;
        } else {
            log("  -> TRAVELING_TO_PHIALS (no unnoted bones, not in POH)");
            return PrayerState.TRAVELING_TO_PHIALS;
        }
    }

    private boolean hasUnnotedBones() {
        return Inventory.search().withId(DRAGON_BONES_ID).first().isPresent();
    }

    private boolean hasNotedBones() {
        return Inventory.search().withId(NOTED_DRAGON_BONES_ID).first().isPresent();
    }

    private boolean hasAnyBones() {
        return hasUnnotedBones() || hasNotedBones();
    }

    private boolean isChatDialogOpen() {
        // Check for dialog widget - we'll need to find the correct widget ID
        // For now, return false - will need to debug this in-game
        // TODO: Find the correct widget ID for Phials chat dialog
        Widget chatDialog = client.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
        return chatDialog != null && !chatDialog.isHidden();
    }

    private boolean isInPOH() {
        // POH is typically in regions 7500s-7700s range
        // Can also check for specific objects like house portal or altar
        return TileObjects.search()
                .withId(HOUSE_PORTAL_ID)
                .withinDistance(20)
                .first()
                .isPresent();
    }

    private int getCurrentAnimation() {
        Player player = client.getLocalPlayer();
        return player != null ? player.getAnimation() : -1;
    }

    private boolean isOffering() {
        return getCurrentAnimation() == OFFERING_ANIMATION;
    }

    /**
     * Transition to a new state with an appropriate delay
     */
    private void transitionToState(PrayerState newState, HumanLikeDelay.Profile delayProfile) {
        log("State transition: " + currentState + " -> " + newState);
        currentState = newState;
        ticksInCurrentState = 0;
        delayTicksRemaining = HumanLikeDelay.generate(delayProfile);
        actionExecutedForCurrentState = false;
        log("  Generated delay: " + delayTicksRemaining + " ticks");
    }

    /**
     * Execute the action for entering the current state
     */
    private void executeStateAction() {
        logAlways(">>> Executing action for state: " + currentState);

        switch (currentState) {
            case TRAVELING_TO_PHIALS:
                interactWithPhials();
                break;

            case SELECTING_CHAT_OPTION:
                selectChatOption();
                break;

            case TELEPORTING_TO_HOUSE:
                teleportToHouse();
                break;

            case TRAVELING_TO_ALTAR:
                useBonesOnAltar();
                break;

            case RETURNING_TO_RIMMINGTON:
                clickHousePortal();
                break;

            case WAITING_AFTER_EXCHANGE:
            case WAITING_AFTER_OFFERING:
            case OFFERING_BONES:
            case IDLE:
                // These states don't have entry actions
                break;
        }
    }

    private void interactWithPhials() {
        // First, get the NOTED bones widget from inventory
        Optional<Widget> bonesWidget = Inventory.search().withId(NOTED_DRAGON_BONES_ID).first();
        if (bonesWidget.isEmpty()) {
            errorAndStop("No noted dragon bones found in inventory when trying to use on Phials");
            return;
        }

        // Find Phials NPC
        Optional<NPC> phials = NPCs.search().withId(PHIALS_NPC_ID).nearestToPlayer();
        if (phials.isEmpty()) {
            errorAndStop("Cannot find Phials NPC (ID: " + PHIALS_NPC_ID + ")");
            return;
        }

        Widget bw = bonesWidget.get();
        NPC npc = phials.get();
        logAlways("DIAG phials itemId=" + bw.getItemId() + " widgetId=" + bw.getId()
                + " slot=" + bw.getIndex() + " npcIndex=" + npc.getIndex());

        if (MenuActionInteractions.useItemOnNpc(bw, npc)) {
            logAlways("Used noted bones on Phials");
        } else {
            errorAndStop("Could not use noted bones on Phials");
        }
    }

    private void selectChatOption() {
        // Select the "Exchange all" option. Dialog options are NOT CC_OP buttons — a real click
        // dispatches WIDGET_CONTINUE (op 30), which sends the RESUME_PAUSEBUTTON packet
        // (jf.ep on 1.12.31.1: widget id + child index). CC_OP here was a silent no-op.
        Widget dialogOptions = client.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
        if (dialogOptions == null || dialogOptions.isHidden()) {
            log("Chat dialog not visible yet, waiting...");
            return;
        }

        Widget[] options = dialogOptions.getChildren();
        if (options == null) {
            errorAndStop("Dialog options widget has no children");
            return;
        }

        // Child 0 is the "Select an Option" header, so match by text rather than index.
        // Live text observed 2026-07-12: "Exchange All: 125 coins." (third option) — prefix
        // match so the varying coin amount doesn't matter.
        Widget option = null;
        for (Widget child : options) {
            String text = child != null ? child.getText() : null;
            if (text != null && text.toLowerCase().startsWith("exchange al")) {
                option = child;
                break;
            }
        }
        if (option == null) {
            StringBuilder texts = new StringBuilder();
            for (Widget child : options) {
                texts.append(child != null ? "'" + child.getText() + "' " : "null ");
            }
            errorAndStop("No 'Exchange All' option found among: " + texts);
            return;
        }

        client.menuAction(option.getIndex(), option.getId(), MenuAction.WIDGET_CONTINUE,
                0, -1, "Continue", "");
        logAlways("Selected dialog option " + option.getIndex() + " (" + option.getText() + ")");
    }

    private void teleportToHouse() {
        Optional<TileObject> advertisement = TileObjects.search()
                .withId(HOUSE_ADVERTISEMENT_ID)
                .withinDistance(config.searchDistance())
                .first();

        if (advertisement.isEmpty()) {
            errorAndStop("Cannot find house advertisement (ID: " + HOUSE_ADVERTISEMENT_ID + ")");
            return;
        }

        TileObject board = advertisement.get();
        for (String action : HOUSE_ADVERTISEMENT_TELEPORT_ACTIONS) {
            if (MenuActionInteractions.interactObject(board, action)) {
                logAlways("Clicked house advertisement '" + action + "'");
                return;
            }
        }

        ObjectComposition comp = TileObjectQuery.getObjectComposition(board);
        String actions = comp != null && comp.getActions() != null
                ? Arrays.toString(comp.getActions())
                : "<no composition>";
        errorAndStop("House advertisement has none of " + Arrays.toString(HOUSE_ADVERTISEMENT_TELEPORT_ACTIONS)
                + " — actual actions: " + actions);
    }

    private void useBonesOnAltar() {
        // First, get the bones widget from inventory
        Optional<Widget> bonesWidget = Inventory.search().withId(DRAGON_BONES_ID).first();
        if (bonesWidget.isEmpty()) {
            errorAndStop("No dragon bones found in inventory when trying to use on altar");
            return;
        }

        // Find the gilded altar
        Optional<TileObject> altar = TileObjects.search()
                .withId(GILDED_ALTAR_ID)
                .withinDistance(config.searchDistance())
                .first();

        if (altar.isEmpty()) {
            errorAndStop("Cannot find gilded altar (ID: " + GILDED_ALTAR_ID + ")");
            return;
        }

        Widget bw = bonesWidget.get();
        TileObject altarObj = altar.get();
        WorldPoint wp = altarObj.getWorldLocation();
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        logAlways("DIAG altar itemId=" + bw.getItemId() + " widgetId=" + bw.getId()
                + " slot=" + bw.getIndex() + " objectId=" + altarObj.getId()
                + " worldX=" + wp.getX() + " worldY=" + wp.getY()
                + " sceneX=" + (lp != null ? lp.getSceneX() : -1)
                + " sceneY=" + (lp != null ? lp.getSceneY() : -1));

        if (MenuActionInteractions.useItemOnObject(bw, altarObj)) {
            logAlways("Used bones on altar");
        } else {
            errorAndStop("Could not use bones on altar");
        }
    }

    private void clickHousePortal() {
        Optional<TileObject> portal = TileObjects.search()
                .withId(HOUSE_PORTAL_ID)
                .withinDistance(config.searchDistance())
                .first();

        if (portal.isEmpty()) {
            errorAndStop("Cannot find house portal (ID: " + HOUSE_PORTAL_ID + ")");
            return;
        }

        if (MenuActionInteractions.interactObject(portal.get(), "Enter")) {
            logAlways("Clicked house portal 'Enter'");
        } else {
            errorAndStop("Could not interact with house portal (ID: " + HOUSE_PORTAL_ID + ")");
        }
    }

    /**
     * Check if the current state's completion condition is met
     */
    private boolean isStateComplete() {
        switch (currentState) {
            case IDLE:
                return false; // Never auto-complete

            case TRAVELING_TO_PHIALS:
                // Complete when chat dialog opens
                return isChatDialogOpen();

            case SELECTING_CHAT_OPTION:
                // Complete when we have unnoted bones in inventory
                return hasUnnotedBones();

            case WAITING_AFTER_EXCHANGE:
                // Complete when delay expires
                return delayTicksRemaining <= 0;

            case TELEPORTING_TO_HOUSE:
                // Complete when we're in POH
                return isInPOH();

            case TRAVELING_TO_ALTAR:
                // Complete when offering animation starts
                return isOffering();

            case OFFERING_BONES:
                // Complete when inventory is empty OR idle for too long (level up case)
                if (!hasUnnotedBones()) {
                    log("Offering complete: inventory empty");
                    return true;
                }

                // Check for level-up interrupt (idle while still having bones)
                if (!isOffering() && idleTicksWhileOffering > 2 && hasUnnotedBones()) {
                    logAlways("Detected offering interruption (level up?), will re-click altar");
                    return true; // Will transition back to TRAVELING_TO_ALTAR
                }

                return false;

            case WAITING_AFTER_OFFERING:
                // Complete when delay expires
                return delayTicksRemaining <= 0;

            case RETURNING_TO_RIMMINGTON:
                // Complete when we're no longer in POH
                return !isInPOH();

            default:
                return false;
        }
    }

    /**
     * Get the next state after current state completes
     */
    private PrayerState getNextState() {
        switch (currentState) {
            case TRAVELING_TO_PHIALS:
                return PrayerState.SELECTING_CHAT_OPTION;

            case SELECTING_CHAT_OPTION:
                return PrayerState.WAITING_AFTER_EXCHANGE;

            case WAITING_AFTER_EXCHANGE:
                return PrayerState.TELEPORTING_TO_HOUSE;

            case TELEPORTING_TO_HOUSE:
                return PrayerState.TRAVELING_TO_ALTAR;

            case TRAVELING_TO_ALTAR:
                return PrayerState.OFFERING_BONES;

            case OFFERING_BONES:
                // Special case: if interrupted with bones remaining, go back to altar
                if (hasUnnotedBones()) {
                    return PrayerState.TRAVELING_TO_ALTAR;
                }
                return PrayerState.WAITING_AFTER_OFFERING;

            case WAITING_AFTER_OFFERING:
                return PrayerState.RETURNING_TO_RIMMINGTON;

            case RETURNING_TO_RIMMINGTON:
                return PrayerState.TRAVELING_TO_PHIALS;

            default:
                return PrayerState.IDLE;
        }
    }

    /**
     * Get the delay profile to use when transitioning from current state
     */
    private HumanLikeDelay.Profile getDelayProfile() {
        switch (currentState) {
            case WAITING_AFTER_OFFERING:
                // Longer delay after offering (AFK likelihood)
                return HumanLikeDelay.ITEM_DROP;

            default:
                // Short delay for all other transitions
                return HumanLikeDelay.INTERACTION_COOLDOWN;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() != client.getLocalPlayer()) {
            return;
        }

        boolean currentlyOffering = isOffering();

        if (currentlyOffering) {
            if (!wasOffering) {
                logAlways("Started offering bones (animation: " + OFFERING_ANIMATION + ")");
            }
            wasOffering = true;
            idleTicksWhileOffering = 0;
        }
        // Note: Don't log when animation stops - it briefly goes idle between each bone
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Perform initial state detection on first tick
        if (needsInitialStateDetection) {
            needsInitialStateDetection = false;
            currentState = detectCurrentState();
            ticksInCurrentState = 0;
            delayTicksRemaining = 0; // No delay when starting
            actionExecutedForCurrentState = false;
            logAlways("Detected initial state: " + currentState);
        }

        if (currentState == PrayerState.IDLE) {
            return;
        }

        ticksInCurrentState++;

        // Timeout detection
        if (ticksInCurrentState > config.stateTimeout()) {
            errorAndStop("Timeout: stuck in state " + currentState + " for " + ticksInCurrentState + " ticks");
            return;
        }

        // Track idle ticks while offering
        if (currentState == PrayerState.OFFERING_BONES) {
            if (!isOffering()) {
                idleTicksWhileOffering++;
            } else {
                idleTicksWhileOffering = 0;
            }
        } else {
            idleTicksWhileOffering = 0;
        }

        // Handle delay countdown
        if (delayTicksRemaining > 0) {
            delayTicksRemaining--;
            log("Delay remaining: " + delayTicksRemaining + " ticks");
        }

        // Execute action after delay completes (if not already executed)
        if (delayTicksRemaining == 0 && !actionExecutedForCurrentState) {
            executeStateAction();
            actionExecutedForCurrentState = true;
        }

        // Check if state is complete
        if (isStateComplete()) {
            PrayerState nextState = getNextState();
            HumanLikeDelay.Profile delayProfile = getDelayProfile();
            transitionToState(nextState, delayProfile);
        }
    }
}
