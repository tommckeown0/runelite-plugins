package com.example.PacketSniffer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("packetsniffer")
public interface PacketSnifferConfig extends Config {

    @ConfigItem(
        keyName = "enabled",
        name = "Enabled",
        description = "Log outgoing short packets (walk packet capture)"
    )
    default boolean enabled() {
        return true;
    }
}
