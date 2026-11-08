package com.ravekits.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;
import java.util.*;

final class ArenaListener implements Listener {
    private final RaveKitsCore plugin;
    private final Map<UUID,Long> tags=new HashMap<>();
    private final Map<UUID,UUID> lastAttackers=new HashMap<>();
    private final Map<UUID,Long> protectedUntil=new HashMap<>();
    private final Map<UUID,Long> reentryUntil=new HashMap<>();
    private final Set<UUID> inside=new HashSet<>();
    ArenaListener(RaveKitsCore plugin){this.plugin=plugin;}
    boolean tagged(Player p){return tags.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis();}
    boolean inside(Player p){return inside.contains(p.getUniqueId());}
    boolean protectedPlayer(Player p){return protectedUntil.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis();}
    void clear(Player p){ UUID id=p.getUniqueId();tags.remove(id);lastAttackers.remove(id);protectedUntil.remove(id);inside.remove(id); }
    void toSpawn(Player p) {
        plugin.reset().leave(p);
        p.teleport(plugin.settings().spawn());
        plugin.lobby(p);
        UUID id=p.getUniqueId();
        tags.remove(id);lastAttackers.remove(id);protectedUntil.remove(id);inside.remove(id);
        PlayerStore.Profile profile=plugin.store().get(id);
        if(profile!=null)profile.streak=0;
        reentryUntil.put(id,System.currentTimeMillis()+plugin.settings().reentryMs());
        plugin.action(p,plugin.settings().spawnMessage());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void preLogin(AsyncPlayerPreLoginEvent e) {
        if(e.getLoginResult()!=AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try{plugin.store().load(e.getUniqueId());}
        catch(Exception ex){plugin.getLogger().warning("Player load failed: "+e.getUniqueId()+" - "+ex.getMessage());e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,"Unable to load player data safely. Please try again.");}
    }
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        if(plugin.store().get(p.getUniqueId())==null){p.kickPlayer("Player profile unavailable.");return;}
        // Delay a tick to avoid competing with the join event's initial spawn.
        Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()){plugin.mining().clear(p);toSpawn(p);}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if(e.getTo()==null || (e.getFrom().getBlockX()==e.getTo().getBlockX() && e.getFrom().getBlockY()==e.getTo().getBlockY() && e.getFrom().getBlockZ()==e.getTo().getBlockZ() && e.getFrom().getWorld().equals(e.getTo().getWorld())))return;
        Player p=e.getPlayer();UUID id=p.getUniqueId();
        boolean was=inside.contains(id);
        boolean now=plugin.regions().pit(e.getTo(),plugin.settings());
        if(now==was) return;
        if(now){
            long left=reentryUntil.getOrDefault(id,0L)-System.currentTimeMillis();
            if(left>0){e.setTo(e.getFrom());plugin.action(p,plugin.settings().cooldownMessage());return;}
            inside.add(id);
            plugin.reset().enter(p);
            plugin.kits().equip(p);
            protectedUntil.put(id,System.currentTimeMillis()+plugin.settings().protectionMs());
            plugin.action(p,plugin.settings().enterMessage());
        }else{
            if(tagged(p)) {e.setTo(e.getFrom());plugin.action(p,plugin.settings().blockedMessage());return;}
            plugin.reset().leave(p);plugin.lobby(p);
            inside.remove(id);protectedUntil.remove(id);tags.remove(id);lastAttackers.remove(id);
            PlayerStore.Profile profile=plugin.store().get(id);if(profile!=null)profile.streak=0;
            reentryUntil.put(id,System.currentTimeMillis()+plugin.settings().reentryMs());
            plugin.action(p,plugin.settings().exitMessage());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player victim))return;
        Player attacker=null;
        Entity damager=e.getDamager();
        if(damager instanceof Player p)attacker=p;
        else if(damager instanceof Projectile proj){ProjectileSource shooter=proj.getShooter();if(shooter instanceof Player p)attacker=p;}
        if(!inside(victim))return;
        if(attacker!=null && inside(attacker))protectedUntil.remove(attacker.getUniqueId());
        if(protectedPlayer(victim)){e.setCancelled(true);return;}
        if(attacker==null || attacker.equals(victim) || !inside(attacker))return;
        long expires=System.currentTimeMillis()+plugin.settings().tagMs();
        tags.put(victim.getUniqueId(),expires);tags.put(attacker.getUniqueId(),expires);
        lastAttackers.put(victim.getUniqueId(),attacker.getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p && inside(p) && protectedPlayer(p))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        Player victim=e.getEntity();UUID victimId=victim.getUniqueId();
        if(!inside.contains(victimId))return;
        PlayerStore.Profile v=plugin.store().get(victimId);
        if(v!=null){v.deaths++;v.streak=0;}
        UUID attackerId=lastAttackers.get(victimId);
        Player killer=attackerId==null?null:Bukkit.getPlayer(attackerId);
        if(killer!=null && killer.isOnline() && !killer.equals(victim)) {
            PlayerStore.Profile k=plugin.store().get(killer.getUniqueId());
            if(k!=null){k.kills++;k.streak++;k.bestStreak=Math.max(k.bestStreak,k.streak);plugin.progress().kill(killer,victim);}
        }
        plugin.reset().leave(victim);clear(victim);
        e.getDrops().clear();
    }
    @EventHandler public void respawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.settings().spawn());
        UUID id=e.getPlayer().getUniqueId();inside.remove(id);tags.remove(id);protectedUntil.remove(id);lastAttackers.remove(id);
        reentryUntil.put(id,System.currentTimeMillis()+plugin.settings().reentryMs());
        Bukkit.getScheduler().runTask(plugin,()->{
            Player p=e.getPlayer();if(!p.isOnline())return;
            plugin.lobby(p);
            PlayerStore.Profile profile=plugin.store().get(id);
            if(profile!=null&&profile.pendingRewards>0)p.sendTitle("§6Rewards Available","§f"+profile.pendingRewards+" level rewards to claim",10,60,20);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        Player p=e.getPlayer();UUID id=p.getUniqueId();
        if(tagged(p) && plugin.settings().creditOnLogout()) {
            PlayerStore.Profile v=plugin.store().get(id);if(v!=null){v.deaths++;v.streak=0;}
            UUID atk=lastAttackers.get(id);
            if(atk!=null && !atk.equals(id)){
                PlayerStore.Profile k=plugin.store().get(atk);
                if(k!=null){k.kills++;k.streak++;k.bestStreak=Math.max(k.bestStreak,k.streak);}
            }
            // Intentionally no fake death: this only credits statistics. Stage 5 adds tagged-ore destruction.
        }
        plugin.reset().leave(p);clear(p);reentryUntil.remove(id);plugin.store().remove(id);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e){
        Player p=e.getPlayer();if(!tagged(p) || !plugin.settings().blockCommands())return;
        String raw=e.getMessage().substring(1).trim().split(" ")[0].toLowerCase(Locale.ROOT);
        if(plugin.settings().tagAllowed().stream().anyMatch(x->x.equalsIgnoreCase(raw)))return;
        e.setCancelled(true);plugin.action(p,plugin.settings().blockedMessage());
    }
}
