# Game Administrator (NeoForge 1.21.1)

Press **G** for the Admin Console (17 abilities, Powers tab, Passives tab). **V** repeats your last ability, **H** toggles flight.
Abilities hit every living thing near you except yourself. World VFX (rings, beams, bubbles, black hole) are server-side so
everyone sees them. No particles. If world effects do not appear, search `logs/latest.log` for `Command rejected by the parser`.

Time Stop uses the real `/tick freeze` (8 seconds). Game rules use the camelCase names of 1.21.1.
### Servers
Install the jar on the server (and on every player who wants the console). Players without the mod can still join; they just cannot use it.
Who can use the powers: the single player/LAN host, server owners (permission level 4), and anyone an operator grants:

- `/gameadmin grant <player>` give access (operators, permission level 2+)
- `/gameadmin revoke <player>` take it away
- `/gameadmin list` who was granted
- `/gameadmin status` check your own access

Grants are saved in `config/gameadmin-admins.txt` (one name per line); you can also edit that file by hand. Pressing G without access tells you so.

## Install
Minecraft **1.21.1**, **NeoForge 21.1+**, Java 21. Put the jar in `mods/`.

## Build on GitHub
Upload to a NEW repo. Actions tab -> "Build mod" -> download the `gameadmin-1.21.1-neoforge` artifact.
