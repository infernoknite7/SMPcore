package com.smpcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SMPCore extends JavaPlugin {

    public CombatManager combat;
    public RankManager ranks;
    public VanishManager vanish;
    public PunishManager punish;
    public HomeManager homes;
    public TpaManager tpa;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("ranks.yml");

        combat = new CombatManager(this);
        ranks = new RankManager(this);
        vanish = new VanishManager(this);
        punish = new PunishManager(this);
        homes = new HomeManager(this);
        tpa = new TpaManager(this);

        PluginManager pm = getServer().getPluginManager();
        Listener[] listeners = {
                combat, ranks, vanish, punish, tpa,
                new SleepListener(this)
        };
        for (Listener l : listeners) pm.registerEvents(l, this);

        for (String c : new String[]{"home", "sethome", "delhome", "homes"}) cmd(c, homes);
        for (String c : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel"}) cmd(c, tpa);
        for (String c : new String[]{"ban", "unban", "mute", "unmute", "warn", "warnings", "clearwarnings"}) cmd(c, punish);
        cmd("vanish", vanish);
        cmd("rank", ranks);

        ranks.reload();
        combat.start();
        vanish.start();

        getLogger().info("SMPCore enabled.");
    }

    @Override
    public void onDisable() {
        if (combat != null) combat.stop();
        if (vanish != null) vanish.stop();
    }

    private void cmd(String name, TabExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        c.setExecutor(executor);
        c.setTabCompleter(executor);
    }

    private void saveIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    /** Handles /smpcore */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            ranks.reload();
                Msg.send(sender, "Config and ranks reloaded.");
        } else {
            Msg.send(sender, "Usage: /smpcore reload");
        }
        return true;
    }
}
