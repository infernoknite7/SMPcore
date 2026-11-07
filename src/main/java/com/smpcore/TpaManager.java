package com.smpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /tpa /tpahere /tpaccept /tpdeny /tpacancel */
public final class TpaManager implements TabExecutor, Listener {

    private record Request(UUID from, boolean here, long expires) {}

    private final SMPCore plugin;
    /** key = player who received the request */
    private final Map<UUID, Request> pending = new HashMap<>();

    public TpaManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.err(sender, "Only players can use this.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "tpa" -> request(p, args, false);
            case "tpahere" -> request(p, args, true);
            case "tpaccept" -> accept(p);
            case "tpdeny" -> deny(p);
            case "tpacancel" -> cancel(p);
        }
        return true;
    }

    private void request(Player p, String[] args, boolean here) {
        if (args.length < 1) {
            Msg.err(p, "Usage: /" + (here ? "tpahere" : "tpa") + " <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || (plugin.vanish.isVanished(target) && !p.hasPermission("smp.vanish.see"))) {
            Msg.err(p, "That player isn't online.");
            return;
        }
        if (target.equals(p)) {
            Msg.err(p, "You can't teleport to yourself.");
            return;
        }
        if (plugin.combat.isTagged(p)) {
            Msg.err(p, "You can't teleport while in combat!");
            return;
        }
        int timeout = plugin.getConfig().getInt("tpa.timeout-seconds", 60);
        pending.put(target.getUniqueId(), new Request(p.getUniqueId(), here, System.currentTimeMillis() + timeout * 1000L));

        Msg.send(p, "Request sent to &b" + target.getName() + "&7. It expires in " + timeout + "s.");
        String text = here ? " &7wants you to teleport to them." : " &7wants to teleport to you.";
        target.sendMessage(Msg.c(Msg.PREFIX + "&b" + p.getName() + text));
        target.sendMessage(Msg.c(Msg.PREFIX)
                .append(Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept")))
                .append(Component.text("  "))
                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny"))));
    }

    private void accept(Player p) {
        Request r = pending.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            Msg.err(p, "You have no pending teleport requests.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            Msg.err(p, "That player is no longer online.");
            return;
        }
        if (plugin.combat.isTagged(from) || plugin.combat.isTagged(p)) {
            Msg.err(p, "Teleporting is blocked while either player is in combat.");
            Msg.err(from, "Teleporting is blocked while either player is in combat.");
            return;
        }
        if (r.here()) {
            p.teleport(from.getLocation());
        } else {
            from.teleport(p.getLocation());
        }
        Msg.send(p, "Teleport request accepted.");
        Msg.send(from, "&b" + p.getName() + " &7accepted your request.");
    }

    private void deny(Player p) {
        Request r = pending.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            Msg.err(p, "You have no pending teleport requests.");
            return;
        }
        Msg.send(p, "Teleport request denied.");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) Msg.send(from, "&b" + p.getName() + " &7denied your request.");
    }

    private void cancel(Player p) {
        boolean removed = pending.entrySet().removeIf(e -> e.getValue().from().equals(p.getUniqueId()));
        if (removed) Msg.send(p, "Your teleport request was cancelled.");
        else Msg.err(p, "You have no outgoing requests.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pending.remove(id);
        pending.entrySet().removeIf(en -> en.getValue().from().equals(id));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase();
        if (args.length == 1 && (n.equals("tpa") || n.equals("tpahere"))) return null; // online players
        return List.of();
    }
}
