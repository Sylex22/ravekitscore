# RaveKits Core — status and limitations

**Status: DEVELOPMENT SOURCE; NOT COMPILED OR SERVER-TESTED.**

Implemented in source:
- WorldGuard region validation, arena spawn/entry/exit, combat tagging and re-entry cooldown.
- Three starter kit snapshots; create, delete, edit via native chest GUI, weighted kit selection.
- Lobby hotbar and native chest-menu routing.
- Physical tagged ore drops from scanned natural ore positions; safe-zone exchanges; quit/death cleanup.
- XP, levels, coins, basic crate keys, basic kit/cosmetic unlocks.
- SQLite profiles, migration columns, async queued writes.
- Optional PlaceholderAPI hooks; configurable spawn, XP values, ore XP and timers.
- First-change arena reset tracking with bounded restore batches.

NOT IMPLEMENTED TO THE HANDOFF'S FULL SPECIFICATION:
- 7-tier x 2 crate tables, crate animations, tier-specific level reward rules or weighted rarity by tier.
- Kit odds upgrades, 100% configurable native GUI layouts, complete cosmetic kill effects/death messages.
- Season reset and hall of fame, leaderboards, sidebar, settings toggles.
- Exact handling of every potential inventory duplication vector and every fire/lava/explosion edge case.
- Permanent persisted arena baseline (tracked modified blocks are currently only kept in RAM).
- Complete server playtesting, performance benchmarks, and all specified admin commands.
- Fully verified integration with Minecraft 1.21.11, Citizens and external plugins.

Risks requiring testing:
- Plugin API build compatibility.
- ItemStack serialisation / inventory layout consistency.
- WorldGuard flag interactions for TNT and block modifications.
- Full-inventory mining and tagged-ore handling through hopper/container/crafting interactions.
- Concurrent cache writes, restart persistence, crash recovery and reward transaction durability.
- Arena reset interactions with ore respawning; TNT/fire/lava effects may not be fully tracked.
- Command routing around `/help`, WorldGuard and other plugins.

Do not deploy to the live server until build errors, startup errors and above gameplay tests are resolved.
