package com.ravekits.core;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

final class RegionService {
    void verify(ConfigService.Settings s) {
        World world = Bukkit.getWorld(s.world());
        if (world==null) throw new IllegalArgumentException("Configured world is missing: "+s.world());
        RegionManager manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager==null) throw new IllegalArgumentException("WorldGuard manager unavailable for "+s.world());
        if (manager.getRegion(s.safeRegion())==null) throw new IllegalArgumentException("WorldGuard safe region not found: "+s.safeRegion());
        if (manager.getRegion(s.pitRegion())==null) throw new IllegalArgumentException("WorldGuard pit region not found: "+s.pitRegion());
    }
    private boolean contains(Location loc, String world, String region) {
        if (loc==null || loc.getWorld()==null || !loc.getWorld().getName().equals(world)) return false;
        RegionManager manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(loc.getWorld()));
        if (manager==null) return false;
        ApplicableRegionSet set=manager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
        for (ProtectedRegion r:set) if (r.getId().equalsIgnoreCase(region)) return true;
        return false;
    }
    boolean pit(Location l,ConfigService.Settings s) { return contains(l,s.world(),s.pitRegion()); }
    boolean safe(Location l,ConfigService.Settings s) { return contains(l,s.world(),s.safeRegion()); }
}
