package dev.g85perf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * G85 Performance Mod
 * ====================
 * Specifically tuned for:
 *   - MediaTek Helio G85 (Cortex-A75 + A55 octa-core)
 *   - Mali-G52 MC2 GPU (OpenGL ES 3.2, NO Vulkan support)
 *   - Zalith Launcher on Android (VirGL renderer)
 *   - 4GB / 6GB RAM devices
 *
 * Problems solved:
 *   1. Mali-G52 has weak geometry throughput → reduce draw calls
 *   2. G85 throttles at ~55°C → limit sustained GPU load
 *   3. VirGL adds CPU overhead → reduce render submission frequency
 *   4. Android GC pauses → reduce object allocations
 *   5. Small L2 cache → reduce chunk mesh complexity
 */
public class G85PerfMod implements ClientModInitializer {

    public static final String MOD_ID = "g85perf";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ── GPU Config (Mali-G52 tuned) ────────────────────────────────────────

    /** Skip sky render every N frames. Mali-G52 benefits greatly from this. */
    public static int skyRenderSkipFrames = 2;

    /** Skip cloud render. Clouds are expensive on Mali-G52. */
    public static boolean disableClouds = true;

    /** Reduce weather render intensity (rain/snow). */
    public static boolean reduceWeather = true;

    /** Skip rendering entities behind player (back-face cull). */
    public static boolean entityBackCull = true;

    /** Maximum entity render distance in blocks. */
    public static double maxEntityRenderDist = 24.0;

    // ── CPU Config (Cortex-A75 tuned) ─────────────────────────────────────

    /** Chunk builder threads. G85 has 4x A75 + 4x A55. Use 3 for chunk build. */
    public static int chunkBuilderThreads = 3;

    /** Max chunk rebuilds per tick to avoid CPU spike. */
    public static int maxRebuildsPerTick = 5;

    // ── Particle Config ────────────────────────────────────────────────────

    /** Max simultaneous particles rendered. */
    public static int maxParticles = 50;

    /** Render 1 out of every N particles. 2 = 50% particles. */
    public static int particleSkipRate = 3;

    // ── Memory Config (Android RAM tuned) ─────────────────────────────────

    /** Force GC every N ticks when memory is low. */
    public static int gcIntervalTicks = 600; // every 30 seconds

    /** Memory threshold in MB — trigger GC below this free RAM. */
    public static long gcTriggerFreeMemMB = 64;

    // ── Thermal Config ────────────────────────────────────────────────────

    /**
     * Thermal throttle mode:
     * When device is hot, reduce FPS cap and skip more render passes
     * to let the CPU/GPU cool down.
     * G85 throttles aggressively after 5-10 min of gaming.
     */
    public static boolean thermalProtection = true;

    /** FPS cap when thermal throttle is detected. */
    public static int thermalFpsCap = 20;

    /** Normal FPS cap. */
    public static int normalFpsCap = 30;

    // ── Background FPS ─────────────────────────────────────────────────────

    /** Limit FPS when Zalith is in background (saves battery). */
    public static boolean limitBgFps = true;
    public static int bgFpsCap = 5;

    // ── Runtime state ──────────────────────────────────────────────────────

    public static boolean thermalThrottleActive = false;
    private static int tickCounter = 0;
    private static long lastGcTick = 0;

    @Override
    public void onInitializeClient() {
        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.info("====================================");
        LOGGER.info("  G85 Performance Mod — Initialized");
        LOGGER.info("  CPU Cores detected : {}", cores);
        LOGGER.info("  Chunk build threads: {}", chunkBuilderThreads);
        LOGGER.info("  Max particles      : {}", maxParticles);
        LOGGER.info("  Thermal protection : {}", thermalProtection);
        LOGGER.info("====================================");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            tickCounter++;

            // ── Memory management ──────────────────────────────────────────
            if (tickCounter % gcIntervalTicks == 0) {
                checkAndRunGC();
            }

            // ── Thermal detection (every 5 sec) ───────────────────────────
            if (thermalProtection && tickCounter % 100 == 0) {
                detectThermalThrottle(client);
            }

            // ── Apply FPS cap ──────────────────────────────────────────────
            if (tickCounter % 20 == 0) {
                applyFpsCap(client);
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            applyStartupSettings(client);
        });
    }

    /**
     * Apply performance settings on startup.
     * Tuned for Zalith Launcher defaults.
     */
    private void applyStartupSettings(MinecraftClient client) {
        if (client.options == null) return;

        // Separate simulation distance from render distance
        // G85 can render 6 chunks but should only simulate 4
        int currentSim = client.options.getSimulationDistance().getValue();
        if (currentSim > 4) {
            client.options.getSimulationDistance().setValue(4);
            LOGGER.info("[G85Perf] Simulation distance → 4");
        }

        // Disable clouds (very expensive on Mali-G52 + VirGL)
        // In MC 1.21, clouds are controlled via cloudRenderMode option directly
        if (disableClouds) {
            client.options.cloudRenderMode.setValue(
                net.minecraft.client.option.CloudRenderMode.OFF
            );
            LOGGER.info("[G85Perf] Clouds disabled");
        }

        LOGGER.info("[G85Perf] Startup settings applied");
    }

    /**
     * Detect thermal throttling by measuring frame time consistency.
     * On G85, when the SoC heats up, frame times become very erratic.
     */
    private void detectThermalThrottle(MinecraftClient client) {
        int fps = client.getCurrentFps();

        // If FPS drops very low consistently, assume thermal throttle
        if (fps < 15 && fps > 0) {
            if (!thermalThrottleActive) {
                thermalThrottleActive = true;
                LOGGER.warn("[G85Perf] Thermal throttle detected! FPS={} — reducing load", fps);
            }
        } else if (fps > 25) {
            if (thermalThrottleActive) {
                thermalThrottleActive = false;
                LOGGER.info("[G85Perf] Thermal throttle cleared. FPS={}", fps);
            }
        }
    }

    /**
     * Apply appropriate FPS cap based on thermal and focus state.
     */
    private void applyFpsCap(MinecraftClient client) {
        if (client.options == null) return;

        int targetFps;
        if (!client.isWindowFocused() && limitBgFps) {
            targetFps = bgFpsCap;
        } else if (thermalThrottleActive) {
            targetFps = thermalFpsCap;
        } else {
            targetFps = normalFpsCap;
        }

        client.options.getMaxFps().setValue(targetFps);
    }

    /**
     * Check free JVM heap and run GC if needed.
     * Android has limited RAM — proactive GC reduces pause stutter.
     */
    private static void checkAndRunGC() {
        Runtime rt = Runtime.getRuntime();
        long freeMemMB = rt.freeMemory() / 1024 / 1024;

        if (freeMemMB < gcTriggerFreeMemMB) {
            LOGGER.info("[G85Perf] Low memory ({}MB free) — requesting GC", freeMemMB);
            System.gc();
        }
    }

    public static int getTickCounter() { return tickCounter; }
}
