package com.mcserver.mcguard.command;

import com.mcserver.mcguard.McGuardConfig;
import com.mcserver.mcguard.PlayerData;
import com.mcserver.mcguard.PlayerDataManager;
import com.mcserver.mcguard.ViolationManager;
import com.mcserver.mcguard.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /mcguard - operator command surface. Requires the mcguard.admin permission.
 *
 *   /mcguard status              overview of everyone with a non-zero VL
 *   /mcguard vl &lt;player&gt;         per-check breakdown for one player
 *   /mcguard reset &lt;player&gt;      clear a player's violations
 *   /mcguard exempt &lt;player&gt;     toggle exemption (for testing or trusted staff)
 *   /mcguard checks              list every check and its description
 *   /mcguard reload              re-read config.yml
 */
public final class McGuardCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB =
            Arrays.asList("status", "checks", "vl", "reset", "exempt", "reload");

    private final Plugin plugin;
    private final McGuardConfig config;
    private final PlayerDataManager players;
    private final ViolationManager violations;

    public McGuardCommand(Plugin plugin, McGuardConfig config,
                          PlayerDataManager players, ViolationManager violations) {
        this.plugin = plugin;
        this.config = config;
        this.players = players;
        this.violations = violations;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcguard.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission (mcguard.admin).");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status":
                return status(sender);
            case "checks":
                return checks(sender);
            case "reload":
                config.reload();
                sender.sendMessage(ChatColor.GREEN + "[McGuard] config.yml reloaded.");
                return true;
            case "vl":
                return vl(sender, resolve(sender, args));
            case "reset":
                return reset(sender, resolve(sender, args));
            case "exempt":
                return exempt(sender, resolve(sender, args));
            default:
                help(sender);
                return true;
        }
    }

    private Player resolve(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mcguard " + args[0] + " <player>");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline: " + args[1]);
        }
        return target;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.AQUA + "--- McGuard ---");
        s.sendMessage(ChatColor.GRAY + "/mcguard status" + ChatColor.DARK_GRAY + " - players carrying violations");
        s.sendMessage(ChatColor.GRAY + "/mcguard vl <player>" + ChatColor.DARK_GRAY + " - per-check breakdown");
        s.sendMessage(ChatColor.GRAY + "/mcguard reset <player>" + ChatColor.DARK_GRAY + " - clear violations");
        s.sendMessage(ChatColor.GRAY + "/mcguard exempt <player>" + ChatColor.DARK_GRAY + " - toggle exemption");
        s.sendMessage(ChatColor.GRAY + "/mcguard checks" + ChatColor.DARK_GRAY + " - list all checks");
        s.sendMessage(ChatColor.GRAY + "/mcguard reload" + ChatColor.DARK_GRAY + " - reload config");
    }

    private boolean status(CommandSender s) {
        s.sendMessage(ChatColor.AQUA + "--- McGuard status ---");
        s.sendMessage(ChatColor.GRAY + "Active: "
                + (config.enabled ? ChatColor.GREEN + "true" : ChatColor.RED + "false"));

        int flagged = 0;
        for (Map.Entry<UUID, PlayerData> e : players.all().entrySet()) {
            PlayerData d = e.getValue();
            if (d.totalVl < 0.5D) continue;
            flagged++;
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : e.getKey().toString();
            String check = d.lastFlagCheck != null ? d.lastFlagCheck.display() : "-";
            ChatColor colour = d.totalVl >= config.warnThreshold ? ChatColor.RED : ChatColor.YELLOW;
            s.sendMessage(colour + String.format("  %-16s VL %.1f  last: %s", name, d.totalVl, check));
        }
        if (flagged == 0) {
            s.sendMessage(ChatColor.GREEN + "  No players currently carrying violations.");
        }
        return true;
    }

    private boolean vl(CommandSender s, Player target) {
        if (target == null) return true;
        PlayerData d = players.get(target);
        s.sendMessage(ChatColor.AQUA + "--- " + target.getName() + " ---");
        s.sendMessage(ChatColor.YELLOW + String.format("Total VL: %.1f", d.totalVl));
        s.sendMessage(ChatColor.GRAY + "Exempt: " + d.exempt);
        for (CheckType t : CheckType.values()) {
            double v = d.vlOf(t);
            if (v < 0.1D) continue;
            s.sendMessage(ChatColor.GRAY + String.format("  %-12s %.1f", t.display(), v));
        }
        if (!d.lastFlagDetail.isEmpty()) {
            s.sendMessage(ChatColor.DARK_GRAY + "  last: " + d.lastFlagDetail);
        }
        return true;
    }

    private boolean reset(CommandSender s, Player target) {
        if (target == null) return true;
        violations.clear(players.get(target));
        s.sendMessage(ChatColor.GREEN + "Cleared violations for " + target.getName());
        return true;
    }

    private boolean exempt(CommandSender s, Player target) {
        if (target == null) return true;
        PlayerData d = players.get(target);
        d.exempt = !d.exempt;
        s.sendMessage(ChatColor.YELLOW + (d.exempt ? "Exempted " : "Un-exempted ") + target.getName());
        return true;
    }

    private boolean checks(CommandSender s) {
        s.sendMessage(ChatColor.AQUA + "--- McGuard checks ---");
        for (CheckType t : CheckType.values()) {
            s.sendMessage(ChatColor.GRAY + String.format("  %-12s w=%.1f  %s",
                    t.display(), t.weight(), t.description()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUB) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("vl") || sub.equals("reset") || sub.equals("exempt")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) names.add(p.getName());
                }
                return names;
            }
        }
        return new ArrayList<>();
    }
}
