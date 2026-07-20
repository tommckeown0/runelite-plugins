package com.example.AInventorySetup;

import java.util.Arrays;

/**
 * A saved snapshot of the 28 inventory slots. {@code itemIds[i] == -1} means slot i is empty.
 * Array index is the inventory slot, so position is preserved implicitly.
 */
public class InventoryTemplate {
    public static final int SIZE = 28;

    public final String name;
    public final int[] itemIds = new int[SIZE];
    public final int[] quantities = new int[SIZE];

    public InventoryTemplate(String name) {
        this.name = name;
        Arrays.fill(itemIds, -1);
    }
}
