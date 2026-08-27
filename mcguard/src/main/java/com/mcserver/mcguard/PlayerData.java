package com.mcserver.mcguard;

import com.mcserver.mcguard.check.CheckType;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Rolling per-player state. One instance per online player; discarded on logout.
 *
 * On a regular Bukkit/Paper server every field is touched only from the main
 * thread. Under Folia, a given player's events fire on the region thread that
 * owns them, so all access to a single PlayerData is still single-threaded -
 * the map that holds these instances is what needs to be concurrent, not the
 * instance itself.
 */
public class PlayerData {

    // --- position tracking ----------------------------------------------------
    public double lastX, lastY, lastZ;
    public boolean hasSnapshot;

    /** Consecutive moves spent off the ground without losing altitude. */
    public int airTicks;
    /** Whether the player was airborne on the previous move (landing edge). */
    public boolean wasAirborne;
    /** Highest Y reached during the current airborne stretch (for NoFall). */
    public double peakFallHeight;
    /** Max server-tracked fall distance seen during the current descent. A
     *  NoFall cheat spoofs ground contact mid-air so this stays near zero even
     *  though the player visibly dropped a long way. */
    public float serverFall;

    /** Consecutive moves the player has been over the speed limit. A single
     *  move over is almost always lag or a jump; only a sustained streak is a
     *  real speed cheat. Reset the moment the player drops back under. */
    public int speedStreak;

    // --- combat: kill-aura & timing ------------------------------------------
    /** Timestamps of recent block placements, newest last. Bounded to 30. */
    public final Deque<Long> placeTimes = new ArrayDeque<>();
    /** Timestamps of recent player actions (attacks+places) for timer detection. */
    public final Deque<Long> actionTimes = new ArrayDeque<>();
    /** Consecutive KillAura-angle hits; one bad-angle hit can be latency. */
    public int auraStreak;

    // --- latency --------------------------------------------------------------
    /** Last measured ping in ms. Updated on move/combat events. */
    public int ping;

    // --- no-slow --------------------------------------------------------------
    /** Consecutive moves moving fast while using a slowing item. */
    public int noSlowStreak;

    // --- grace windows --------------------------------------------------------
    public long joinGraceUntil;
    public long teleportGraceUntil;

    // --- combat ---------------------------------------------------------------
    /** Timestamps of recent attacks, newest last. Bounded to 40 entries. */
    public final Deque<Long> attackTimes = new ArrayDeque<>();

    // --- world ----------------------------------------------------------------
    /** Timestamps of recent block breaks, newest last. Bounded to 60 entries. */
    public final Deque<Long> breakTimes = new ArrayDeque<>();
    public long lastBreakAt;

    // --- x-ray sampling -------------------------------------------------------
    public int minedInWindow;
    public int oresInWindow;
    /** Backing queue: true = the break was a valuable ore. Bounds the window. */
    public final Deque<Boolean> mineWindow = new ArrayDeque<>();

    // --- violations -----------------------------------------------------------
    public final Map<CheckType, Double> violations = new EnumMap<>(CheckType.class);
    public double totalVl;
    public long   lastDecayAt = System.currentTimeMillis();
    public boolean exempt;

    /** Human-readable note about the most recent flag, surfaced by /mcguard vl. */
    public String lastFlagDetail = "";
    public CheckType lastFlagCheck;
    public long lastFlagAt;

    /** Seeds the position snapshot so the next move can compute a delta. */
    public void reset(Location loc) {
        lastX = loc.getX();
        lastY = loc.getY();
        lastZ = loc.getZ();
        airTicks = 0;
        wasAirborne = false;
        peakFallHeight = loc.getY();
        serverFall = 0f;
        hasSnapshot = true;
        attackTimes.clear();
        breakTimes.clear();
    }

    public void snapshot(Location loc) {
        lastX = loc.getX();
        lastY = loc.getY();
        lastZ = loc.getZ();
        hasSnapshot = true;
    }

    public void markTeleport(long graceMs) {
        teleportGraceUntil = System.currentTimeMillis() + graceMs;
        airTicks = 0;
        wasAirborne = false;
        serverFall = 0f;
        hasSnapshot = false;
    }

    public boolean inGrace() {
        long now = System.currentTimeMillis();
        return now < joinGraceUntil || now < teleportGraceUntil;
    }

    public void pushAttack(long now) {
        attackTimes.addLast(now);
        while (attackTimes.size() > 40) attackTimes.removeFirst();
    }

    public void pushBreak(long now) {
        breakTimes.addLast(now);
        while (breakTimes.size() > 60) breakTimes.removeFirst();
    }

    public void pushPlace(long now) {
        placeTimes.addLast(now);
        while (placeTimes.size() > 30) placeTimes.removeFirst();
    }

    public void pushAction(long now) {
        actionTimes.addLast(now);
        while (actionTimes.size() > 60) actionTimes.removeFirst();
    }

    /** Counts entries in the deque newer than `windowMs`. */
    public static int countWithin(Deque<Long> times, long now, long windowMs) {
        int n = 0;
        for (Long t : times) {
            if (now - t <= windowMs) n++;
        }
        return n;
    }

    public double vlOf(CheckType type) {
        return violations.getOrDefault(type, 0.0D);
    }
}
