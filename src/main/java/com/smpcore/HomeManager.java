package com.smpcore;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** /home /sethome /delhome /homes */
public final class HomeManager implements TabExecutor {

    private final SMPCore plugin;
    private final Store store;

    public HomeManager(SMPCore plugin) {
        this.plugin = plugin;
        this.store = new Store(plugin, "homes.yml");
    }

    private Map<String, Location> homesOf(UUID id) {
        Map<String, Location> m = new TreeMap<>();
        ConfigurationSection s = store.get().getConfigurationSection(id.toString());
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Location l = s.getLocation(k);
                if (l != null) m.put(k, l);
            }
        }
        return m;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.err(sender, "Only players can use this.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "sethome" -> setHome(p, args);
            case "home" -> goHome(p, args);
            case "delhome" -> delHome(p, args);
            case "homes" -> listHomes(p);
        }
        return true;
    }

    private void setHome(Player p, String[] args) {
        String name = (args.length > 0 ? args[0] : "home").toLowerCase();
        if (!name.matches("[a-z0-9_-]{1,16}")) {
            Msg.err(p, "Home names can use letters, numbers, - and _ (max 16 characters).");
            return;
        }
        Map<String, Location> homes = homesOf(p.getUniqueId());
        int max = plugin.getConfig().getInt("homes.max", 3);
        if (!homes.containsKey(name) && homes.size() >= max && !p.hasPermission("smp.homes.unlimited")) {
            Msg.err(p, "You've reached your home limit (" + max + "). Use /delhome first.");
            return;
        }
        store.get().set(p.getUniqueId() + "." + name, p.getLocation());
        store.save();
        Msg.send(p, "Home &b" + name + " &7set.");
    }

    private void goHome(Player p, String[] args) {
        Map<String, Location> homes = homesOf(p.getUniqueId());
        if (homes.isEmpty()) {
            Msg.err(p, "You have no homes. Use /sethome first.");
            return;
        }
        String name;
        if (args.length > 0) name = args[0].toLowerCase();
        else if (homes.containsKey("home")) name = "home";
        else if (homes.size() == 1) name = homes.keySet().iterator().next();
        else {
            listHomes(p);
            return;
        }
        Location loc = homes.get(name);
        if (loc == null) {
            Msg.err(p, "No home called '" + name + "'. Your homes: " + String.join(", ", homes.keySet()));
            return;
        }
        if (plugin.combat.isTagged(p)) {
            Msg.err(p, "You can't teleport while in combat!");
            return;
        }
        p.teleport(loc);
        Msg.send(p, "Teleported to home &b" + name + "&7.");
    }

    private void delHome(Player p, String[] args) {
        if (args.length < 1) {
            Msg.err(p, "Usage: /delhome <name>");
            return;
        }
        String name = args[0].toLowerCase();
        if (!homesOf(p.getUniqueId()).containsKey(name)) {
            Msg.err(p, "No home called '" + name + "'.");
            return;
        }
        store.get().set(p.getUniqueId() + "." + name, null);
        store.save();
        Msg.send(p, "Home &b" + name + " &7deleted.");
    }

    private void listHomes(Player p) {
        Map<String, Location> homes = homesOf(p.getUniqueId());
        if (homes.isEmpty()) {
            Msg.send(p, "You have no homes. Use &b/sethome&7.");
            return;
        }
        Msg.send(p, "Your homes: &b" + String.join("&7, &b", homes.keySet()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase();
        if (args.length == 1 && sender instanceof Player p && (n.equals("home") || n.equals("delhome"))) {
            return Msg.filter(homesOf(p.getUniqueId()).keySet(), args[0]);
        }
        return List.of();
    }
}
