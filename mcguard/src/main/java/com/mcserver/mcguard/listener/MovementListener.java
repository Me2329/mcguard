package com.mcserver.mcguard.listener;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerData;
import com.mcserver.mcguard.PlayerDataManager;
import com.mcserver.mcguard.ViolationManager;
import com.mcserver.mcguard.check.CheckEngine;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Feeds every positional move into the movement checks.
 *
 * Runs at MONITOR because McGuard observes and punishes via violation level; it
 * never cancels the move (no setback), so it wants the final, authoritative
 * position after every other plugin has had its say. Under Folia this event
 * fires on the region thread that owns the moving player, which is exactly the
 * thread a kick must run on - so punishment stays correct with no scheduling.
 */
public class MovementListener implements Listener {

    private final McGuardConfig config;
    private final CheckEngine engine;
    private final PlayerDataManager players;
    private final ViolationManager violations;

    public MovementListener(McGuardConfig config, CheckEngine engine,
                            PlayerDataManager players, ViolationManager violations) {
        this.config = config;
        this.engine = engine;
        this.players = players;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.enabled) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Ignore look-only events (same position, only yaw/pitch changed).
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();
        PlayerData data = players.get(player);
        engine.runMovementChecks(player, data, from, to);
        violations.decay(data);
        data.snapshot(to);
    }
}
