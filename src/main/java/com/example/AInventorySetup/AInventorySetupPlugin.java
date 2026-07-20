package com.example.AInventorySetup;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.InteractionApi.MenuActionInteractions;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.example.PiggyUtils.API.BankUtil;
import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = "A Inventory Setup",
        description = "Save inventory snapshots as named templates and refill them from the bank between boss trips",
        tags = {"inventory", "bank", "setup", "loadout", "template"},
        hidden = false
)
public class AInventorySetupPlugin extends Plugin {

    private static final String CONFIG_GROUP = "AInventorySetup";
    private static final String TEMPLATES_KEY = "templates";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private AInventorySetupConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    private AInventorySetupPanel panel;
    private NavigationButton navButton;

    private final Map<String, InventoryTemplate> templates = new LinkedHashMap<>();

    private enum ApplyState {
        IDLE,
        OPENING_BANK,
        DEPOSITING,
        WITHDRAWING
    }

    private ApplyState state = ApplyState.IDLE;
    private String pendingTemplateName;
    // One entry per occupied template slot, in ascending slot order (not merged by item id) - see
    // buildWithdrawPlan(). Withdrawing in this order, rather than grouping every occurrence of an
    // id together, is what makes the final inventory land in the same slots as the template: the
    // bank always fills the next empty inventory slot, so slot order in == slot order out.
    private List<int[]> pendingItems; // {itemId, cumulativeQtyNeededSoFar}
    private int pendingIndex;
    private int ticksInState;
    private int itemTicks;

    @Provides
    AInventorySetupConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AInventorySetupConfig.class);
    }

    private void log(String message) {
        if (config.debugLogging()) {
            System.out.println("[AInventorySetup] " + message);
        }
    }

    private void logAlways(String message) {
        System.out.println("[AInventorySetup] " + message);
    }

    @Override
    protected void startUp() {
        loadTemplates();

        panel = new AInventorySetupPanel(this, clientThread);
        navButton = NavigationButton.builder()
                .tooltip("Inventory Setup")
                .icon(createIcon())
                .priority(5)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        panel.refresh();
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        state = ApplyState.IDLE;
        pendingTemplateName = null;
        pendingItems = null;
    }

    private static BufferedImage createIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(65, 65, 65));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.drawString("IS", 6, 21);
        g.dispose();
        return img;
    }

    public List<String> getTemplateNames() {
        return templates.keySet().stream().sorted().collect(Collectors.toList());
    }

    public boolean hasTemplate(String name) {
        return templates.containsKey(name);
    }

    /** Must be called on the client thread — reads the live inventory container. */
    public void captureAndSaveTemplate(String name) {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        InventoryTemplate template = new InventoryTemplate(name);
        if (container != null) {
            Item[] items = container.getItems();
            int used = 0;
            for (int i = 0; i < InventoryTemplate.SIZE && i < items.length; i++) {
                Item item = items[i];
                if (item != null && item.getId() != -1 && item.getQuantity() > 0) {
                    template.itemIds[i] = item.getId();
                    template.quantities[i] = item.getQuantity();
                    used++;
                }
            }
            logAlways("Saved template '" + name + "' (" + used + " items)");
        }
        templates.put(name, template);
        persistTemplates();
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.refresh();
            }
        });
    }

    public void deleteTemplate(String name) {
        templates.remove(name);
        persistTemplates();
    }

    /** Must be called on the client thread — starts the deposit/withdraw state machine. */
    public void startApplyTemplate(String name) {
        InventoryTemplate template = templates.get(name);
        if (template == null) {
            logAlways("No such template: " + name);
            return;
        }
        if (state != ApplyState.IDLE) {
            logAlways("Already applying a template, ignoring request");
            return;
        }
        pendingTemplateName = name;
        pendingItems = buildWithdrawPlan(template);
        pendingIndex = 0;
        ticksInState = 0;
        itemTicks = 0;
        state = Bank.isOpen() ? ApplyState.DEPOSITING : ApplyState.OPENING_BANK;
        logAlways("Applying template '" + name + "'");
    }

    /**
     * Walks the template slots in ascending order (0..27) and emits one entry per occupied slot,
     * each carrying the *cumulative* target quantity for that item id up to and including this
     * slot. Repeated non-stackable items (e.g. 4 prayer potions at 4 different slots) therefore
     * become 4 separate entries interleaved with whatever other slots sit between them in the
     * template, instead of one entry withdrawn all at once - matching how withdrawing "as you go"
     * fills the inventory left-to-right in the same order the template's items appear in.
     */
    private List<int[]> buildWithdrawPlan(InventoryTemplate template) {
        Map<Integer, Integer> runningTotal = new LinkedHashMap<>();
        List<int[]> plan = new ArrayList<>();
        for (int i = 0; i < InventoryTemplate.SIZE; i++) {
            int id = template.itemIds[i];
            if (id == -1) {
                continue;
            }
            int cumulative = runningTotal.merge(id, template.quantities[i], Integer::sum);
            plan.add(new int[]{id, cumulative});
        }
        return plan;
    }

    private void persistTemplates() {
        JSONObject root = new JSONObject();
        for (InventoryTemplate t : templates.values()) {
            JSONObject obj = new JSONObject();
            obj.put("itemIds", new JSONArray(t.itemIds));
            obj.put("quantities", new JSONArray(t.quantities));
            root.put(t.name, obj);
        }
        configManager.setConfiguration(CONFIG_GROUP, TEMPLATES_KEY, root.toString());
    }

    private void loadTemplates() {
        templates.clear();
        String json = configManager.getConfiguration(CONFIG_GROUP, TEMPLATES_KEY);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            for (String name : root.keySet()) {
                JSONObject obj = root.getJSONObject(name);
                InventoryTemplate t = new InventoryTemplate(name);
                JSONArray ids = obj.getJSONArray("itemIds");
                JSONArray quantities = obj.getJSONArray("quantities");
                for (int i = 0; i < InventoryTemplate.SIZE; i++) {
                    t.itemIds[i] = ids.getInt(i);
                    t.quantities[i] = quantities.getInt(i);
                }
                templates.put(name, t);
            }
        } catch (Exception e) {
            logAlways("Failed to load templates: " + e.getMessage());
        }
    }

    private int currentInventoryQuantity(int itemId) {
        return Inventory.search().withId(itemId).result().stream()
                .mapToInt(Widget::getItemQuantity)
                .sum();
    }

    private boolean tryOpenBank() {
        Optional<TileObject> bankObj = TileObjects.search()
                .withAction("Bank")
                .withinDistance(config.searchDistance())
                .nearestToPlayer();
        return bankObj.isPresent() && MenuActionInteractions.interactObject(bankObj.get(), "Bank");
    }

    /**
     * Chunks toward the needed amount using the bank's real quantity-selector presets (10/5/1)
     * so large quantities converge in a few ticks instead of one-at-a-time; anything left over
     * falls back to "Withdraw-1" rather than automating the chat-input "Withdraw-X" dialog,
     * which needs real keystroke simulation this codebase doesn't do anywhere else.
     */
    private String pickWithdrawAction(int needed, int availableInBank) {
        int cap = Math.min(needed, availableInBank);
        if (needed >= availableInBank) {
            return "Withdraw-All";
        }
        if (cap >= 10) {
            return "Withdraw-10";
        }
        if (cap >= 5) {
            return "Withdraw-5";
        }
        return "Withdraw-1";
    }

    private void advanceWithdraw() {
        while (pendingIndex < pendingItems.size()) {
            int[] entry = pendingItems.get(pendingIndex);
            int itemId = entry[0];
            int targetQty = entry[1];
            int have = currentInventoryQuantity(itemId);

            if (have >= targetQty) {
                // Already satisfied by an earlier withdrawal of this same item id (e.g. a
                // "Withdraw-5" for slot 1 already covers what slot 3 needs) - advance immediately
                // rather than burning a tick, so interleaved duplicates don't stall the sequence.
                pendingIndex++;
                itemTicks = 0;
                continue;
            }

            itemTicks++;
            if (itemTicks > config.withdrawTimeoutTicks()) {
                logAlways("Warning: only got " + have + "/" + targetQty + " of item " + itemId + " - skipping");
                pendingIndex++;
                itemTicks = 0;
                continue;
            }

            Optional<Widget> bankItem = Bank.search().withId(itemId).first();
            if (!bankItem.isPresent()) {
                logAlways("Warning: item " + itemId + " not found in bank - skipping");
                pendingIndex++;
                itemTicks = 0;
                continue;
            }

            Widget item = bankItem.get();
            int needed = targetQty - have;
            String action = pickWithdrawAction(needed, item.getItemQuantity());
            log("Withdrawing item " + itemId + " via '" + action + "' (have " + have + "/" + targetQty + ")");
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(item, action);
            return;
        }

        logAlways("Template '" + pendingTemplateName + "' applied");
        state = ApplyState.IDLE;
        pendingTemplateName = null;
        pendingItems = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (state == ApplyState.IDLE || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        ticksInState++;

        switch (state) {
            case OPENING_BANK:
                if (Bank.isOpen()) {
                    state = ApplyState.DEPOSITING;
                    ticksInState = 0;
                    return;
                }
                if (ticksInState > 20) {
                    logAlways("Could not open a bank within " + config.searchDistance()
                            + " tiles - stopping. Stand next to a bank booth/chest and try again.");
                    state = ApplyState.IDLE;
                    pendingTemplateName = null;
                    pendingItems = null;
                    return;
                }
                if (ticksInState == 1 || ticksInState % 3 == 0) {
                    tryOpenBank();
                }
                break;

            case DEPOSITING:
                if (Inventory.getEmptySlots() == InventoryTemplate.SIZE) {
                    pendingIndex = 0;
                    itemTicks = 0;
                    state = ApplyState.WITHDRAWING;
                    ticksInState = 0;
                    return;
                }
                if (ticksInState > 10) {
                    logAlways("Deposit-all did not clear the inventory in time - stopping");
                    state = ApplyState.IDLE;
                    pendingTemplateName = null;
                    pendingItems = null;
                    return;
                }
                if (ticksInState == 1) {
                    BankUtil.depositAll();
                }
                break;

            case WITHDRAWING:
                advanceWithdraw();
                break;

            case IDLE:
                break;
        }
    }
}
