package com.mcserver.mcguard.check;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerData;
import com.mcserver.mcguard.ViolationManager;
import com.mcserver.mcguard.util.ItemUse;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * All detection logic, ported from the Forge build to the Bukkit event model.
 *
 * Every check follows the same shape:
 *   1. bail out on any condition that could legitimately produce the signal
 *   2. compute how far past the limit the player is
 *   3. hand a severity to {@link ViolationManager} - never punish inline
 *
 * Step 1 is the important one. Most anticheat false positives are not subtle
 * maths errors, they are a forgotten exemption (boats, elytra, ice, pistons,
 * levitation, riptide). Each bail-out below is one of those.
 *
 * Everything here reads server-authoritative state (position deltas from the
 * move event, server velocity, server fall distance) rather than trusting the
 * client onGround / fall flags, which the cheats being detected spoof.
 */
public class CheckEngine {

    private final McGuardConfig config;
    private final ViolationManager violations;

    public CheckEngine(McGuardConfig config, ViolationManager violations) {
        this.config = config;
        this.violations = violations;
    }

    // ============================================================ MOVEMENT ====

    public void runMovementChecks(Player player, PlayerData data, Location from, Location to) {
        if (isExempt(player, data)) { data.airTicks = 0; data.wasAirborne = false; return; }
        if (data.inGrace())         { data.hasSnapshot = false; return; }

        data.ping = safePing(player);

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean onGround = isOnGround(player, to);

        invalidMove(player, data, dx, dy, dz);
        speed(player, data, horizontal);
        airborne(player, data, to, dy, onGround);
        noSlow(player, data, horizontal);
    }

    /** Teleport-sized jumps that no movement mechanic can produce. */
    private void invalidMove(Player player, PlayerData data, double dx, double dy, double dz) {
        if (!config.invalidMoveEnabled) return;

        double delta = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double max = config.invalidMoveMaxDelta;
        if (delta <= max) return;

        // A genuine teleport (pearl, portal, /tp, chorus fruit) fires
        // PlayerTeleportEvent, which sets the grace window before this runs. If
        // we are here without grace, no mechanic explains it.
        violations.flag(player, data, CheckType.INVALID_MOVE,
                delta / max,
                String.format("moved %.1f blocks in one move", delta));
        data.markTeleport(config.teleportGraceMs);
    }

    /** Horizontal velocity beyond everything the player could legitimately have. */
    private void speed(Player player, PlayerData data, double horizontal) {
        if (!config.speedEnabled) return;
        if (player.isInsideVehicle()) return;      // boats, horses, minecarts
        if (player.isGliding()) return;            // elytra
        if (player.isFlying() || player.getAllowFlight()) return;
        if (isInLiquid(player.getLocation())) return;

        double limit = config.speedBaseLimit;

        // Speed potions add roughly 20% per level.
        PotionEffect speedEff = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEff != null) {
            int amp = speedEff.getAmplifier() + 1;
            limit *= (1.0D + 0.2D * amp);
        }

        // Being launched (TNT, pistons, knockback, riptide) shows up as high
        // horizontal delta the player did not create. Trust server-side velocity.
        Vector v = player.getVelocity();
        double serverVel = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        if (serverVel > limit) limit = serverVel * 1.1D;

        // Ice, slime, soul speed, depth strider and general latency.
        limit *= config.speedTolerance;

        if (horizontal <= limit) {
            data.speedStreak = 0;   // back under the limit - not cheating
            return;
        }

        // Over the limit for ONE move is almost always a lag spike (bunched
        // movement packets applied together), a jump apex, knockback, or landing
        // momentum - not a cheat. Only flag once the player has stayed over the
        // limit for several consecutive moves, which is the real speed signal.
        data.speedStreak++;
        if (data.speedStreak < config.speedMinStreak) return;

        violations.flag(player, data, CheckType.SPEED,
                horizontal / limit,
                String.format("%.3f b/t vs limit %.3f for %d moves",
                        horizontal, limit, data.speedStreak));
    }

    /**
     * Manages airborne state and runs both Flight and NoFall, which share the
     * same airTicks / peak-height bookkeeping. Kept in one place so the two
     * checks never disagree about whether the player is on the ground.
     */
    private void airborne(Player player, PlayerData data, Location to, double dy, boolean onGround) {
        boolean allowed = player.isFlying()
                || player.getAllowFlight()
                || player.isGliding()
                || player.isInsideVehicle()
                || isInLiquid(to)
                || isOnClimbable(to)
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

        if (onGround || allowed) {
            // Landing edge: run NoFall before we reset the fall bookkeeping.
            if (data.wasAirborne && !allowed) {
                checkNoFall(player, data, to);
            }
            data.airTicks = 0;
            data.wasAirborne = false;
            data.peakFallHeight = to.getY();
            data.serverFall = 0f;
            return;
        }

        // Airborne.
        data.wasAirborne = true;
        if (to.getY() > data.peakFallHeight) data.peakFallHeight = to.getY();
        data.serverFall = Math.max(data.serverFall, player.getFallDistance());

        // Flight: only count moves where the player is airborne and NOT losing
        // height. Falling is fine - gravity means dy trends negative.
        if (dy < -0.05D) return;

        data.airTicks++;
        if (!config.flightEnabled) return;

        int max = config.flightMaxAirTicks;
        if (data.airTicks <= max) return;

        violations.flag(player, data, CheckType.FLIGHT,
                (double) data.airTicks / max,
                String.format("%d moves airborne, dy=%.3f", data.airTicks, dy));
        data.airTicks = 0;
    }

    /**
     * NoFall detection, corrected for the plugin.
     *
     * NoFall works by telling the server "I am on the ground" mid-air so the
     * server never accrues fall distance and never applies fall damage. We see
     * the real descent through position deltas (peak minus landing Y), and we
     * see what the server THINKS the fall was via Player#getFallDistance().
     * A legitimate faller has the two roughly equal; a NoFall cheat drops a long
     * way while the server-side fall distance stays near zero. Soft landings and
     * mechanics that legitimately zero fall distance are exempted so this never
     * fires on honest play.
     */
    private void checkNoFall(Player player, PlayerData data, Location landing) {
        if (!config.noFallEnabled) return;
        if (player.isInsideVehicle()) return;
        if (isSoftLanding(landing)) return;

        double descended = data.peakFallHeight - landing.getY();
        if (descended < 5.0D) return;

        // Legit fall -> server fall distance tracked the descent. NoFall -> the
        // server "forgot" most of it.
        if (data.serverFall >= descended * 0.5D) return;

        violations.flag(player, data, CheckType.NOFALL,
                descended / Math.max(data.serverFall + 1.0D, 1.0D),
                String.format("descended %.1f but server fall was %.1f", descended, data.serverFall));
    }

    /**
     * NoSlow. Using an item that should slow the player - eating, drinking,
     * blocking with a shield, pulling a bow - drops movement to about 20% speed.
     * A NoSlow cheat ignores that. We flag full-speed movement while such an
     * item is in active use.
     */
    private void noSlow(Player player, PlayerData data, double horizontal) {
        if (!config.noSlowEnabled) { data.noSlowStreak = 0; return; }
        if (!ItemUse.isUsingItem(player)) { data.noSlowStreak = 0; return; }
        if (player.isInsideVehicle() || player.isFlying()) { data.noSlowStreak = 0; return; }

        double useLimit = 0.15D * config.speedTolerance;
        if (horizontal <= useLimit) { data.noSlowStreak = 0; return; }

        data.noSlowStreak++;
        if (data.noSlowStreak < config.speedMinStreak) return;

        violations.flag(player, data, CheckType.NOSLOW,
                horizontal / useLimit,
                String.format("%.3f b/t while using item (limit %.3f)", horizontal, useLimit));
        data.noSlowStreak = 0;
    }

    // ============================================================== COMBAT ====

    public void runCombatChecks(Player player, PlayerData data, Entity target) {
        if (isExempt(player, data)) return;
        if (data.inGrace()) return;

        data.ping = safePing(player);
        long now = System.currentTimeMillis();
        reach(player, data, target);
        killAura(player, data, target);
        autoClicker(player, data, now);
        data.pushAttack(now);
        data.pushAction(now);
        timer(player, data, now);
    }

    /** Attack landed from further away than vanilla permits. */
    private void reach(Player player, PlayerData data, Entity target) {
        if (!config.reachEnabled || target == null) return;

        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();

        double dx = box.getCenterX() - eye.getX();
        double dy = box.getCenterY() - eye.getY();
        double dz = box.getCenterZ() - eye.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // A wide mob is legitimately hit from further out than its centre.
        distance -= box.getWidthX() / 2.0D;

        double max = config.reachMaxSurvival + pingReachBonus(data);
        if (distance <= max) return;

        violations.flag(player, data, CheckType.REACH,
                distance / max,
                String.format("hit from %.2f blocks (max %.2f)", distance, max));
    }

    /**
     * KillAura angle check. A legitimate melee hit requires the player to be
     * looking roughly at the target - the client raycasts from the crosshair.
     * An aura swings at entities behind or beside the player. We measure the
     * angle between the look vector and the direction to the target; a wide
     * angle sustained across hits is the aura signature.
     */
    private void killAura(Player player, PlayerData data, Entity target) {
        if (!config.killAuraEnabled || target == null) return;

        Vector look = player.getLocation().getDirection().normalize();
        Location eye = player.getEyeLocation();
        Location te = (target instanceof LivingEntity le) ? le.getEyeLocation() : target.getLocation();
        Vector to = new Vector(te.getX() - eye.getX(), te.getY() - eye.getY(), te.getZ() - eye.getZ());
        if (to.lengthSquared() < 1.0E-4) return;
        to.normalize();

        double dot = Math.max(-1.0D, Math.min(1.0D, look.dot(to)));
        double angle = Math.toDegrees(Math.acos(dot));

        double max = config.killAuraMaxAngle;
        if (config.lagCompensation && data.ping > config.lagPingThreshold) {
            max += Math.min(30.0D, data.ping / 20.0D);
        }

        if (angle <= max) {
            data.auraStreak = 0;
            return;
        }

        data.auraStreak++;
        if (data.auraStreak < 3) return;

        violations.flag(player, data, CheckType.KILLAURA,
                angle / max,
                String.format("hit at %.0f deg off-aim (max %.0f) x%d", angle, max, data.auraStreak));
        data.auraStreak = 0;
    }

    /**
     * Two signals: raw click rate, and rhythm regularity. The second matters
     * more - a human clicking 15 CPS has messy intervals, a macro at 12 CPS has
     * near-identical ones.
     */
    private void autoClicker(Player player, PlayerData data, long now) {
        if (!config.autoClickerEnabled) return;
        if (data.attackTimes.size() < 10) return;

        int cps = PlayerData.countWithin(data.attackTimes, now, 1000L);
        int maxCps = config.autoClickerMaxCps;
        if (cps > maxCps) {
            violations.flag(player, data, CheckType.AUTOCLICKER,
                    (double) cps / maxCps,
                    String.format("%d CPS (max %d)", cps, maxCps));
            return;
        }

        double minDev = config.autoClickerMinDeviationMs;
        if (minDev <= 0) return;

        Long[] times = data.attackTimes.toArray(new Long[0]);
        if (times.length < 10) return;

        int n = times.length - 1;
        double[] gaps = new double[n];
        double sum = 0;
        for (int i = 1; i < times.length; i++) {
            gaps[i - 1] = times[i] - times[i - 1];
            sum += gaps[i - 1];
        }
        if (n < 8) return;

        double mean = sum / n;
        // Only meaningful for fast, sustained clicking; slow clicking is noisy
        // by nature and produces false positives.
        if (mean > 250) return;

        double varianceSum = 0;
        for (int i = 0; i < n; i++) {
            varianceSum += Math.pow(gaps[i] - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / n);
        if (stdDev >= minDev) return;

        violations.flag(player, data, CheckType.AUTOCLICKER,
                minDev / Math.max(stdDev, 0.1D),
                String.format("click rhythm too regular: sd=%.1fms over %.0fms mean", stdDev, mean));
    }

    /**
     * Timer detection. A timer hack speeds up the client clock, so the player
     * emits more actions per real second than 20 t/s allows. We measure action
     * frequency over a short window and compare to a human ceiling.
     */
    private void timer(Player player, PlayerData data, long now) {
        if (!config.timerEnabled) return;
        if (data.actionTimes.size() < 20) return;

        Long[] times = data.actionTimes.toArray(new Long[0]);
        long span = times[times.length - 1] - times[0];
        if (span < 500) return;
        double actionsPerSec = (times.length - 1) * 1000.0 / span;

        double humanCeiling = 16.0 * config.timerMaxRate;
        if (actionsPerSec <= humanCeiling) return;

        violations.flag(player, data, CheckType.TIMER,
                actionsPerSec / humanCeiling,
                String.format("%.1f actions/s over %dms (ceiling %.1f)", actionsPerSec, span, humanCeiling));
    }

    // =============================================================== WORLD ====

    public void runBreak(Player player, PlayerData data, Block block) {
        if (isExempt(player, data)) return;
        if (data.inGrace()) return;

        long now = System.currentTimeMillis();
        fastBreak(player, data, block, now);
        nuker(player, data, now);
        xray(player, data, block);
        data.pushBreak(now);
        data.lastBreakAt = now;
    }

    /** Blocks placed faster than a human can click. Called from the place event. */
    public void runPlace(Player player, PlayerData data) {
        if (!config.fastPlaceEnabled) return;
        if (isExempt(player, data)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        long now = System.currentTimeMillis();
        data.pushPlace(now);
        data.pushAction(now);

        int perSecond = PlayerData.countWithin(data.placeTimes, now, 1000L);
        int max = config.fastPlaceMaxPerSecond;
        if (perSecond <= max) return;

        violations.flag(player, data, CheckType.FASTPLACE,
                (double) perSecond / max,
                String.format("%d blocks placed/s (max %d)", perSecond, max));
    }

    /**
     * Statistical x-ray detection.
     *
     * A pure server-side anticheat cannot see through the client, so it cannot
     * catch an x-rayer in the act the way a packet/render check could. What it
     * CAN do is measure the outcome: someone digging straight to every diamond
     * mines valuable ore at a rate no honest strip-miner reaches. We keep a
     * rolling window of the player's recent breaks and flag when the fraction
     * that are valuable ore stays implausibly high over a large enough sample.
     */
    private void xray(Player player, PlayerData data, Block block) {
        if (!config.xrayEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        boolean ore = isValuableOre(block.getType());

        int window = config.xrayWindow;
        data.mineWindow.addLast(ore);
        data.minedInWindow++;
        if (ore) data.oresInWindow++;
        while (data.mineWindow.size() > window) {
            Boolean old = data.mineWindow.removeFirst();
            data.minedInWindow--;
            if (old != null && old) data.oresInWindow--;
        }

        int minSample = config.xrayMinSample;
        if (data.oresInWindow < minSample) return;

        double ratio = (double) data.oresInWindow / Math.max(1, data.minedInWindow);
        double max = config.xrayMaxRatio;
        if (ratio <= max) return;

        violations.flag(player, data, CheckType.XRAY,
                ratio / max,
                String.format("%d ores in %d blocks (%.1f%%, natural is <5%%)",
                        data.oresInWindow, data.minedInWindow, ratio * 100.0D));
    }

    /** Block destroyed faster than its hardness plausibly allows. */
    private void fastBreak(Player player, PlayerData data, Block block, long now) {
        if (!config.fastBreakEnabled) return;
        if (data.lastBreakAt == 0) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        float hardness;
        try {
            hardness = block.getType().getHardness();
        } catch (Throwable t) {
            return;
        }
        if (hardness <= 0) return;  // instant-break blocks: grass, torches, etc.

        double expectedMs = estimateBreakMs(player, hardness);
        if (expectedMs < 100) return;   // too fast to measure reliably

        double allowedMs = expectedMs * config.fastBreakTolerance;
        double actualMs = now - data.lastBreakAt;
        if (actualMs >= allowedMs) return;

        violations.flag(player, data, CheckType.FASTBREAK,
                allowedMs / Math.max(actualMs, 1.0D),
                String.format("broke %s in %.0fms, expected >=%.0fms",
                        block.getType(), actualMs, allowedMs));
    }

    /**
     * Best-effort lower bound on legitimate break time (ms). Bukkit does not
     * expose the vanilla dig-speed calculation, so we deliberately assume a
     * STRONG legit setup (top-tier tool, plus efficiency and haste bonuses).
     * FastBreak then only fires on breaks faster than even an optimised
     * legitimate player could manage, keeping false positives near zero at the
     * cost of only catching blatant instant-break cheats. See the README.
     */
    private double estimateBreakMs(Player player, float hardness) {
        double speed = 12.0D;   // ~ diamond/netherite tier best-case base
        ItemStack tool = player.getInventory().getItemInMainHand();
        int eff = tool.getEnchantmentLevel(Enchantment.DIG_SPEED);
        if (eff > 0) speed += (double) eff * eff + 1.0D;
        PotionEffect haste = player.getPotionEffect(PotionEffectType.FAST_DIGGING);
        if (haste != null) speed *= (1.0D + 0.2D * (haste.getAmplifier() + 1));
        double seconds = 1.5D * hardness / speed;   // vanilla: t = 1.5*hardness/speed
        return seconds * 1000.0D;
    }

    /** Many blocks destroyed per second - the signature of a nuker module. */
    private void nuker(Player player, PlayerData data, long now) {
        if (!config.nukerEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        int perSecond = PlayerData.countWithin(data.breakTimes, now, 1000L);
        int max = config.nukerMaxBlocksPerSecond;
        if (perSecond <= max) return;

        violations.flag(player, data, CheckType.NUKER,
                (double) perSecond / max,
                String.format("%d blocks/s (max %d)", perSecond, max));
    }

    // ============================================================= HELPERS ====

    private boolean isValuableOre(Material m) {
        switch (m) {
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case ANCIENT_DEBRIS:
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
            case NETHER_GOLD_ORE:
                return true;
            default:
                return false;
        }
    }

    /** Extra reach allowance in blocks derived from ping. */
    private double pingReachBonus(PlayerData data) {
        if (!config.lagCompensation) return 0.0D;
        if (data.ping <= config.lagPingThreshold) return 0.0D;
        return Math.min(1.5D, data.ping / 150.0D);
    }

    private int safePing(Player player) {
        try {
            return Math.max(0, player.getPing());
        } catch (Throwable t) {
            return 0;   // platform without Player#getPing() - lag comp just stays off
        }
    }

    /**
     * Server-side ground check. We do NOT trust the client onGround flag -
     * fly and nofall cheats spoof it. Sample the block layer just beneath the
     * feet across the player's footprint instead.
     */
    private boolean isOnGround(Player player, Location loc) {
        World w = loc.getWorld();
        if (w == null) return player.isOnGround();
        double y = loc.getY() - 0.05D;
        int by = (int) Math.floor(y);
        for (double ox = -0.3D; ox <= 0.3D; ox += 0.3D) {
            for (double oz = -0.3D; oz <= 0.3D; oz += 0.3D) {
                Block b = w.getBlockAt((int) Math.floor(loc.getX() + ox), by, (int) Math.floor(loc.getZ() + oz));
                if (b.getType().isSolid()) return true;
            }
        }
        return false;
    }

    private boolean isInLiquid(Location loc) {
        Material feet = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 0.1, 0).getBlock().getType();
        return feet == Material.WATER || feet == Material.LAVA
                || below == Material.WATER || below == Material.LAVA;
    }

    private boolean isOnClimbable(Location loc) {
        switch (loc.getBlock().getType()) {
            case LADDER:
            case VINE:
            case WEEPING_VINES:
            case WEEPING_VINES_PLANT:
            case TWISTING_VINES:
            case TWISTING_VINES_PLANT:
            case SCAFFOLDING:
            case CAVE_VINES:
            case CAVE_VINES_PLANT:
                return true;
            default:
                return false;
        }
    }

    /** Blocks / mechanics that legitimately zero out fall distance on landing. */
    private boolean isSoftLanding(Location loc) {
        Material at = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 0.1, 0).getBlock().getType();
        return isSoft(at) || isSoft(below);
    }

    private boolean isSoft(Material m) {
        switch (m) {
            case WATER:
            case LAVA:
            case COBWEB:
            case SLIME_BLOCK:
            case HONEY_BLOCK:
            case POWDER_SNOW:
            case SCAFFOLDING:
            case LADDER:
            case VINE:
            case HAY_BLOCK:
            case SWEET_BERRY_BUSH:
                return true;
            default:
                // Any bed also breaks a fall.
                return m.name().endsWith("_BED");
        }
    }

    private boolean isExempt(Player player, PlayerData data) {
        if (data.exempt) return true;
        if (player.hasPermission("mcguard.bypass")) return true;
        if (config.exemptCreative
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return true;
        }
        if (config.exemptOps && (player.isOp() || player.hasPermission("mcguard.admin"))) return true;
        return false;
    }
}
