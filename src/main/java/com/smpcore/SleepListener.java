package com.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

/** One sleeping player skips the night. */
public final class SleepListener implements Listener {

    private final SMPCore plugin;

    public SleepListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (!plugin.getConfig().getBoolean("sleep.one-player-skip", true)) return;
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player p = e.getPlayer();
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return;

        // Vanilla needs ~5 seconds in bed (100 ticks) before the night is skipped.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !p.isSleeping()) return;
            if (w.getTime() >= 12541) w.setTime(0); // moves forward to the next morning
            w.setStorm(false);
            w.setThundering(false);
            Bukkit.broadcast(Msg.c(Msg.PREFIX + "&b" + p.getName() + " &7slept, so the night was skipped!"));
        }, 101L);
    }
}
