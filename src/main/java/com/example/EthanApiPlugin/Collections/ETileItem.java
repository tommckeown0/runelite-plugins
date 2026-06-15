package com.example.EthanApiPlugin.Collections;

import com.example.InteractionApi.MenuActionInteractions;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import static net.runelite.api.TileItem.OWNERSHIP_GROUP;
import static net.runelite.api.TileItem.OWNERSHIP_SELF;

public class ETileItem {
    public WorldPoint location;
    public TileItem tileItem;

    public ETileItem(WorldPoint worldLocation, TileItem tileItem) {
        this.location = worldLocation;
        this.tileItem = tileItem;
    }

    public WorldPoint getLocation() {
        return location;
    }

    public TileItem getTileItem() {
        return tileItem;
    }
    public boolean isMine(){
        return tileItem.getOwnership() == OWNERSHIP_SELF||tileItem.getOwnership()==OWNERSHIP_GROUP;
    }

    public void interact(boolean ctrlDown) {
        // ctrlDown is irrelevant for "Take"; routed via the rev-238-verified menuAction path
        // instead of the broken obfuscated TileItemPackets raw-packet path (see CLAUDE.md).
        MenuActionInteractions.takeGroundItem(this);
    }
}
