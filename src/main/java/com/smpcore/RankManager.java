package com.smpcore;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lightweight rank/permission system (LuckPerms-lite) + chat prefixes. */
public final class RankManager implements TabExecutor, Listener {

    private final SMPCore plugin;
    private final Store players;
    private YamlConfiguration ranks;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public RankManager(SMPCore plugin) {
        this.plugin = plugin;
        this.players = new Store(plugin, "playerranks.yml");
        this.ranks = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "ranks.yml"));
    }

    public void reload() {
        ranks = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "ranks.yml"));
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    public String defaultRank() {
        return ranks.getString("default-rank", "member").toLowerCase();
    }

    public String getRank(UUID id) {
        String r = players.get().getString(id.toString());
        if (r != null && ranks.contains("ranks." + r)) return r;
        return defaultRank();
    }

    public String getPrefix(Player p) {
        return ranks.getString("ranks." + getRank(p.getUniqueId()) + ".prefix", "");
    }

    public int getWeight(Player p) {
        return ranks.getInt("ranks." + getRank(p.getUniqueId()) + ".weight", 0);
    }

    private void collect(String rank, Set<String> out, Set<String> seen) {
        if (!seen.add(rank)) return;
        ConfigurationSection s = ranks.getConfigurationSection("ranks." + rank);
        if (s == null) return;
        for (String parent : s.getStringList("inherits")) collect(parent.toLowerCase(), out, seen);
        out.addAll(s.getStringList("permissions"));
    }

    public void apply(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        PermissionAttachment att = p.addAttachment(plugin);
        Set<String> perms = new LinkedHashSet<>();
        collect(getRank(p.getUniqueId()), perms, new HashSet<>());
        for (String perm : perms) {
            if (perm.startsWith("-")) att.setPermission(perm.substring(1), false);
            else att.setPermission(perm, true);
        }
        attachments.put(p.getUniqueId(), att);
        p.updateCommands();
    }

    // ---------- events ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        attachments.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String prefix = getPrefix(e.getPlayer());
        e.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
                Msg.c(prefix)
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.WHITE))));
    }

    // ---------- commands ----------

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            Msg.err(sender, "Usage: /rank <set <player> <rank> | get <player> | list | reload>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                ConfigurationSection sec = ranks.getConfigurationSection("ranks");
                Msg.send(sender, "Ranks: &b" + (sec == null ? "none" : String.join("&7, &b", sec.getKeys(false))));
            }
            case "reload" -> {
                reload();
                Msg.send(sender, "Ranks reloaded.");
            }
            case "get" -> {
                if (args.length < 2) {
                    Msg.err(sender, "Usage: /rank get <player>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                Msg.send(sender, "&b" + args[1] + " &7has rank &b" + getRank(t.getUniqueId()) + "&7.");
            }
            case "set" -> {
                if (args.length < 3) {
                    Msg.err(sender, "Usage: /rank set <player> <rank>");
                    return true;
                }
                String rank = args[2].toLowerCase();
                if (!ranks.contains("ranks." + rank)) {
                    Msg.err(sender, "Unknown rank. Use /rank list.");
                    return true;
                }
                Player online = Bukkit.getPlayerExact(args[1]);
                OfflinePlayer t = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
                if (online == null && !t.hasPlayedBefore()) {
                    Msg.err(sender, "That player has never joined.");
                    return true;
                }
                players.get().set(t.getUniqueId().toString(), rank);
                players.save();
                if (online != null) {
                    apply(online);
                }
                Msg.send(sender, "Set &b" + args[1] + "&7's rank to &b" + rank + "&7.");
            }
            default -> Msg.err(sender, "Usage: /rank <set|get|list|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Msg.filter(List.of("set", "get", "list", "reload"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("get"))) return null;
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            ConfigurationSection sec = ranks.getConfigurationSection("ranks");
            return sec == null ? List.of() : Msg.filter(new ArrayList<>(sec.getKeys(false)), args[2]);
        }
        return List.of();
    }
}
