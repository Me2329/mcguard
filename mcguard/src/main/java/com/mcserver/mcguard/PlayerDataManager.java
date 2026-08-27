package com.mcserver.mcguard;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns per-player tracking state, keyed by UUID.
 *
 * The backing map is a {@link ConcurrentHashMap} because on Folia different
 * players are ticked on different region threads, so lookups and removals can
 * race. Each individual {@link PlayerData} is still only touched by one thread
 * (the one that owns that player), so it needs no internal synchronisation.
 */
public final class PlayerDataManager {

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    public PlayerData get(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), u -> new PlayerData());
    }

    public void put(Player player, PlayerData data) {
        players.put(player.getUniqueId(), data);
    }

    public void remove(Player player) {
        players.remove(player.getUniqueId());
    }

    public Map<UUID, PlayerData> all() {
        return players;
    }

    public void clear() {
        players.clear();
    }
}
