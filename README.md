# SMPCore (Paper 1.21.11)

One jar with: homes, TPA, bans/mutes, 3-strike warnings, vanish,
ranks (LuckPerms-lite), 15s combat log, and one-player sleep.

## Build
Needs Java 21 + Maven (and internet for Maven to download paper-api):

    mvn package

Jar: `target/SMPCore.jar` -> put it in your server's `plugins/` folder.

## Commands
/home /sethome /delhome /homes
/tpa /tpahere /tpaccept /tpdeny /tpacancel
/ban <player> [duration] [reason]   /unban
/mute <player> [duration] [reason]  /unmute
/warn <player> [reason]  /warnings  /clearwarnings   (3 warnings = 1 day ban)
/vanish (/v)
/rank set|get|list|reload
/smpcore reload

Durations: 30m, 2h, 1d, 1w, perm

## Config files (plugins/SMPCore/)
config.yml, ranks.yml
