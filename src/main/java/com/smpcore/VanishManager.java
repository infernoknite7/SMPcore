package com.smpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** /vanish */
public final class VanishManager implements TabExecutor, Listener {

    private final SMPCore plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private BukkitTask task;

    public VanishManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : vanished) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendActionBar(Msg.c("&bYou are vanished"));
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : new HashSet<>(vanished)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) show(p);
        }
        vanished.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.err(sender, "Only players can vanish.");
            return true;
        }
        if (isVanished(p)) {
            vanished.remove(p.getUniqueId());
            show(p);
            Bukkit.broadcast(Component.translatable("multiplayer.player.joined", Component.text(p.getName()))
                    .color(NamedTextColor.YELLOW));
            Msg.send(p, "You are now &bvisible&7.");
        } else {
            vanished.add(p.getUniqueId());
            hide(p);
            Bukkit.broadcast(Component.translatable("multiplayer.player.left", Component.text(p.getName()))
                    .color(NamedTextColor.YELLOW));
            Msg.send(p, "You are now &bvanished&7.");
        }
        return true;
    }

    private void hide(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && !o.hasPermission("smp.vanish.see")) o.hidePlayer(plugin, p);
        }
        p.setSleepingIgnored(true);
        p.setCanPickupItems(false);
    }

    private void show(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) o.showPlayer(plugin, p);
        }
        p.setSleepingIgnored(false);
        p.setCanPickupItems(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joiner = e.getPlayer();
        if (joiner.hasPermission("smp.vanish.see")) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) joiner.hidePlayer(plugin, v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (vanished.remove(e.getPlayer().getUniqueId())) {
            e.quitMessage(null); // vanished players leave silently
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return List.of();
    }
}
