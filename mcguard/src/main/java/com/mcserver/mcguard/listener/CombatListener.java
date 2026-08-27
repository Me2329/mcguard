package com.mcserver.mcguard.listener;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerDataManager;
import com.mcserver.mcguard.check.CheckEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Feeds melee attacks into the combat checks (Reach, KillAura, AutoClicker,
 * Timer). Only direct entity-on-entity damage where the damager is a player
 * counts - projectile hits have an arrow as the damager and are skipped, since
 * reach and aim angle are melee concepts.
 */
public class CombatListener implements Listener {

    private final McGuardConfig config;
    private final CheckEngine engine;
    private final PlayerDataManager players;

    public CombatListener(McGuardConfig config, CheckEngine engine, PlayerDataManager players) {
        this.config = config;
        this.engine = engine;
        this.players = players;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.enabled) return;
        if (!(event.getDamager() instanceof Player player)) return;
        engine.runCombatChecks(player, players.get(player), event.getEntity());
    }
}
