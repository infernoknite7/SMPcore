package com.smpcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Message + parsing helpers. */
public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    public static final String PREFIX = "&8[&bSMP&8] &7";

    private Msg() {}

    public static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(c(PREFIX + s));
    }

    public static void err(CommandSender to, String s) {
        to.sendMessage(c(PREFIX + "&c" + s));
    }

    /** Returns -2 if invalid, -1 if permanent, otherwise milliseconds. */
    public static long parseDuration(String s) {
        s = s.toLowerCase();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) return -1;
        if (s.length() < 2) return -2;
        char unit = s.charAt(s.length() - 1);
        long n;
        try {
            n = Long.parseLong(s.substring(0, s.length() - 1));
        } catch (NumberFormatException e) {
            return -2;
        }
        if (n <= 0) return -2;
        return switch (unit) {
            case 's' -> n * 1000L;
            case 'm' -> n * 60_000L;
            case 'h' -> n * 3_600_000L;
            case 'd' -> n * 86_400_000L;
            case 'w' -> n * 604_800_000L;
            default -> -2;
        };
    }

    public static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public static List<String> filter(Collection<String> options, String typed) {
        List<String> out = new ArrayList<>();
        String t = typed.toLowerCase();
        for (String o : options) if (o.toLowerCase().startsWith(t)) out.add(o);
        return out;
    }
}
