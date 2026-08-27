package com.mcserver.mcguard.listener;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerDataManager;
import com.mcserver.mcguard.check.CheckEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Feeds block breaks (FastBreak, Nuker, X-Ray) and placements (FastPlace) into
 * the world checks. ignoreCancelled = true so a break/place denied by a
 * protection plugin does not count against the player.
 */
public class WorldListener implements Listener {

    private final McGuardConfig config;
    private final CheckEngine engine;
    private final PlayerDataManager players;

    public WorldListener(McGuardConfig config, CheckEngine engine, PlayerDataManager players) {
        this.config = config;
        this.engine = engine;
        this.players = players;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.enabled) return;
        Player player = event.getPlayer();
        engine.runBreak(player, players.get(player), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.enabled) return;
        Player player = event.getPlayer();
        engine.runPlace(player, players.get(player));
    }
}
