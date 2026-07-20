package com.example.PacketUtils;

public final class ObfuscatedNames {

    public static final String EVENT_MOUSE_CLICK_OBFUSCATEDNAME = "cv";
    public static final String EVENT_MOUSE_CLICK_WRITE1 = "mouseY";
    public static final String EVENT_MOUSE_CLICK_METHOD_NAME1 = "sk";
    public static final String EVENT_MOUSE_CLICK_WRITE2 = "mouseX";
    public static final String EVENT_MOUSE_CLICK_METHOD_NAME2 = "sk";
    public static final String EVENT_MOUSE_CLICK_WRITE3 = "0";
    public static final String EVENT_MOUSE_CLICK_METHOD_NAME3 = "ee";
    public static final String EVENT_MOUSE_CLICK_WRITE4 = "mouseInfo";
    public static final String EVENT_MOUSE_CLICK_METHOD_NAME4 = "ok";
    public static final String[][] EVENT_MOUSE_CLICK_WRITES = new String[][]{
            {"r 8", "v"},
            {"r 8", "v"},
            {"a 128"},
            {"r 8", "a 128"},
    };

    public static final String IF_BUTTONT_OBFUSCATEDNAME = "ci";
    public static final String IF_BUTTONT_WRITE1 = "destinationWidgetId";
    public static final String IF_BUTTONT_METHOD_NAME1 = "fb";
    public static final String IF_BUTTONT_WRITE2 = "sourceSlot";
    public static final String IF_BUTTONT_METHOD_NAME2 = "ok";
    public static final String IF_BUTTONT_WRITE3 = "destinationSlot";
    public static final String IF_BUTTONT_METHOD_NAME3 = "lk";
    public static final String IF_BUTTONT_WRITE4 = "sourceItemId";
    public static final String IF_BUTTONT_METHOD_NAME4 = "lk";
    public static final String IF_BUTTONT_WRITE5 = "sourceWidgetId";
    public static final String IF_BUTTONT_METHOD_NAME5 = "fb";
    public static final String IF_BUTTONT_WRITE6 = "destinationItemId";
    public static final String IF_BUTTONT_METHOD_NAME6 = "ep";
    public static final String[][] IF_BUTTONT_WRITES = new String[][]{
            {"r 16", "r 24", "v", "r 8"},
            {"r 8", "a 128"},
            {"v", "r 8"},
            {"v", "r 8"},
            {"r 16", "r 24", "v", "r 8"},
            {"a 128", "r 8"},
    };
    public static final String IF_BUTTONX_OBFUSCATEDNAME = "bi";
    public static final String IF_BUTTONX_WRITE1 = "widgetId";
    public static final String IF_BUTTONX_METHOD_NAME1 = "ov";
    public static final String IF_BUTTONX_WRITE2 = "slot";
    public static final String IF_BUTTONX_METHOD_NAME2 = "sk";
    public static final String IF_BUTTONX_WRITE3 = "itemId";
    public static final String IF_BUTTONX_METHOD_NAME3 = "sk";
    public static final String IF_BUTTONX_WRITE4 = "opCode";
    public static final String IF_BUTTONX_METHOD_NAME4 = "cu";
    public static final String[][] IF_BUTTONX_WRITES = new String[][]{
            {"r 24", "r 16", "r 8", "v"},
            {"r 8", "v"},
            {"r 8", "v"},
            {"v"},
    };
    public static final String IF_SUBOP_OBFUSCATEDNAME = "dd";
    public static final String IF_SUBOP_WRITE1 = "widgetId";
    public static final String IF_SUBOP_METHOD_NAME1 = "ov";
    public static final String IF_SUBOP_WRITE2 = "slot";
    public static final String IF_SUBOP_METHOD_NAME2 = "sk";
    public static final String IF_SUBOP_WRITE3 = "itemId";
    public static final String IF_SUBOP_METHOD_NAME3 = "sk";
    public static final String IF_SUBOP_WRITE4 = "menuIndex";
    public static final String IF_SUBOP_METHOD_NAME4 = "cu";
    public static final String IF_SUBOP_WRITE5 = "subActionIndex";
    public static final String IF_SUBOP_METHOD_NAME5 = "cu";
    public static final String[][] IF_SUBOP_WRITES = new String[][]{
            {"r 24", "r 16", "r 8", "v"},
            {"r 8", "v"},
            {"r 8", "v"},
            {"v"},
            {"v"},
    };
    // rev238 / RuneLite 1.12.31.1: move packet is jf.ea. Construction (decompiled from the
    // injected client's xv buffer write methods):
    //   buf.bw(5)             -> 1 byte, constant 5
    //   buf.el(baseX + local) -> worldX as little-endian short (low byte, then high byte)
    //   buf.el(baseY + local) -> worldY as little-endian short (low byte, then high byte)
    //   buf.dz(ctrl)          -> 1 byte = (128 - ctrl)
    // dp.aq is getBaseX and dp.al is getBaseY in the injected jar, so the FIRST coordinate written
    // is worldX, then worldY — both little-endian. (The previous 1.12.28 mapping had them in the
    // wrong order with worldX big-endian and ctrl plain; that was never verified to actually walk.)
    // The METHOD_NAME fields are decorative — BufferMethods writes the byte array directly per WRITES.
    public static final String MOVE_GAMECLICK_OBFUSCATEDNAME = "ea";
    public static final String MOVE_GAMECLICK_WRITE1 = "5";
    public static final String MOVE_GAMECLICK_METHOD_NAME1 = "bw";
    public static final String MOVE_GAMECLICK_WRITE2 = "worldPointX";
    public static final String MOVE_GAMECLICK_METHOD_NAME2 = "el";
    public static final String MOVE_GAMECLICK_WRITE3 = "worldPointY";
    public static final String MOVE_GAMECLICK_METHOD_NAME3 = "el";
    public static final String MOVE_GAMECLICK_WRITE4 = "ctrlDown";
    public static final String MOVE_GAMECLICK_METHOD_NAME4 = "dz";
    public static final String[][] MOVE_GAMECLICK_WRITES = new String[][]{
            {"v"},              // constant 5 (bw)
            {"v", "r 8"},       // worldX little-endian: low byte then high byte (el)
            {"v", "r 8"},       // worldY little-endian: low byte then high byte (el)
            {"s 128"},          // ctrl: byte = 128 - value (dz)
    };

    public static final String OPLOC1_OBFUSCATEDNAME = "dm";
    public static final String OPLOC1_WRITE1 = "worldPointX";
    public static final String OPLOC1_METHOD_NAME1 = "ok";
    public static final String OPLOC1_WRITE2 = "worldPointY";
    public static final String OPLOC1_METHOD_NAME2 = "lk";
    public static final String OPLOC1_WRITE3 = "ctrlDown";
    public static final String OPLOC1_METHOD_NAME3 = "bd";
    public static final String OPLOC1_WRITE4 = "objectId";
    public static final String OPLOC1_METHOD_NAME4 = "ep";
    public static final String[][] OPLOC1_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"v", "r 8"},
            {"s 0"},
            {"a 128", "r 8"},
    };
    public static final String OPLOC2_OBFUSCATEDNAME = "av";
    public static final String OPLOC2_WRITE1 = "worldPointX";
    public static final String OPLOC2_METHOD_NAME1 = "ep";
    public static final String OPLOC2_WRITE2 = "worldPointY";
    public static final String OPLOC2_METHOD_NAME2 = "ep";
    public static final String OPLOC2_WRITE3 = "ctrlDown";
    public static final String OPLOC2_METHOD_NAME3 = "es";
    public static final String OPLOC2_WRITE4 = "objectId";
    public static final String OPLOC2_METHOD_NAME4 = "sk";
    public static final String[][] OPLOC2_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"a 128", "r 8"},
            {"s 128"},
            {"r 8", "v"},
    };
    public static final String OPLOC3_OBFUSCATEDNAME = "ax";
    public static final String OPLOC3_WRITE1 = "worldPointy";
    public static final String OPLOC3_METHOD_NAME1 = "lk";
    public static final String OPLOC3_WRITE2 = "objectId";
    public static final String OPLOC3_METHOD_NAME2 = "lk";
    public static final String OPLOC3_WRITE3 = "worldPointX";
    public static final String OPLOC3_METHOD_NAME3 = "ep";
    public static final String OPLOC3_WRITE4 = "ctrlDown";
    public static final String OPLOC3_METHOD_NAME4 = "es";
    public static final String[][] OPLOC3_WRITES = new String[][]{
            {"v", "r 8"},
            {"v", "r 8"},
            {"a 128", "r 8"},
            {"s 128"},
    };
    public static final String OPLOC4_OBFUSCATEDNAME = "bg";
    public static final String OPLOC4_WRITE1 = "worldPointX";
    public static final String OPLOC4_METHOD_NAME1 = "ok";
    public static final String OPLOC4_WRITE2 = "objectId";
    public static final String OPLOC4_METHOD_NAME2 = "lk";
    public static final String OPLOC4_WRITE3 = "worldPointY";
    public static final String OPLOC4_METHOD_NAME3 = "ok";
    public static final String OPLOC4_WRITE4 = "ctrlDown";
    public static final String OPLOC4_METHOD_NAME4 = "bd";
    public static final String[][] OPLOC4_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"v", "r 8"},
            {"r 8", "a 128"},
            {"s 0"},
    };
    public static final String OPLOC5_OBFUSCATEDNAME = "cu";
    public static final String OPLOC5_WRITE1 = "worldPointX";
    public static final String OPLOC5_METHOD_NAME1 = "sk";
    public static final String OPLOC5_WRITE2 = "ctrlDown";
    public static final String OPLOC5_METHOD_NAME2 = "ee";
    public static final String OPLOC5_WRITE3 = "worldPointY";
    public static final String OPLOC5_METHOD_NAME3 = "ok";
    public static final String OPLOC5_WRITE4 = "objectId";
    public static final String OPLOC5_METHOD_NAME4 = "ok";
    public static final String[][] OPLOC5_WRITES = new String[][]{
            {"r 8", "v"},
            {"a 128"},
            {"r 8", "a 128"},
            {"r 8", "a 128"},
    };
    public static final String OPLOCT_OBFUSCATEDNAME = "cn";
    public static final String OPLOCT_WRITE1 = "worldPointY";
    public static final String OPLOCT_METHOD_NAME1 = "sk";
    public static final String OPLOCT_WRITE2 = "itemId";
    public static final String OPLOCT_METHOD_NAME2 = "ok";
    public static final String OPLOCT_WRITE3 = "worldPointX";
    public static final String OPLOCT_METHOD_NAME3 = "sk";
    public static final String OPLOCT_WRITE4 = "ctrlDown";
    public static final String OPLOCT_METHOD_NAME4 = "cu";
    public static final String OPLOCT_WRITE5 = "widgetId";
    public static final String OPLOCT_METHOD_NAME5 = "ff";
    public static final String OPLOCT_WRITE6 = "slot";
    public static final String OPLOCT_METHOD_NAME6 = "ok";
    public static final String OPLOCT_WRITE7 = "objectId";
    public static final String OPLOCT_METHOD_NAME7 = "ok";
    public static final String[][] OPLOCT_WRITES = new String[][]{
            {"r 8", "v"},
            {"r 8", "a 128"},
            {"r 8", "v"},
            {"v"},
            {"v", "r 8", "r 16", "r 24"},
            {"r 8", "a 128"},
            {"r 8", "a 128"},
    };
    public static final String OPNPC1_OBFUSCATEDNAME = "ao";
    public static final String OPNPC1_WRITE1 = "ctrlDown";
    public static final String OPNPC1_METHOD_NAME1 = "ee";
    public static final String OPNPC1_WRITE2 = "npcIndex";
    public static final String OPNPC1_METHOD_NAME2 = "lk";
    public static final String[][] OPNPC1_WRITES = new String[][]{
            {"a 128"},
            {"v", "r 8"},
    };
    public static final String OPNPC2_OBFUSCATEDNAME = "be";
    public static final String OPNPC2_WRITE1 = "npcIndex";
    public static final String OPNPC2_METHOD_NAME1 = "sk";
    public static final String OPNPC2_WRITE2 = "ctrlDown";
    public static final String OPNPC2_METHOD_NAME2 = "bd";
    public static final String[][] OPNPC2_WRITES = new String[][]{
            {"r 8", "v"},
            {"s 0"},
    };
    public static final String OPNPC3_OBFUSCATEDNAME = "dv";
    public static final String OPNPC3_WRITE1 = "ctrlDown";
    public static final String OPNPC3_METHOD_NAME1 = "cu";
    public static final String OPNPC3_WRITE2 = "npcIndex";
    public static final String OPNPC3_METHOD_NAME2 = "lk";
    public static final String[][] OPNPC3_WRITES = new String[][]{
            {"v"},
            {"v", "r 8"},
    };
    public static final String OPNPC4_OBFUSCATEDNAME = "cc";
    public static final String OPNPC4_WRITE1 = "npcIndex";
    public static final String OPNPC4_METHOD_NAME1 = "ep";
    public static final String OPNPC4_WRITE2 = "ctrlDown";
    public static final String OPNPC4_METHOD_NAME2 = "cu";
    public static final String[][] OPNPC4_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"v"},
    };
    public static final String OPNPC5_OBFUSCATEDNAME = "bn";
    public static final String OPNPC5_WRITE1 = "npcIndex";
    public static final String OPNPC5_METHOD_NAME1 = "ok";
    public static final String OPNPC5_WRITE2 = "ctrlDown";
    public static final String OPNPC5_METHOD_NAME2 = "bd";
    public static final String[][] OPNPC5_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"s 0"},
    };
    public static final String OPNPCT_OBFUSCATEDNAME = "da";
    public static final String OPNPCT_WRITE1 = "itemId";
    public static final String OPNPCT_METHOD_NAME1 = "ep";
    public static final String OPNPCT_WRITE2 = "widgetId";
    public static final String OPNPCT_METHOD_NAME2 = "fb";
    public static final String OPNPCT_WRITE3 = "slot";
    public static final String OPNPCT_METHOD_NAME3 = "lk";
    public static final String OPNPCT_WRITE4 = "ctrlDown";
    public static final String OPNPCT_METHOD_NAME4 = "es";
    public static final String OPNPCT_WRITE5 = "npcIndex";
    public static final String OPNPCT_METHOD_NAME5 = "ep";
    public static final String[][] OPNPCT_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"r 16", "r 24", "v", "r 8"},
            {"v", "r 8"},
            {"s 128"},
            {"a 128", "r 8"},
    };
    public static final String OPOBJ1_OBFUSCATEDNAME = "bk";
    public static final String OPOBJ1_WRITE1 = "ctrlDown";
    public static final String OPOBJ1_METHOD_NAME1 = "es";
    public static final String OPOBJ1_WRITE2 = "worldPointX";
    public static final String OPOBJ1_METHOD_NAME2 = "ok";
    public static final String OPOBJ1_WRITE3 = "worldPointY";
    public static final String OPOBJ1_METHOD_NAME3 = "sk";
    public static final String OPOBJ1_WRITE4 = "objectId";
    public static final String OPOBJ1_METHOD_NAME4 = "ok";
    public static final String[][] OPOBJ1_WRITES = new String[][]{
            {"s 128"},
            {"r 8", "a 128"},
            {"r 8", "v"},
            {"r 8", "a 128"},
    };
    public static final String OPOBJ2_OBFUSCATEDNAME = "cl";
    public static final String OPOBJ2_WRITE1 = "worldPointY";
    public static final String OPOBJ2_METHOD_NAME1 = "ep";
    public static final String OPOBJ2_WRITE2 = "worldPointX";
    public static final String OPOBJ2_METHOD_NAME2 = "sk";
    public static final String OPOBJ2_WRITE3 = "ctrlDown";
    public static final String OPOBJ2_METHOD_NAME3 = "ee";
    public static final String OPOBJ2_WRITE4 = "objectId";
    public static final String OPOBJ2_METHOD_NAME4 = "ep";
    public static final String[][] OPOBJ2_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"r 8", "v"},
            {"a 128"},
            {"a 128", "r 8"},
    };
    public static final String OPOBJ3_OBFUSCATEDNAME = "cm";
    public static final String OPOBJ3_WRITE1 = "worldPointX";
    public static final String OPOBJ3_METHOD_NAME1 = "ok";
    public static final String OPOBJ3_WRITE2 = "ctrlDown";
    public static final String OPOBJ3_METHOD_NAME2 = "bd";
    public static final String OPOBJ3_WRITE3 = "objectId";
    public static final String OPOBJ3_METHOD_NAME3 = "ep";
    public static final String OPOBJ3_WRITE4 = "worldPointY";
    public static final String OPOBJ3_METHOD_NAME4 = "lk";
    public static final String[][] OPOBJ3_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"s 0"},
            {"a 128", "r 8"},
            {"v", "r 8"},
    };
    public static final String OPOBJ4_OBFUSCATEDNAME = "de";
    public static final String OPOBJ4_WRITE1 = "worldPointY";
    public static final String OPOBJ4_METHOD_NAME1 = "sk";
    public static final String OPOBJ4_WRITE2 = "ctrlDown";
    public static final String OPOBJ4_METHOD_NAME2 = "bd";
    public static final String OPOBJ4_WRITE3 = "objectId";
    public static final String OPOBJ4_METHOD_NAME3 = "sk";
    public static final String OPOBJ4_WRITE4 = "worldPointX";
    public static final String OPOBJ4_METHOD_NAME4 = "ep";
    public static final String[][] OPOBJ4_WRITES = new String[][]{
            {"r 8", "v"},
            {"s 0"},
            {"r 8", "v"},
            {"a 128", "r 8"},
    };
    public static final String OPOBJ5_OBFUSCATEDNAME = "dl";
    public static final String OPOBJ5_WRITE1 = "objectId";
    public static final String OPOBJ5_METHOD_NAME1 = "lk";
    public static final String OPOBJ5_WRITE2 = "worldPointX";
    public static final String OPOBJ5_METHOD_NAME2 = "ep";
    public static final String OPOBJ5_WRITE3 = "worldPointY";
    public static final String OPOBJ5_METHOD_NAME3 = "ok";
    public static final String OPOBJ5_WRITE4 = "ctrlDown";
    public static final String OPOBJ5_METHOD_NAME4 = "es";
    public static final String[][] OPOBJ5_WRITES = new String[][]{
            {"v", "r 8"},
            {"a 128", "r 8"},
            {"r 8", "a 128"},
            {"s 128"},
    };
    public static final String OPOBJT_OBFUSCATEDNAME = "bh";
    public static final String OPOBJT_WRITE1 = "worldPointX";
    public static final String OPOBJT_METHOD_NAME1 = "ep";
    public static final String OPOBJT_WRITE2 = "ctrlDown";
    public static final String OPOBJT_METHOD_NAME2 = "bd";
    public static final String OPOBJT_WRITE3 = "itemId";
    public static final String OPOBJT_METHOD_NAME3 = "ep";
    public static final String OPOBJT_WRITE4 = "worldPointY";
    public static final String OPOBJT_METHOD_NAME4 = "lk";
    public static final String OPOBJT_WRITE5 = "objectId";
    public static final String OPOBJT_METHOD_NAME5 = "sk";
    public static final String OPOBJT_WRITE6 = "widgetId";
    public static final String OPOBJT_METHOD_NAME6 = "ff";
    public static final String OPOBJT_WRITE7 = "slot";
    public static final String OPOBJT_METHOD_NAME7 = "ep";
    public static final String[][] OPOBJT_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"s 0"},
            {"a 128", "r 8"},
            {"v", "r 8"},
            {"r 8", "v"},
            {"v", "r 8", "r 16", "r 24"},
            {"a 128", "r 8"},
    };
    public static final String OPPLAYER1_OBFUSCATEDNAME = "as";
    public static final String OPPLAYER1_WRITE1 = "ctrlDown";
    public static final String OPPLAYER1_METHOD_NAME1 = "es";
    public static final String OPPLAYER1_WRITE2 = "playerIndex";
    public static final String OPPLAYER1_METHOD_NAME2 = "sk";
    public static final String[][] OPPLAYER1_WRITES = new String[][]{
            {"s 128"},
            {"r 8", "v"},
    };
    public static final String OPPLAYER2_OBFUSCATEDNAME = "ak";
    public static final String OPPLAYER2_WRITE1 = "ctrlDown";
    public static final String OPPLAYER2_METHOD_NAME1 = "cu";
    public static final String OPPLAYER2_WRITE2 = "playerIndex";
    public static final String OPPLAYER2_METHOD_NAME2 = "sk";
    public static final String[][] OPPLAYER2_WRITES = new String[][]{
            {"v"},
            {"r 8", "v"},
    };
    public static final String OPPLAYER3_OBFUSCATEDNAME = "cf";
    public static final String OPPLAYER3_WRITE1 = "ctrlDown";
    public static final String OPPLAYER3_METHOD_NAME1 = "es";
    public static final String OPPLAYER3_WRITE2 = "playerIndex";
    public static final String OPPLAYER3_METHOD_NAME2 = "sk";
    public static final String[][] OPPLAYER3_WRITES = new String[][]{
            {"s 128"},
            {"r 8", "v"},
    };
    public static final String OPPLAYER4_OBFUSCATEDNAME = "az";
    public static final String OPPLAYER4_WRITE1 = "playerIndex";
    public static final String OPPLAYER4_METHOD_NAME1 = "sk";
    public static final String OPPLAYER4_WRITE2 = "ctrlDown";
    public static final String OPPLAYER4_METHOD_NAME2 = "bd";
    public static final String[][] OPPLAYER4_WRITES = new String[][]{
            {"r 8", "v"},
            {"s 0"},
    };
    public static final String OPPLAYER5_OBFUSCATEDNAME = "dz";
    public static final String OPPLAYER5_WRITE1 = "playerIndex";
    public static final String OPPLAYER5_METHOD_NAME1 = "ep";
    public static final String OPPLAYER5_WRITE2 = "ctrlDown";
    public static final String OPPLAYER5_METHOD_NAME2 = "es";
    public static final String[][] OPPLAYER5_WRITES = new String[][]{
            {"a 128", "r 8"},
            {"s 128"},
    };
    public static final String OPPLAYER6_OBFUSCATEDNAME = "ds";
    public static final String OPPLAYER6_WRITE1 = "playerIndex";
    public static final String OPPLAYER6_METHOD_NAME1 = "sk";
    public static final String OPPLAYER6_WRITE2 = "ctrlDown";
    public static final String OPPLAYER6_METHOD_NAME2 = "bd";
    public static final String[][] OPPLAYER6_WRITES = new String[][]{
            {"r 8", "v"},
            {"s 0"},
    };
    public static final String OPPLAYER7_OBFUSCATEDNAME = "bs";
    public static final String OPPLAYER7_WRITE1 = "playerIndex";
    public static final String OPPLAYER7_METHOD_NAME1 = "ok";
    public static final String OPPLAYER7_WRITE2 = "ctrlDown";
    public static final String OPPLAYER7_METHOD_NAME2 = "es";
    public static final String[][] OPPLAYER7_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"s 128"},
    };
    public static final String OPPLAYER8_OBFUSCATEDNAME = "bc";
    public static final String OPPLAYER8_WRITE1 = "ctrlDown";
    public static final String OPPLAYER8_METHOD_NAME1 = "cu";
    public static final String OPPLAYER8_WRITE2 = "playerIndex";
    public static final String OPPLAYER8_METHOD_NAME2 = "ok";
    public static final String[][] OPPLAYER8_WRITES = new String[][]{
            {"v"},
            {"r 8", "a 128"},
    };
    public static final String OPPLAYERT_OBFUSCATEDNAME = "cs";
    public static final String OPPLAYERT_WRITE1 = "slot";
    public static final String OPPLAYERT_METHOD_NAME1 = "ok";
    public static final String OPPLAYERT_WRITE2 = "playerIndex";
    public static final String OPPLAYERT_METHOD_NAME2 = "sk";
    public static final String OPPLAYERT_WRITE3 = "widgetId";
    public static final String OPPLAYERT_METHOD_NAME3 = "ff";
    public static final String OPPLAYERT_WRITE4 = "itemId";
    public static final String OPPLAYERT_METHOD_NAME4 = "ep";
    public static final String OPPLAYERT_WRITE5 = "ctrlDown";
    public static final String OPPLAYERT_METHOD_NAME5 = "cu";
    public static final String[][] OPPLAYERT_WRITES = new String[][]{
            {"r 8", "a 128"},
            {"r 8", "v"},
            {"v", "r 8", "r 16", "r 24"},
            {"a 128", "r 8"},
            {"v"},
    };

    public static final String OPHELDD_OBFUSCATEDNAME = "dt";
    public static final String OPHELDD_WRITE1 = "destItemId";
    public static final String OPHELDD_METHOD_NAME1 = "lk";
    public static final String OPHELDD_WRITE2 = "destId";
    public static final String OPHELDD_METHOD_NAME2 = "ff";
    public static final String OPHELDD_WRITE3 = "selectedItemId";
    public static final String OPHELDD_METHOD_NAME3 = "sk";
    public static final String OPHELDD_WRITE4 = "selectedChildIndex";
    public static final String OPHELDD_METHOD_NAME4 = "ok";
    public static final String OPHELDD_WRITE5 = "selectedId";
    public static final String OPHELDD_METHOD_NAME5 = "kn";
    public static final String OPHELDD_WRITE6 = "destChildIndex";
    public static final String OPHELDD_METHOD_NAME6 = "lk";
    public static final String[][] OPHELDD_WRITES = new String[][] {
            {"v", "r 8"},
            {"v", "r 8", "r 16", "r 24"},
            {"r 8", "v"},
            {"r 8", "a 128"},
            {"r 8",  "v", "r 24", "r 16"},
            {"v", "r 8"},
    };


    public static final String SET_HEADING_OBFUSCATEDNAME = "di";
    public static final String SET_HEADING_WRITE1 = "var3";
    public static final String SET_HEADING_METHOD_NAME1 = "ee";
    public static final String[][] SET_HEADING_WRITES = new String[][] {
            {"a 128"}
    };

    public static final String RESUME_COUNTDIALOG_OBFUSCATEDNAME = "ag";
    public static final String RESUME_COUNTDIALOG_WRITE1 = "var0";
    public static final String RESUME_COUNTDIALOG_METHOD_NAME1 = "ov";
    public static final String[][] RESUME_COUNTDIALOG_WRITES = new String[][]{
            {"r 24", "r 16", "r 8", "v"},
    };


    public static final String RESUME_PAUSEBUTTON_OBFUSCATEDNAME = "cq";
    public static final String RESUME_PAUSEBUTTON_WRITE1 = "var1";
    public static final String RESUME_PAUSEBUTTON_METHOD_NAME1 = "lk";
    public static final String RESUME_PAUSEBUTTON_WRITE2 = "var0";
    public static final String RESUME_PAUSEBUTTON_METHOD_NAME2 = "ff";
    public static final String[][] RESUME_PAUSEBUTTON_WRITES = new String[][]{
            {"v", "r 8"},
            {"v", "r 8", "r 16", "r 24"},
    };
    public static final String RESUME_OBJDIALOG_OBFUSCATEDNAME = "ae";
    public static final String RESUME_OBJDIALOG_WRITE1 = "var0";
    public static final String RESUME_OBJDIALOG_METHOD_NAME1 = "ca";
    public static final String[][] RESUME_OBJDIALOG_WRITES = new String[][]{
            {"r 8", "v"},
    };

    public static final String RESUME_NAMEDIALOG_OBFUSCATED_NAME = "af";
    public static final String RESUME_NAMEDIALOG_WRITE1 = "length";
    public static final String RESUME_NAMEDIALOG_METHOD_NAME1 = "writeByte";
    public static final String RESUME_NAMEDIALOG_WRITE2 = "string";
    public static final String RESUME_NAMEDIALOG_METHOD_NAME2 = "writeStringCp1252NullTerminated";
    public static final String[][] RESUME_NAMEDIALOG_WRITES = new String[][] {
            {"v"},
            {"strn"},
    };

    public static final String RESUME_STRINGDIALOG_OBFUSCATED_NAME = "bb";
    public static final String RESUME_STRINGDIALOG_WRITE1 = "length";
    public static final String RESUME_STRINGDIALOG_METHOD_NAME1 = "writeByte";
    public static final String RESUME_STRINGDIALOG_WRITE2 = "string";
    public static final String RESUME_STRINGDIALOG_METHOD_NAME2 = "writeStringCp1252NullTerminated";
    public static final String[][] RESUME_STRINGDIALOG_WRITES = new String[][] {
            {"v"},
            {"strn"},
    };

    // RL 1.12.31.1 / rev238: xm.bw/el writeByte does `ak[(ab += -1278253407) * 769523041 - 1] = v`.
    // The two constants are modular inverses mod 2^32, mirroring BufferMethods.nextIndex / index calc.
    public static final String offsetMultiplier = "-1278253407"; // ab += -1278253407 per byte
    public static final String indexMultiplier = "769523041";    // arrayIndex = ab * 769523041 - 1
    public static final String addNodeGarbageValue = "-1771370198"; // dw.ae's int param is unused; any value
    public static final String getPacketBufferNodeGarbageValue = "-2111588182"; // xt.ag REQUIRES this exact int (else throws); abs<Integer.MAX so int-invoke path is used
    public static final String packetWriterFieldName = "ad"; // client.ad is the dw packet writer (was client.aq/df)
    public static final String isaacCipherFieldName = "aa"; // dw.aa is public xs (ISAAC cipher)
    public static final String addNodeMethodName = "ae"; // dw.ae(jr, int) is the addNode method
    public static final String clientPacketClassName = "jf"; // jf holds all static packet fields (jf.ea = MOVE_GAMECLICK etc.)
    public static final String packetWriterClassName = "jr"; // dw.ae first param is jr (node); used by auto-detection filter
    public static final String classContainingGetPacketBufferNodeName = "xt"; // xt.ag(jf, xs, int) builds the packet buffer node
    public static final String packetBufferNodeClassName = "jr"; // xt.ag returns jr
    public static final String packetBufferFieldName = "al"; // jr.al is public xv (extends xm/PacketBuffer)
    public static final String bufferOffsetField = "ab"; // xm.ab is public int (offset)
    public static final String bufferArrayField = "ak"; // xm.ak is public byte[] (array)
    public static final String MouseHandler_lastPressedTimeMillisClass = "bp";
    public static final String MouseHandler_lastPressedTimeMillisField = "au";
    public static final String clientMillisField = "jn";
    public static final String mouseHandlerMillisMultiplier = "-458198379578361685";
    public static final String clientMillisMultiplier = "-458198379578361685";
    public static final int getAnimationMultiplier = 1746420373;
    public static final int skullIconMultiplier = 0;
    public static final String skullIconField = "null";
    public static final String pathLengthFieldName = "null";
    public static final int pathLengthMultiplier = 0;
    public static final String doActionClassName = "do";
    public static final String doActionMethodName = "lx";
}

