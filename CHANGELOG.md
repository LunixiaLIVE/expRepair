# Changelog

## v1.9.2
- Lowered Java bytecode target to 21 (minimum JVM: Java 21+)
- Mod environment set to `*` — loads correctly in singleplayer and on dedicated servers

## v1.9
- Migrated player data storage from NBT AttachmentTypes to a file-based system (`config/exprepair/playerdata.json`)
- Legacy NBT data auto-migrates and is cleaned up on player join
- Restructured into Architectury multi-loader layout (common + fabric modules)

## v1.0
- Fixed player settings (passive mode, manual mode, threshold) being lost on dimension change or death/respawn
- Enforced mutual exclusion between passive and manual repair modes — enabling one disables the other
- Restructured admin commands: all now under `/er admin`, removed redundant subcommand layers
- `/er admin reload` now broadcasts by default; silent is explicit
- Removed unused session maps and dead code

## Initial Release
- Passive auto-repair: automatically repairs Mending items using XP
- Manual repair: sneak + right-click to repair on demand
- Per-player toggles for passive/manual modes
- Configurable XP threshold (minimum XP floor before repair triggers)
- Configurable max XP consumed per repair tick
- Full admin commands for managing per-player settings and server defaults
- `/er` alias for all `/exprepair` commands
