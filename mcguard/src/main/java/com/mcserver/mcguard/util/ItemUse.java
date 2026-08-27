package com.mcserver.mcguard.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Bridges "is the player actively using an item" across API flavours.
 *
 * Paper exposes {@code LivingEntity#isHandRaised()}, true while eating,
 * drinking, blocking with a shield, or drawing a bow/crossbow. Some Spigot
 * builds and older hybrids predate it, so we resolve it reflectively once and
 * fall back to the always-present {@code isBlocking()} (shield only) when it is
 * absent. This keeps the plugin loading cleanly everywhere instead of throwing
 * NoSuchMethodError the first time NoSlow runs on a server without the method.
 */
public final class ItemUse {

    private static final Method HAND_RAISED = resolve();

    private ItemUse() { }

    private static Method resolve() {
        try {
            Method m = Player.class.getMethod("isHandRaised");
            if (m.getReturnType() == boolean.class) {
                return m;
            }
        } catch (Throwable ignored) {
            // Method not present on this platform - fall back to isBlocking().
        }
        return null;
    }

    /** True on platforms where the richer hand-raised signal is available. */
    public static boolean hasHandRaisedApi() {
        return HAND_RAISED != null;
    }

    public static boolean isUsingItem(Player player) {
        if (HAND_RAISED != null) {
            try {
                return (boolean) HAND_RAISED.invoke(player);
            } catch (Throwable ignored) {
                // Fall through to the shield-only signal.
            }
        }
        return player.isBlocking();
    }
}
