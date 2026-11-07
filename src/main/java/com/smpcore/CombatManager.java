package com.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Combat log timer (default 15 seconds). */
public final class CombatManager implements Listener {

    private final SMPCore plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private BukkitTask task;

    public CombatManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isTagged(Player p) {
        Long u = until.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null) {
                    it.remove();
                    continue;
                }
                long left = en.getValue() - now;
                if (left <= 0) {
                    it.remove();
                    p.sendActionBar(Msg.c("&aYou are no longer in combat"));
                } else {
                    p.sendActionBar(Msg.c("&cIn combat: &f" + (int) Math.ceil(left / 1000.0) + "s &7- don't log out!"));
                }
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        until.clear();
    }

    private void tag(Player p) {
        if (p.hasPermission("smp.combat.bypass")) return;
        boolean already = isTagged(p);
        int seconds = plugin.getConfig().getInt("combat.seconds", 15);
        until.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        if (!already) {
            Msg.err(p, "You are in combat! Don't log out for " + seconds + " seconds.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker != null) {
            if (!attacker.equals(victim)) {
                tag(victim);
                tag(attacker);
            }
        } else if (plugin.getConfig().getBoolean("combat.tag-on-mob-damage", false)) {
            tag(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isTagged(p) && !p.isDead()) {
            until.remove(p.getUniqueId());
            p.setHealth(0.0);
            Bukkit.broadcast(Msg.c("&c" + p.getName() + " &7logged out during combat and was killed!"));
        }
        until.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        until.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isTagged(p)) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (plugin.getConfig().getStringList("combat.blocked-commands").contains(label)) {
            e.setCancelled(true);
            Msg.err(p, "You can't use /" + label + " while in combat!");
        }
    }
}
