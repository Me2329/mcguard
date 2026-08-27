package com.mcserver.mcguard.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Runs tasks on the correct thread for whichever platform we are on.
 *
 * Folia splits the world into regions, each ticked on its own thread, and
 * forbids touching an entity from any thread other than the one that owns it.
 * Regular Bukkit/Paper has a single main thread. This adapter hides that:
 *
 *  - {@link #runGlobal} : global state (ban list) -> global region on Folia,
 *    main thread on Bukkit.
 *  - {@link #runOnEntity} : entity operations -> that entity's region on Folia,
 *    main thread on Bukkit.
 *
 * The Folia-only branches live in their own methods, so on a non-Folia server
 * (where {@code isFolia()} is false) the JVM never links the Folia scheduler
 * classes and the jar loads cleanly. Compiled against the Paper API, which
 * carries the Folia scheduler surface even on non-Folia Paper.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA = detectFolia();

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Global/shared state (e.g. the ban list). */
    public void runGlobal(Runnable task) {
        if (FOLIA) {
            runGlobalFolia(task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void runGlobalFolia(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    /** An operation that touches {@code entity} (kick, teleport, effects). */
    public void runOnEntity(Entity entity, Runnable task) {
        if (FOLIA) {
            runOnEntityFolia(entity, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void runOnEntityFolia(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }
}
