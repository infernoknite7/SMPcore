package com.smpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** TAB list: header/footer, rank prefixes in names, and rank-based sorting. */
public final class TabManager implements Listener {

    private final SMPCore plugin;
    private BukkitTask task;

    public TabManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        long ticks = Math.max(10L, plugin.getConfig().getLong("tab.update-ticks", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(board.getTeams())) {
            if (t.getName().startsWith("smp_")) t.unregister();
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    public void update(Player p) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;

        String prefix = plugin.ranks.getPrefix(p);
        p.playerListName(Msg.c(prefix + p.getName()));
        updateTeam(p, prefix);

        Component header = join(plugin.getConfig().getStringList("tab.header"), p);
        Component footer = join(plugin.getConfig().getStringList("tab.footer"), p);
        p.sendPlayerListHeaderAndFooter(header, footer);
    }

    private void updateTeam(Player p, String prefix) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        int order = Math.max(0, 99 - Math.min(99, plugin.ranks.getWeight(p)));
        String teamName = String.format(Locale.ROOT, "smp_%02d_%s", order, plugin.ranks.getRank(p.getUniqueId()));
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(Msg.c(prefix));

        String entry = p.getName();
        if (!team.hasEntry(entry)) {
            for (Team other : board.getTeams()) {
                if (other.getName().startsWith("smp_") && other.hasEntry(entry)) other.removeEntry(entry);
            }
            team.addEntry(entry);
        }
    }

    private Component join(List<String> lines, Player p) {
        List<Component> parts = new ArrayList<>();
        for (String line : lines) parts.add(Msg.c(fill(line, p)));
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    private String fill(String s, Player p) {
        long online = Bukkit.getOnlinePlayers().stream().filter(x -> !plugin.vanish.isVanished(x)).count();
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        return s.replace("%online%", String.valueOf(online))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%ping%", String.valueOf(p.getPing()))
                .replace("%tps%", String.format(Locale.ROOT, "%.1f", tps))
                .replace("%player%", p.getName())
                .replace("%rank%", plugin.ranks.getRank(p.getUniqueId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> update(e.getPlayer()));
    }
}
