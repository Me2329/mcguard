package com.mcserver.mcguard;

import com.mcserver.mcguard.check.CheckEngine;
import com.mcserver.mcguard.check.CheckType;
import com.mcserver.mcguard.command.McGuardCommand;
import com.mcserver.mcguard.listener.CombatListener;
import com.mcserver.mcguard.listener.MovementListener;
import com.mcserver.mcguard.listener.PlayerConnectionListener;
import com.mcserver.mcguard.listener.WorldListener;
import com.mcserver.mcguard.util.FoliaScheduler;
import com.mcserver.mcguard.util.ItemUse;
import com.mcserver.mcguard.util.ViolationLogger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * McGuard - lightweight, event-based server-side anticheat.
 *
 * Design notes:
 *  - Pure Bukkit event listeners. No mixins, no packet interception, no NMS.
 *    That is what lets one jar run on CraftBukkit, Spigot, Paper, Purpur,
 *    Folia and hybrids (Arclight, Mohist) from a single build.
 *  - Checks never teleport, cancel movement, or kick directly. They raise
 *    violation levels; {@link ViolationManager} decides what happens. That
 *    separation is what stops one noisy check from ruining gameplay.
 *  - Folia-safe: per-player state lives in a ConcurrentHashMap, each player's
 *    events run on their own region thread, punishment happens inline on that
 *    thread, and the only shared-state write (the ban list) is routed through
 *    the global region scheduler.
 *  - Defaults deliberately under-flag. A false positive on a legitimate player
 *    is far more damaging to a server than a cheater surviving an extra 30s.
 */
public final class McGuardPlugin extends JavaPlugin {

    private McGuardConfig config;
    private PlayerDataManager players;
    private ViolationManager violations;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new McGuardConfig(this);

        FoliaScheduler scheduler = new FoliaScheduler(this);
        ViolationLogger fileLog = new ViolationLogger(this, config);
        violations = new ViolationManager(config, getLogger(), scheduler, fileLog);
        players = new PlayerDataManager();
        CheckEngine engine = new CheckEngine(config, violations);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(config, players), this);
        pm.registerEvents(new MovementListener(config, engine, players, violations), this);
        pm.registerEvents(new CombatListener(config, engine, players), this);
        pm.registerEvents(new WorldListener(config, engine, players), this);

        McGuardCommand cmd = new McGuardCommand(this, config, players, violations);
        if (getCommand("mcguard") != null) {
            getCommand("mcguard").setExecutor(cmd);
            getCommand("mcguard").setTabCompleter(cmd);
        }

        // Seed tracking for anyone already online (e.g. after a plugin reload).
        for (Player p : getServer().getOnlinePlayers()) {
            PlayerData d = new PlayerData();
            d.reset(p.getLocation());
            d.joinGraceUntil = System.currentTimeMillis() + config.joinGraceMs;
            players.put(p, d);
        }

        getLogger().info("McGuard enabled - " + CheckType.values().length + " checks | platform: "
                + (FoliaScheduler.isFolia() ? "Folia" : "Bukkit/Spigot/Paper")
                + " | item-use API: " + (ItemUse.hasHandRaisedApi() ? "full" : "shield-only"));
    }

    @Override
    public void onDisable() {
        if (players != null) players.clear();
        getLogger().info("McGuard disabled");
    }

    public McGuardConfig configuration() { return config; }
    public PlayerDataManager players() { return players; }
    public ViolationManager violations() { return violations; }
}
