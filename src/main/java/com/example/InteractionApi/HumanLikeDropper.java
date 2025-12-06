package com.example.InteractionApi;

import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import net.runelite.api.widgets.Widget;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Human-like item dropper that mimics realistic shift-drop behavior:
 * - Drops items in zigzag column pattern (column 0 down, column 1 up, column 2 down, etc.)
 * - Randomly "misses" some items during initial pass and goes back for them later
 * - Drops 2-5 items per tick (human-like speed)
 *
 * Usage:
 * 1. Create a new instance when entering drop state
 * 2. Call dropNextBatch() each tick
 * 3. When dropNextBatch() returns false, all items are dropped
 */
public class HumanLikeDropper {

    private final Set<Integer> missedIndices = new HashSet<>();
    private boolean initialized = false;
    private final Predicate<Widget> itemFilter;

    /**
     * Create a dropper for specific item IDs
     * @param itemIds Item IDs to drop
     */
    public HumanLikeDropper(int... itemIds) {
        this.itemFilter = widget -> {
            for (int id : itemIds) {
                if (widget.getItemId() == id) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Create a dropper for specific item IDs (Collection variant)
     * @param itemIds Item IDs to drop
     */
    public HumanLikeDropper(Collection<Integer> itemIds) {
        Set<Integer> idSet = new HashSet<>(itemIds);
        this.itemFilter = widget -> idSet.contains(widget.getItemId());
    }

    /**
     * Create a dropper with custom filter predicate
     * @param itemFilter Predicate to determine which items to drop
     */
    public HumanLikeDropper(Predicate<Widget> itemFilter) {
        this.itemFilter = itemFilter;
    }

    /**
     * Drop the next batch of items (2-5 items per call)
     * @return true if more items remain to drop, false if all items are dropped
     */
    public boolean dropNextBatch() {
        // Get all items matching the filter
        List<Widget> allItems = Inventory.search()
                .filter(itemFilter)
                .result();

        // No items to drop - we're done
        if (allItems.isEmpty()) {
            return false;
        }

        // First time - randomly select which items to "miss" (0-5 items)
        if (!initialized) {
            int numToMiss = ThreadLocalRandom.current().nextInt(0, Math.min(6, allItems.size() + 1));
            if (numToMiss > 0) {
                // Randomly pick which item indices to skip
                List<Widget> shuffled = new ArrayList<>(allItems);
                Collections.shuffle(shuffled);
                for (int i = 0; i < numToMiss; i++) {
                    missedIndices.add(shuffled.get(i).getIndex());
                }
            }
            initialized = true;
        }

        // Sort by zigzag pattern (mimics shift-drop: column 0 down, column 1 up, column 2 down, etc.)
        // Inventory layout: 4 columns x 7 rows (indices 0-27)
        allItems.sort((w1, w2) -> {
            int idx1 = w1.getIndex();
            int idx2 = w2.getIndex();

            // Calculate column (0-3) and row (0-6) from inventory index
            int col1 = idx1 % 4;
            int row1 = idx1 / 4;
            int col2 = idx2 % 4;
            int row2 = idx2 / 4;

            // First priority: compare by column (left to right)
            if (col1 != col2) {
                return Integer.compare(col1, col2);
            }

            // Same column: drop direction depends on column number
            if (col1 % 2 == 0) {
                // Even columns (0, 2): top to bottom
                return Integer.compare(row1, row2);
            } else {
                // Odd columns (1, 3): bottom to top
                return Integer.compare(row2, row1);
            }
        });

        // Separate items into "missed" and "normal" lists
        List<Widget> normalItems = new ArrayList<>();
        List<Widget> missedItems = new ArrayList<>();

        for (Widget item : allItems) {
            if (missedIndices.contains(item.getIndex())) {
                missedItems.add(item);
            } else {
                normalItems.add(item);
            }
        }

        List<Widget> itemsToDrop;

        // If only missed items remain, we're in cleanup phase
        if (normalItems.isEmpty() && !missedItems.isEmpty()) {
            // Cleanup phase: drop missed items at human speed (2-5 per tick)
            int dropsThisTick = ThreadLocalRandom.current().nextInt(2, 6);
            dropsThisTick = Math.min(dropsThisTick, missedItems.size());

            itemsToDrop = missedItems.stream()
                    .limit(dropsThisTick)
                    .collect(Collectors.toList());
        } else if (!normalItems.isEmpty()) {
            // Normal phase: drop normal items (skip missed ones)
            int dropsThisTick = ThreadLocalRandom.current().nextInt(2, 6);
            dropsThisTick = Math.min(dropsThisTick, normalItems.size());

            itemsToDrop = normalItems.stream()
                    .limit(dropsThisTick)
                    .collect(Collectors.toList());
        } else {
            return false; // Nothing to drop
        }

        // Queue all drops for this tick
        for (Widget item : itemsToDrop) {
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(item, "Drop");
        }

        return true; // More items may remain
    }

    /**
     * Check if there are any items left to drop
     * @return true if items matching the filter exist in inventory
     */
    public boolean hasItemsRemaining() {
        return Inventory.search()
                .filter(itemFilter)
                .first()
                .isPresent();
    }

    /**
     * Reset the dropper state (useful if you want to reuse the same instance)
     */
    public void reset() {
        missedIndices.clear();
        initialized = false;
    }
}
