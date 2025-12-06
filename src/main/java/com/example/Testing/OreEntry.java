package com.example.Testing;

import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = "A Test",
        description = "Used for testing",
        tags = {"test"},
        hidden = false
)

public class OreEntry {
    public final int id;
    public final String name;

    public OreEntry(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
