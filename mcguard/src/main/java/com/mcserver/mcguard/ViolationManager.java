package com.mcserver.mcguard;

import com.mcserver.mcguard.check.CheckType;
import com.mcserver.mcguard.util.FoliaScheduler;
import com.mcserver.mcguard.util.ViolationLogger;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Owns violation levels and decides what a given VL total means.
 *
 * Checks deliberately have no authority to punish. They report; this class acts.
 * That is what lets you leave a noisy check enabled for observation without it
 * kicking anybody.
 *
 * Threading: {@link #flag} is always invoked from the event thread that owns
 * the flagged player (their move/combat/break event), so kicks happen inline on
 * the correct thread. Only the ban-list write is routed through the global
 * scheduler, because on Folia the ban list is shared state.
 */
public class ViolationManager {

    private final McGuardConfig config;
    private final Logger log;
    private final FoliaScheduler scheduler;
    private final ViolationLogger fileLog;

    public ViolationManager(McGuardConfig config, Logger log,
                            FoliaScheduler scheduler, ViolationLogger fileLog) {
        this.config = config;
        this.log = log;
        this.scheduler = scheduler;
        this.fileLog = fileLog;
    }

    /**
     * Records a flag. `severity` is a per-check multiplier supplied by the check
     * itself (how far past the limit the player was), scaled by the check's
     * intrinsic weight and clamped so one extreme reading cannot insta-ban.
     */
    public void flag(Player player, PlayerData data, CheckType type,
                     double severity, String detail) {

        double amount = type.weight() * Math.max(0.5D, Math.min(severity, 5.0D));

        double newVl = data.vlOf(type) + amount;
        data.violations.put(type, newVl);
        data.totalVl += amount;
        data.lastFlagCheck = type;
        data.lastFlagDetail = detail;
        data.lastFlagAt = System.currentTimeMillis();

        fileLog.log(player, type, data.totalVl, detail);

        if (config.notifyStaff && data.totalVl >= config.warnThreshold) {
            notifyStaff(player, type, data.totalVl, detail);
        }

        double kickAt = config.kickThreshold;
        double banAt = config.banThreshold;

        if (config.banEnabled && banAt > 0 && data.totalVl >= banAt) {
            ban(player, type);
            return;
        }
        if (kickAt > 0 && data.totalVl >= kickAt) {
            kick(player, type);
        }
    }

    /**
     * Bleeds VL away during clean play. Without this, a player who triggers one
     * borderline flag per hour would eventually be banned for nothing.
     */
    public void decay(PlayerData data) {
        long now = System.currentTimeMillis();
        long elapsed = now - data.lastDecayAt;
        if (elapsed < 1000L) return;

        double seconds = elapsed / 1000.0D;
        double reduction = config.decayPerSecond * seconds;
        data.lastDecayAt = now;
        if (reduction <= 0 || data.totalVl <= 0) return;

        data.totalVl = Math.max(0.0D, data.totalVl - reduction);
        data.violations.replaceAll((k, v) -> Math.max(0.0D, v - reduction));
    }

    private void notifyStaff(Player flagged, CheckType type, double vl, String detail) {
        String msg = ChatColor.RED + "[McGuard] "
                + ChatColor.YELLOW + flagged.getName()
                + ChatColor.GRAY + " failed "
                + ChatColor.AQUA + type.display()
                + ChatColor.GRAY + String.format(" (VL %.1f) ", vl)
                + ChatColor.DARK_GRAY + detail;

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mcguard.admin")) {
                staff.sendMessage(msg);
            }
        }
    }

    private void kick(Player player, CheckType type) {
        log.warning("Kicking " + player.getName() + " - " + type.display() + " (VL exceeded)");
        player.kickPlayer(ChatColor.RED + "Disconnected by McGuard\n"
                + ChatColor.GRAY + "Suspected: " + type.display());
    }

    private void ban(Player player, CheckType type) {
        log.warning("Banning " + player.getName() + " - " + type.display());
        final String name = player.getName();
        final String reason = "Cheating detected: " + type.display();
        // The ban list is global/shared state - write it on the global thread.
        scheduler.runGlobal(() ->
                Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, null, "McGuard"));
        player.kickPlayer(ChatColor.RED + "Banned by McGuard: " + type.display());
    }

    public void clear(PlayerData data) {
        data.violations.clear();
        data.totalVl = 0;
        data.lastFlagCheck = null;
        data.lastFlagDetail = "";
    }
}
