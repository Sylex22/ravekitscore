package com.ravekits.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.List;

final class ConfigService {
    record Settings(String world, String safeRegion, String pitRegion, Location spawn,
                    long reentryMs, long protectionMs, long tagMs, boolean blockCommands,
                    List<String> tagAllowed, boolean creditOnLogout, long autosaveTicks,
                    String sqliteFile, String prefix, String spawnMessage, String blockedMessage,
                    String cooldownMessage, String enterMessage, String exitMessage) {}
    private final RaveKitsCore plugin;
    private volatile Settings current;
    ConfigService(RaveKitsCore plugin) { this.plugin = plugin; }
    Settings get() { return current; }
    Settings load(boolean first) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration c = YamlConfiguration.loadConfiguration(file);
        String worldName = required(c, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("config.yml -> world: '" + worldName + "' not loaded");
        String spawnWorld = required(c, "spawn.world");
        World sWorld = Bukkit.getWorld(spawnWorld);
        if (sWorld == null) throw new IllegalArgumentException("config.yml -> spawn.world: '" + spawnWorld + "' not loaded");
        Location spawn = new Location(sWorld, num(c,"spawn.x"),num(c,"spawn.y"),num(c,"spawn.z"),
                (float)num(c,"spawn.yaw"),(float)num(c,"spawn.pitch"));
        if (!spawn.getBlock().isPassable()) throw new IllegalArgumentException("config.yml -> spawn: position is inside a solid block");
        long cooldown = nonnegative(c,"arena.reentry-cooldown-seconds");
        long protection = nonnegative(c,"arena.spawn-protection-seconds");
        long tag = nonnegative(c,"combat.tag-seconds");
        long autosave = nonnegative(c,"storage.autosave-seconds");
        if (autosave < 10) throw new IllegalArgumentException("config.yml -> storage.autosave-seconds must be >=10");
        String db = required(c,"storage.sqlite-file");
        if (!db.matches("[a-zA-Z0-9_.-]+\\.db")) throw new IllegalArgumentException("config.yml -> storage.sqlite-file must be a simple .db filename");
        Settings candidate = new Settings(worldName,required(c,"regions.safe"),required(c,"regions.pit"),spawn,
                cooldown*1000, protection*1000, tag*1000, c.getBoolean("combat.block-commands",true),
                List.copyOf(c.getStringList("combat.allowed-commands-while-tagged")),
                c.getBoolean("combat.cancel-on-quit-and-credit-last-attacker",true),autosave*20,db,
                required(c,"messages.prefix"), required(c,"messages.spawn"), required(c,"messages.combat-blocked"),
                required(c,"messages.cooldown"),required(c,"messages.arena-enter"),required(c,"messages.arena-exit"));
        // Validate candidate first. Never modify the live settings on a failed reload.
        plugin.regions().verify(candidate);
        if (!first && !candidate.sqliteFile().equals(current.sqliteFile()))
            throw new IllegalArgumentException("config.yml -> storage.sqlite-file cannot change during reload; restart required");
        current = candidate;
        return candidate;
    }
    private static String required(FileConfiguration c,String key) {
        String v=c.getString(key); if (v==null || v.isBlank()) throw new IllegalArgumentException("config.yml -> " +key+" is missing/empty"); return v;
    }
    private static double num(FileConfiguration c,String key) {
        Object o=c.get(key); if (!(o instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException("config.yml -> "+key+" must be a finite number"); return n.doubleValue();
    }
    private static long nonnegative(FileConfiguration c,String key) {
        Object o=c.get(key); if (!(o instanceof Number n) || n.longValue()<0 || n.doubleValue()!=n.longValue())
            throw new IllegalArgumentException("config.yml -> "+key+" must be a non-negative integer"); return n.longValue();
    }
}
