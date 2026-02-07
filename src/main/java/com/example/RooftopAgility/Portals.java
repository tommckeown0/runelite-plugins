package com.example.RooftopAgility;

import lombok.Getter;

import static net.runelite.api.NullObjectID.NULL_36241;
import static net.runelite.api.NullObjectID.NULL_36242;
import static net.runelite.api.NullObjectID.NULL_36243;
import static net.runelite.api.NullObjectID.NULL_36244;
import static net.runelite.api.NullObjectID.NULL_36245;
import static net.runelite.api.NullObjectID.NULL_36246;

public enum Portals {
    PORTAL_ONE(NULL_36241, 1),
    PORTAL_TWO(NULL_36242, 2),
    PORTAL_THREE(NULL_36243, 3),
    PORTAL_FOUR(NULL_36244, 4),
    PORTAL_FIVE(NULL_36245, 5),
    PORTAL_SIX(NULL_36246, 6);

    @Getter
    private final int portalID;

    @Getter
    private final int varbitValue;

    Portals(final int portalID, final int varbitValue) {
        this.portalID = portalID;
        this.varbitValue = varbitValue;
    }

    public static Portals getPortal(int varbitValue) {
        for (Portals portal : values()) {
            if (portal.getVarbitValue() == varbitValue) {
                return portal;
            }
        }
        return null;
    }
}
