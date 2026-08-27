package com.mcserver.mcguard.util;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.check.CheckType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Appends one CSV row per flag to plugins/McGuard/violations.log.
 *
 * The format is stable and machine-readable so an external dashboard can
 * aggregate it:
 *   timestamp,player,uuid,check,vl,detail
 *
 * Writes are guarded by a lock because on Folia two region threads can flag
 * two different players at the same instant.
 */
public final class ViolationLogger {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Plugin plugin;
    private final McGuardConfig config;
    private final Object lock = new Object();
    private File file;

    public ViolationLogger(Plugin plugin, McGuardConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void log(Player player, CheckType check, double vl, String detail) {
        if (!config.logToFile) return;
        synchronized (lock) {
            try {
                if (file == null) {
                    plugin.getDataFolder().mkdirs();
                    file = new File(plugin.getDataFolder(), "violations.log");
                    if (!file.exists()) {
                        Files.write(file.toPath(),
                                "timestamp,player,uuid,check,vl,detail\n".getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.CREATE);
                    }
                }
                String safeDetail = detail.replace(',', ';').replace('\n', ' ');
                String line = String.format("%s,%s,%s,%s,%.1f,%s%n",
                        LocalDateTime.now().format(TS),
                        player.getName(),
                        player.getUniqueId(),
                        check.name(),
                        vl,
                        safeDetail);
                Files.write(file.toPath(), line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write violation log: " + e.getMessage());
            }
        }
    }
}
