package com.smpcore;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Bans, mutes and warnings (3 warnings = 1 day ban by default). */
public final class PunishManager implements TabExecutor, Listener {

    private record Entry(String name, String reason, String by, long until) {
        boolean expired() {
            return until > 0 && System.currentTimeMillis() > until;
        }

        String remaining() {
            return until < 0 ? "Never (permanent)" : Msg.formatDuration(until - System.currentTimeMillis());
        }
    }

    private final SMPCore plugin;
    private final Store store;

    public PunishManager(SMPCore plugin) {
        this.plugin = plugin;
        this.store = new Store(plugin, "punishments.yml");
    }

    // ---------- storage ----------

    private synchronized Entry getEntry(String type, UUID id) {
        ConfigurationSection s = store.get().getConfigurationSection(type + "." + id);
        if (s == null) return null;
        Entry e = new Entry(s.getString("name", "?"), s.getString("reason", "No reason given"),
                s.getString("by", "Console"), s.getLong("until", -1));
        if (e.expired()) {
            store.get().set(type + "." + id, null);
            store.save();
            return null;
        }
        return e;
    }

    private synchronized void putEntry(String type, UUID id, Entry e) {
        String base = type + "." + id + ".";
        store.get().set(base + "name", e.name());
        store.get().set(base + "reason", e.reason());
        store.get().set(base + "by", e.by());
        store.get().set(base + "until", e.until());
        store.save();
    }

    private synchronized boolean removeEntry(String type, UUID id) {
        boolean had = store.get().contains(type + "." + id);
        if (had) {
            store.get().set(type + "." + id, null);
            store.save();
        }
        return had;
    }

    private synchronized UUID findId(String type, String name) {
        ConfigurationSection sec = store.get().getConfigurationSection(type);
        if (sec == null) return null;
        for (String k : sec.getKeys(false)) {
            if (name.equalsIgnoreCase(sec.getString(k + ".name"))) return UUID.fromString(k);
        }
        return null;
    }

    private synchronized List<String> namesOf(String type) {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = store.get().getConfigurationSection(type);
        if (sec != null) for (String k : sec.getKeys(false)) out.add(sec.getString(k + ".name", k));
        return out;
    }

    // ---------- helpers ----------

    @SuppressWarnings("deprecation")
    private OfflinePlayer target(CommandSender s, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (!op.hasPlayedBefore()) {
            Msg.err(s, "Player '" + name + "' has never joined this server.");
            return null;
        }
        return op;
    }

    private String nameOf(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString();
    }

    private void announce(String msg) {
        if (plugin.getConfig().getBoolean("punishments.broadcast", true)) {
            Bukkit.broadcast(Msg.c(Msg.PREFIX + msg));
        }
    }

    private Component banScreen(Entry e) {
        return Msg.c("&c&lYou are banned from this server\n\n&7Reason: &f" + e.reason()
                + "\n&7Expires in: &f" + e.remaining() + "\n&7Banned by: &f" + e.by());
    }

    // ---------- commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "ban" -> issue(sender, args, true);
            case "mute" -> issue(sender, args, false);
            case "unban" -> lift(sender, args, true);
            case "unmute" -> lift(sender, args, false);
            case "warn" -> warn(sender, args);
            case "warnings" -> warnings(sender, args);
            case "clearwarnings" -> clearWarnings(sender, args);
        }
        return true;
    }

    private void issue(CommandSender s, String[] a, boolean ban) {
        String name = ban ? "ban" : "mute";
        if (a.length < 1) {
            Msg.err(s, "Usage: /" + name + " <player> [duration like 30m/2h/1d/perm] [reason]");
            return;
        }
        OfflinePlayer t = target(s, a[0]);
        if (t == null) return;
        long dur = -1;
        int reasonIndex = 1;
        if (a.length > 1) {
            long d = Msg.parseDuration(a[1]);
            if (d != -2) {
                dur = d;
                reasonIndex = 2;
            }
        }
        String reason = a.length > reasonIndex
                ? String.join(" ", Arrays.copyOfRange(a, reasonIndex, a.length)) : "No reason given";
        Entry e = new Entry(nameOf(t), reason, s.getName(), dur < 0 ? -1 : System.currentTimeMillis() + dur);
        putEntry(ban ? "bans" : "mutes", t.getUniqueId(), e);

        Player online = t.getPlayer();
        if (ban) {
            if (online != null) online.kick(banScreen(e));
        } else if (online != null) {
            Msg.err(online, "You have been muted. Reason: " + reason + " | Time left: " + e.remaining());
        }
        announce("&b" + e.name() + " &7was " + (ban ? "banned" : "muted") + " by &f" + s.getName()
                + "&7 (" + (dur < 0 ? "permanent" : Msg.formatDuration(dur)) + "). Reason: &f" + reason);
    }

    private void lift(CommandSender s, String[] a, boolean ban) {
        String name = ban ? "unban" : "unmute";
        if (a.length < 1) {
            Msg.err(s, "Usage: /" + name + " <player>");
            return;
        }
        UUID id = findId(ban ? "bans" : "mutes", a[0]);
        if (id == null || !removeEntry(ban ? "bans" : "mutes", id)) {
            Msg.err(s, a[0] + " is not " + (ban ? "banned." : "muted."));
            return;
        }
        Msg.send(s, "&b" + a[0] + " &7was " + (ban ? "unbanned." : "unmuted."));
        if (!ban) {
            Player on = Bukkit.getPlayer(id);
            if (on != null) Msg.send(on, "You have been unmuted.");
        }
    }

    private void warn(CommandSender s, String[] a) {
        if (a.length < 1) {
            Msg.err(s, "Usage: /warn <player> [reason]");
            return;
        }
        OfflinePlayer t = target(s, a[0]);
        if (t == null) return;
        String reason = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "No reason given";
        int threshold = plugin.getConfig().getInt("warnings.threshold", 3);
        int count;
        boolean ban;

        synchronized (this) {
            String path = "warnings." + t.getUniqueId();
            List<String> list = new ArrayList<>(store.get().getStringList(path + ".list"));
            list.add(System.currentTimeMillis() + ";" + s.getName() + ";" + reason);
            count = list.size();
            ban = count >= threshold;
            if (ban) {
                store.get().set(path, null); // reset after the ban
            } else {
                store.get().set(path + ".name", nameOf(t));
                store.get().set(path + ".list", list);
            }
            store.save();
        }

        Player online = t.getPlayer();
        if (!ban) {
            Msg.send(s, "Warned &b" + nameOf(t) + " &7(" + count + "/" + threshold + ").");
            if (online != null) {
                Msg.err(online, "You were warned by " + s.getName() + ": " + reason
                        + " (" + count + "/" + threshold + "). At " + threshold + " warnings you will be banned.");
            }
            return;
        }

        long dur = Msg.parseDuration(plugin.getConfig().getString("warnings.ban-duration", "1d"));
        if (dur < 0) dur = 86_400_000L;
        Entry e = new Entry(nameOf(t), "Reached " + threshold + " warnings (last: " + reason + ")",
                "Warning system", System.currentTimeMillis() + dur);
        putEntry("bans", t.getUniqueId(), e);
        if (online != null) online.kick(banScreen(e));
        announce("&b" + e.name() + " &7reached &c" + threshold + " warnings &7and was banned for &f"
                + Msg.formatDuration(dur) + "&7.");
    }

    private void warnings(CommandSender s, String[] a) {
        if (a.length < 1) {
            Msg.err(s, "Usage: /warnings <player>");
            return;
        }
        OfflinePlayer t = target(s, a[0]);
        if (t == null) return;
        List<String> list;
        synchronized (this) {
            list = store.get().getStringList("warnings." + t.getUniqueId() + ".list");
        }
        if (list.isEmpty()) {
            Msg.send(s, "&b" + nameOf(t) + " &7has no warnings.");
            return;
        }
        Msg.send(s, "Warnings for &b" + nameOf(t) + "&7:");
        int i = 1;
        for (String line : list) {
            String[] parts = line.split(";", 3);
            if (parts.length < 3) continue;
            String ago;
            try {
                ago = Msg.formatDuration(System.currentTimeMillis() - Long.parseLong(parts[0])) + " ago";
            } catch (NumberFormatException ex) {
                ago = "?";
            }
            s.sendMessage(Msg.c("&8" + i++ + ". &7" + parts[2] + " &8(by " + parts[1] + ", " + ago + ")"));
        }
    }

    private void clearWarnings(CommandSender s, String[] a) {
        if (a.length < 1) {
            Msg.err(s, "Usage: /clearwarnings <player>");
            return;
        }
        OfflinePlayer t = target(s, a[0]);
        if (t == null) return;
        synchronized (this) {
            store.get().set("warnings." + t.getUniqueId(), null);
            store.save();
        }
        Msg.send(s, "Cleared warnings for &b" + nameOf(t) + "&7.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase();
        if (args.length == 1) {
            if (n.equals("unban")) return Msg.filter(namesOf("bans"), args[0]);
            if (n.equals("unmute")) return Msg.filter(namesOf("mutes"), args[0]);
            return null; // online players
        }
        if (args.length == 2 && (n.equals("ban") || n.equals("mute"))) {
            return Msg.filter(List.of("30m", "1h", "1d", "7d", "perm"), args[1]);
        }
        return List.of();
    }

    // ---------- enforcement ----------

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Entry ban = getEntry("bans", e.getUniqueId());
        if (ban != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ban));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Entry mute = getEntry("mutes", e.getPlayer().getUniqueId());
        if (mute != null) {
            e.setCancelled(true);
            Msg.err(e.getPlayer(), "You are muted. Reason: " + mute.reason() + " | Time left: " + mute.remaining());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!plugin.getConfig().getStringList("punishments.muted-blocked-commands").contains(label)) return;
        Entry mute = getEntry("mutes", p.getUniqueId());
        if (mute != null) {
            e.setCancelled(true);
            Msg.err(p, "You can't use /" + label + " while muted.");
        }
    }
}
