package com.mcserver.mcguard.listener;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerData;
import com.mcserver.mcguard.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Lifecycle wiring: create tracking state on join (with a grace window so
 * chunk-load and spawn teleports are not read as speed/flight), drop it on
 * quit, and open a teleport grace window on every teleport so pearls, portals
 * and /tp never register as an InvalidMove.
 */
public class PlayerConnectionListener implements Listener {

    private final McGuardConfig config;
    private final PlayerDataManager players;

    public PlayerConnectionListener(McGuardConfig config, PlayerDataManager players) {
        this.config = config;
        this.players = players;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = new PlayerData();
        data.reset(player.getLocation());
        data.joinGraceUntil = System.currentTimeMillis() + config.joinGraceMs;
        players.put(player, data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        players.get(event.getPlayer()).markTeleport(config.teleportGraceMs);
    }
}
