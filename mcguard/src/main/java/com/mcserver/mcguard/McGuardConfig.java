package com.mcserver.mcguard;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Typed view over config.yml.
 *
 * The Forge build used a ForgeConfigSpec; Bukkit hands us a plain
 * {@link FileConfiguration}, so we snapshot the values into final-ish fields on
 * load and expose them by name. {@link #reload()} re-reads the file so
 * /mcguard reload can pick up edits without a restart.
 */
public final class McGuardConfig {

    private final Plugin plugin;

    // general
    public boolean enabled;
    public boolean logToFile;
    public boolean notifyStaff;
    public boolean exemptCreative;
    public boolean exemptOps;
    public int joinGraceMs;
    public int teleportGraceMs;

    // violations
    public double decayPerSecond;
    public double warnThreshold;
    public double kickThreshold;
    public double banThreshold;
    public boolean banEnabled;

    // movement
    public boolean speedEnabled;
    public double speedBaseLimit;
    public double speedTolerance;
    public int speedMinStreak;
    public boolean flightEnabled;
    public int flightMaxAirTicks;
    public boolean noFallEnabled;
    public boolean invalidMoveEnabled;
    public double invalidMoveMaxDelta;

    // combat
    public boolean reachEnabled;
    public double reachMaxSurvival;
    public boolean autoClickerEnabled;
    public int autoClickerMaxCps;
    public double autoClickerMinDeviationMs;
    public boolean killAuraEnabled;
    public double killAuraMaxAngle;
    public boolean noSlowEnabled;

    // placement
    public boolean fastPlaceEnabled;
    public int fastPlaceMaxPerSecond;

    // latency
    public boolean timerEnabled;
    public double timerMaxRate;
    public boolean lagCompensation;
    public int lagPingThreshold;

    // world
    public boolean fastBreakEnabled;
    public double fastBreakTolerance;
    public boolean nukerEnabled;
    public int nukerMaxBlocksPerSecond;
    public boolean xrayEnabled;
    public int xrayMinSample;
    public double xrayMaxRatio;
    public int xrayWindow;

    public McGuardConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        enabled         = c.getBoolean("general.enabled", true);
        logToFile       = c.getBoolean("general.logToFile", true);
        notifyStaff     = c.getBoolean("general.notifyStaff", true);
        exemptCreative  = c.getBoolean("general.exemptCreative", true);
        exemptOps       = c.getBoolean("general.exemptOps", true);
        joinGraceMs     = c.getInt("general.joinGraceMs", 5000);
        teleportGraceMs = c.getInt("general.teleportGraceMs", 3000);

        decayPerSecond  = c.getDouble("violations.decayPerSecond", 0.25);
        warnThreshold   = c.getDouble("violations.warnThreshold", 10.0);
        kickThreshold   = c.getDouble("violations.kickThreshold", 35.0);
        banThreshold    = c.getDouble("violations.banThreshold", 100.0);
        banEnabled      = c.getBoolean("violations.banEnabled", true);

        speedEnabled        = c.getBoolean("movement.speedEnabled", true);
        speedBaseLimit      = c.getDouble("movement.baseLimitPerTick", 0.42);
        speedTolerance      = c.getDouble("movement.tolerance", 1.35);
        speedMinStreak      = c.getInt("movement.minViolationStreak", 4);
        flightEnabled       = c.getBoolean("movement.flightEnabled", true);
        flightMaxAirTicks   = c.getInt("movement.maxAirTicks", 80);
        noFallEnabled       = c.getBoolean("movement.noFallEnabled", true);
        invalidMoveEnabled  = c.getBoolean("movement.invalidMoveEnabled", true);
        invalidMoveMaxDelta = c.getDouble("movement.maxDeltaPerTick", 10.0);

        reachEnabled             = c.getBoolean("combat.reachEnabled", true);
        reachMaxSurvival         = c.getDouble("combat.maxSurvivalReach", 4.2);
        autoClickerEnabled       = c.getBoolean("combat.autoClickerEnabled", true);
        autoClickerMaxCps        = c.getInt("combat.maxCps", 20);
        autoClickerMinDeviationMs = c.getDouble("combat.minDeviationMs", 8.0);
        killAuraEnabled          = c.getBoolean("combat.killAuraEnabled", true);
        killAuraMaxAngle         = c.getDouble("combat.maxHitAngle", 75.0);
        noSlowEnabled            = c.getBoolean("combat.noSlowEnabled", true);

        fastPlaceEnabled      = c.getBoolean("placement.fastPlaceEnabled", true);
        fastPlaceMaxPerSecond = c.getInt("placement.maxPlacePerSecond", 12);

        timerEnabled     = c.getBoolean("latency.timerEnabled", true);
        timerMaxRate     = c.getDouble("latency.maxRate", 1.12);
        lagCompensation  = c.getBoolean("latency.lagCompensation", true);
        lagPingThreshold = c.getInt("latency.pingThresholdMs", 120);

        fastBreakEnabled        = c.getBoolean("world.fastBreakEnabled", true);
        fastBreakTolerance      = c.getDouble("world.tolerance", 0.6);
        nukerEnabled            = c.getBoolean("world.nukerEnabled", true);
        nukerMaxBlocksPerSecond = c.getInt("world.maxBlocksPerSecond", 30);
        xrayEnabled             = c.getBoolean("world.xrayEnabled", true);
        xrayMinSample           = c.getInt("world.minOreSample", 24);
        xrayMaxRatio            = c.getDouble("world.maxOreRatio", 0.20);
        xrayWindow              = c.getInt("world.sampleWindow", 800);
    }
}
