package com.example.InteractionApi;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent camera controller with human-like movement patterns.
 *
 * Key features:
 * - Rotates camera to bring off-screen entities into view
 * - Periodic random adjustments (humans constantly fidget with camera)
 * - Gaussian distribution for rotation amounts
 * - Smooth interpolated camera movements (direct API control)
 * - Variable speed rotations for realism
 * - Respects human timing patterns
 *
 * Usage:
 *   // Check if entity is visible, rotate if needed
 *   if (CameraController.ensureEntityVisible(npc)) {
 *       // NPC is now visible, safe to interact
 *   }
 *
 *   // Periodic random adjustments (call every 30-90 seconds)
 *   CameraController.randomAdjustment();
 */
public class CameraController {

    private static final Client client = RuneLite.getInjector().getInstance(Client.class);
    private static boolean loggingEnabled = false;
    private static long lastCameraMovement = 0;
    private static long lastRandomAdjustment = 0;
    private static long nextAdjustmentTime = 0; // When next random adjustment should occur

    // Camera constants
    private static final int CAMERA_YAW_MIN = 0;
    private static final int CAMERA_YAW_MAX = 2047;
    private static final int CAMERA_PITCH_MIN = 128;  // High angle
    private static final int CAMERA_PITCH_MAX = 512;  // Low angle

    // Human-like timing (milliseconds)
    private static final int MIN_ROTATION_DURATION = 200;  // Fast rotation
    private static final int MAX_ROTATION_DURATION = 800;  // Slow rotation
    private static final int KEY_PRESS_DURATION = 50;      // How long to hold arrow key

    /**
     * Enable or disable camera logging
     */
    public static void setLoggingEnabled(boolean enabled) {
        loggingEnabled = enabled;
    }

    private static void log(String message) {
        if (loggingEnabled) {
            System.out.println("[CameraController] " + message);
        }
    }

    /**
     * Get angle from player to a world point (in OSRS yaw units: 0-2047)
     * 0 = North, 512 = East, 1024 = South, 1536 = West
     */
    public static int getAngleToWorldPoint(WorldPoint target) {
        if (client.getLocalPlayer() == null || target == null) {
            return -1;
        }

        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        int dx = target.getX() - playerPos.getX();
        int dy = target.getY() - playerPos.getY();

        // Convert to angle in radians, then to OSRS yaw units
        double angleRad = Math.atan2(dy, dx);
        // OSRS: 0=north, increases clockwise
        // atan2: 0=east, increases counter-clockwise
        // Convert: OSRS_angle = (90 - degrees) * (2048/360)
        double angleDeg = Math.toDegrees(angleRad);
        int osrsAngle = (int) ((90 - angleDeg) * (2048.0 / 360.0));

        // Normalize to 0-2047
        while (osrsAngle < 0) osrsAngle += 2048;
        while (osrsAngle >= 2048) osrsAngle -= 2048;

        return osrsAngle;
    }

    /**
     * Get shortest rotation direction to target angle
     * @return -1 (left), 0 (already there), or 1 (right)
     */
    private static int getRotationDirection(int currentYaw, int targetYaw, int threshold) {
        int diff = targetYaw - currentYaw;

        // Handle wrap-around (e.g., from 2000 to 100)
        if (diff > 1024) diff -= 2048;
        if (diff < -1024) diff += 2048;

        if (Math.abs(diff) <= threshold) {
            return 0; // Already facing target
        }

        return diff > 0 ? 1 : -1; // 1=right, -1=left
    }

    /**
     * Rotate camera toward a world point
     * @param target The world point to face
     * @param randomness Add random offset (degrees, 0-30 typical)
     * @return true if rotation was performed
     */
    public static boolean rotateTo(WorldPoint target, int randomness) {
        if (client.getLocalPlayer() == null || target == null) {
            log("rotateTo: Invalid target or player");
            return false;
        }

        int targetAngle = getAngleToWorldPoint(target);
        if (targetAngle == -1) {
            return false;
        }

        // Add human-like randomness (don't center perfectly)
        if (randomness > 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int randomOffset = random.nextInt(-randomness, randomness + 1);
            int randomOffsetInYaw = (int) (randomOffset * (2048.0 / 360.0));
            targetAngle = (targetAngle + randomOffsetInYaw) & 2047; // Wrap to 0-2047
        }

        int currentYaw = client.getCameraYaw();
        int direction = getRotationDirection(currentYaw, targetAngle, 50);

        if (direction == 0) {
            log(String.format("rotateTo: Already facing target (current=%d, target=%d)", currentYaw, targetAngle));
            return false;
        }

        log(String.format("rotateTo: Rotating %s | Current=%d, Target=%d",
                direction > 0 ? "RIGHT" : "LEFT", currentYaw, targetAngle));

        // Rotate toward target
        rotateCamera(direction, targetAngle);
        return true;
    }

    /**
     * Rotate camera smoothly to target yaw
     * Uses direct camera manipulation for reliability
     * @param direction -1 for left, 1 for right (used for logging only)
     * @param targetYaw The target yaw to reach
     */
    private static void rotateCamera(int direction, int targetYaw) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int currentYaw = client.getCameraYaw();

        log(String.format("Starting rotation: current=%d, target=%d", currentYaw, targetYaw));

        // Calculate total rotation needed (handle wrap-around)
        int diff = targetYaw - currentYaw;
        if (diff > 1024) diff -= 2048;
        if (diff < -1024) diff += 2048;

        int totalRotation = Math.abs(diff);
        int rotationDirection = diff > 0 ? 1 : -1;

        // Smooth rotation: move incrementally over time
        // Speed varies: 200-800ms for full rotation
        int rotationDuration = random.nextInt(MIN_ROTATION_DURATION, MAX_ROTATION_DURATION + 1);
        int steps = Math.max(5, totalRotation / 20); // 5-100 steps depending on distance
        int stepDelay = rotationDuration / steps;

        log(String.format("Rotating %d units over %dms in %d steps", totalRotation, rotationDuration, steps));

        long startTime = System.currentTimeMillis();
        int stepsCompleted = 0;

        try {
            for (int i = 0; i < steps; i++) {
                // Calculate how far we should be at this step (linear interpolation)
                float progress = (float) (i + 1) / steps;
                int targetStep = currentYaw + (int) (diff * progress);

                // Normalize to 0-2047
                while (targetStep < 0) targetStep += 2048;
                while (targetStep >= 2048) targetStep -= 2048;

                // Set camera to this intermediate position
                client.setCameraYawTarget(targetStep);

                stepsCompleted++;

                // Small delay between steps for smooth motion
                Thread.sleep(stepDelay);

                // Early exit if we've reached target
                int newYaw = client.getCameraYaw();
                if (getRotationDirection(newYaw, targetYaw, 50) == 0) {
                    log(String.format("Reached target early at step %d/%d", i + 1, steps));
                    break;
                }
            }

            // Final set to ensure we hit target exactly
            client.setCameraYawTarget(targetYaw);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lastCameraMovement = System.currentTimeMillis();
            int finalYaw = client.getCameraYaw();
            log(String.format("Rotation complete: finalYaw=%d (completed %d/%d steps)", finalYaw, stepsCompleted, steps));
        }
    }

    /**
     * Generate next random adjustment interval using gaussian distribution
     * @param meanSeconds The average interval (center of distribution)
     * @return Interval in milliseconds until next adjustment
     */
    private static long generateNextAdjustmentInterval(int meanSeconds) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Use gaussian distribution with stddev = mean/3
        // This creates realistic variation: 68% within ±33% of mean, 95% within ±66%
        double stdDev = meanSeconds / 3.0;
        double intervalSeconds = meanSeconds + (random.nextGaussian() * stdDev);

        // Clamp to reasonable bounds (minimum 20 sec, maximum 3x mean)
        intervalSeconds = Math.max(20, Math.min(meanSeconds * 3, intervalSeconds));

        return (long) (intervalSeconds * 1000);
    }

    /**
     * Check if it's time for a random camera adjustment
     * Uses gaussian-distributed intervals for realism
     * @param meanIntervalSeconds The average interval between adjustments
     * @return true if an adjustment should be made now
     */
    public static boolean shouldMakeRandomAdjustment(int meanIntervalSeconds) {
        long now = System.currentTimeMillis();

        // First time: schedule first adjustment
        if (nextAdjustmentTime == 0) {
            nextAdjustmentTime = now + generateNextAdjustmentInterval(meanIntervalSeconds);
            log(String.format("Scheduled first camera adjustment in %.1f seconds",
                    (nextAdjustmentTime - now) / 1000.0));
            return false;
        }

        // Check if it's time for adjustment
        if (now >= nextAdjustmentTime) {
            return true;
        }

        return false;
    }

    /**
     * Random camera adjustment (humans fidget with camera constantly)
     * Call this when shouldMakeRandomAdjustment() returns true
     * @param meanIntervalSeconds Mean interval for scheduling next adjustment
     */
    public static void randomAdjustment(int meanIntervalSeconds) {
        long now = System.currentTimeMillis();
        long timeSinceLastAdjustment = now - lastRandomAdjustment;

        // Safety check: Don't adjust too frequently (minimum 15 seconds between adjustments)
        if (timeSinceLastAdjustment < 15000) {
            log("randomAdjustment: Too soon since last adjustment, skipping");
            // Reschedule for later
            nextAdjustmentTime = now + 15000;
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 70% chance: Just rotate (yaw change)
        // 30% chance: Rotate AND pitch change
        boolean changePitch = random.nextDouble() < 0.3;

        // 15% chance: "Clustering" behavior - make another adjustment soon (humans often adjust multiple times)
        boolean willCluster = random.nextDouble() < 0.15;

        // Random rotation direction and amount
        int direction = random.nextBoolean() ? 1 : -1;
        int rotationAmount = (int) Math.abs(random.nextGaussian() * 200) + 50; // 50-400 yaw units typically
        rotationAmount = Math.min(rotationAmount, 600); // Cap at ~105 degrees

        int currentYaw = client.getCameraYaw();
        int targetYaw = (currentYaw + (direction * rotationAmount)) & 2047;

        log(String.format("randomAdjustment: Rotating %s by %d units (changePitch=%b, willCluster=%b)",
                direction > 0 ? "RIGHT" : "LEFT", rotationAmount, changePitch, willCluster));

        rotateCamera(direction, targetYaw);

        // Sometimes adjust pitch too (look up/down)
        if (changePitch) {
            adjustPitch();
        }

        lastRandomAdjustment = now;

        // Schedule next adjustment with variable interval
        long nextInterval;
        if (willCluster) {
            // Clustering: next adjustment very soon (5-15 seconds)
            nextInterval = random.nextLong(5000, 15000);
            log(String.format("Clustering behavior: next adjustment in %.1f seconds", nextInterval / 1000.0));
        } else {
            // Normal: use gaussian distribution around mean
            nextInterval = generateNextAdjustmentInterval(meanIntervalSeconds);
            log(String.format("Next adjustment scheduled in %.1f seconds", nextInterval / 1000.0));
        }

        nextAdjustmentTime = now + nextInterval;
    }

    /**
     * Adjust camera pitch (look up/down)
     * Humans occasionally adjust vertical angle
     */
    private static void adjustPitch() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int currentPitch = client.getCameraPitch();

        // Pick a random target pitch (weighted toward mid-range)
        int targetPitch;
        if (random.nextDouble() < 0.7) {
            // 70% - Mid range (256-400)
            targetPitch = random.nextInt(256, 401);
        } else if (random.nextDouble() < 0.5) {
            // 15% - High angle (128-255)
            targetPitch = random.nextInt(CAMERA_PITCH_MIN, 256);
        } else {
            // 15% - Low angle (401-512)
            targetPitch = random.nextInt(401, CAMERA_PITCH_MAX + 1);
        }

        log(String.format("adjustPitch: current=%d, target=%d", currentPitch, targetPitch));

        // Smooth pitch adjustment over time
        int totalChange = Math.abs(targetPitch - currentPitch);
        int duration = random.nextInt(200, 600);
        int steps = Math.max(3, totalChange / 30); // 3-20 steps
        int stepDelay = duration / steps;

        try {
            for (int i = 0; i < steps; i++) {
                float progress = (float) (i + 1) / steps;
                int intermediatePitch = currentPitch + (int) ((targetPitch - currentPitch) * progress);

                // Clamp to valid range
                intermediatePitch = Math.max(CAMERA_PITCH_MIN, Math.min(CAMERA_PITCH_MAX, intermediatePitch));

                client.setCameraPitchTarget(intermediatePitch);
                Thread.sleep(stepDelay);
            }

            // Final set to ensure we hit target exactly
            client.setCameraPitchTarget(targetPitch);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log(String.format("adjustPitch: complete, finalPitch=%d", client.getCameraPitch()));
    }

    /**
     * Ensure an entity at a world point is visible on screen
     * Rotates camera if needed
     * @param targetLocation World location of entity
     * @return true if entity should now be visible
     */
    public static boolean ensureEntityVisible(WorldPoint targetLocation) {
        if (targetLocation == null) {
            return false;
        }

        // Check if we need to rotate (entity might be behind player)
        int targetAngle = getAngleToWorldPoint(targetLocation);
        if (targetAngle == -1) {
            return false;
        }

        int currentYaw = client.getCameraYaw();
        int angleDiff = targetAngle - currentYaw;

        // Normalize angle difference
        if (angleDiff > 1024) angleDiff -= 2048;
        if (angleDiff < -1024) angleDiff += 2048;

        // If entity is more than ~90 degrees off-screen, rotate
        int threshold = 500; // ~88 degrees
        if (Math.abs(angleDiff) > threshold) {
            log(String.format("ensureEntityVisible: Entity off-screen (diff=%d degrees), rotating...",
                    (int) (angleDiff * 360.0 / 2048.0)));
            return rotateTo(targetLocation, 15); // 15 degree randomness
        }

        log("ensureEntityVisible: Entity already on-screen");
        return true;
    }


    /**
     * Get current camera yaw as degrees (0-360, 0=North)
     */
    public static int getCameraYawDegrees() {
        int yaw = client.getCameraYaw();
        return (int) ((yaw * 360.0) / 2048.0);
    }

    /**
     * Get current camera pitch as degrees (approximate)
     */
    public static int getCameraPitchDegrees() {
        int pitch = client.getCameraPitch();
        // Pitch 128=high (~67 degrees), 512=low (~0 degrees)
        // This is approximate mapping
        return (int) (67.0 * (1.0 - ((pitch - 128.0) / 384.0)));
    }
}
