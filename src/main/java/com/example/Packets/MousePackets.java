package com.example.Packets;

import net.runelite.api.Client;
import net.runelite.client.RuneLite;

import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.concurrent.Executors;

import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

public class MousePackets {

    static Client client = RuneLite.getInjector().getInstance(Client.class);
    private static final Random random = new Random();
    private static long randomDelay = randomDelay();

    /**
     * Formerly also sent a synthetic EVENT_MOUSE_CLICK packet carrying screen coordinates, to make
     * scripted actions look mouse-driven. That "click coordinate" experiment was dropped: real
     * actions go through {@code client.menuAction} (which needs no click at all), and a lone click
     * coordinate with no preceding mouse-movement stream is an <em>inconsistent</em> signal rather
     * than a convincing one — on top of relying on obfuscated mouse-handler fields that break on
     * every revision bump. All that remains here is the anti-idle keypress that prevents the
     * ~5-minute idle logout.
     */
    public static void queueClickPacket() {
        try {
            if (checkIdleLogout()) {
                randomDelay = randomDelay();
                Executors.newSingleThreadExecutor()
                        .submit(MousePackets::pressKey);
            }
        } catch (Throwable ignored) {
            // anti-idle is best-effort; never let it propagate to callers
        }
    }

    /** Coordinates are ignored now — kept only so existing callers (e.g. HumanLikeDropper) compile. */
    public static void queueClickPacket(int x, int y) {
        queueClickPacket();
    }

    private static boolean checkIdleLogout() {
        int idleClientTicks = client.getKeyboardIdleTicks();

        if (client.getMouseIdleTicks() < idleClientTicks) {
            idleClientTicks = client.getMouseIdleTicks();
        }

        return idleClientTicks >= randomDelay;
    }

    private static long randomDelay() {
        return (long) clamp(
                Math.round(random.nextGaussian() * 8000)
        );
    }

    private static double clamp(double val) {
        return Math.max(1, Math.min(13000, val));
    }

    private static void pressKey() {
        KeyEvent keyPress = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), BUTTON1_DOWN_MASK, KeyEvent.VK_BACK_SPACE);
        client.getCanvas().dispatchEvent(keyPress);
        KeyEvent keyRelease = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_BACK_SPACE);
        client.getCanvas().dispatchEvent(keyRelease);
        KeyEvent keyTyped = new KeyEvent(client.getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED);
        client.getCanvas().dispatchEvent(keyTyped);
    }
}
