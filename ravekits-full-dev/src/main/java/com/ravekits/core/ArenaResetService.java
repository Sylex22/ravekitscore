package com.ravekits.core;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

/** Tracks first modifications and restores them in bounded batches. */
final class ArenaResetService implements Listener {
    private record Position(UUID world,int x,int y,int z){}
    private final RaveKitsCore plugin;
    private final Map<Position,String> original=new LinkedHashMap<>();
    private final BossBar bar=Bukkit.createBossBar("§6RaveKits §7Arena Reset",BarColor.YELLOW,BarStyle.SOLID);
    private boolean restoring=false;
    private long resetAt;
    ArenaResetService(RaveKitsCore plugin){
        this.plugin=plugin;long period=Math.max(60,plugin.getConfig().getLong("arena.reset-minutes",15)*60);
        resetAt=System.currentTimeMillis()+period*1000;
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            long seconds=Math.max(0,(resetAt-System.currentTimeMillis())/1000);
            bar.setTitle("§6Arena resets in §f"+seconds+"s");bar.setProgress(Math.max(0,Math.min(1,seconds/(double)period)));
            if(seconds==0&&!restoring)reset(null);
        },20L,20L);
    }
    void enter(Player p){bar.addPlayer(p);}
    void leave(Player p){bar.removePlayer(p);}
    void saveBaseline(){original.clear();}
    int pending(){return original.size();}
    private Position position(Block b){return new Position(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
    private void track(Block b){track(b,b.getBlockData().getAsString());}
    private void track(Block b,String oldBlockData){
        if(restoring||!plugin.regions().pit(b.getLocation(),plugin.settings()))return;
        int limit=plugin.getConfig().getInt("arena.max-tracked-blocks",50000);
        Position pos=position(b);
        if(!original.containsKey(pos)&&original.size()<limit)original.put(pos,oldBlockData);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){track(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){track(e.getBlockReplacedState().getBlock(),e.getBlockReplacedState().getBlockData().getAsString());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(EntityExplodeEvent e){for(Block b:e.blockList())track(b);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void blockExplode(BlockExplodeEvent e){for(Block b:e.blockList())track(b);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void burn(BlockBurnEvent e){track(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spread(BlockSpreadEvent e){track(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void flow(BlockFromToEvent e){
        if(e.getBlock().isLiquid())track(e.getToBlock());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void placementLimit(BlockPlaceEvent e){
        if(!plugin.regions().pit(e.getBlock().getLocation(),plugin.settings()))return;
        if(e.getBlock().getY()>plugin.getConfig().getInt("arena.max-build-y",85)){
            e.setCancelled(true);plugin.action(e.getPlayer(),"&cBuild height limit reached");return;
        }
        if(original.size()>=plugin.getConfig().getInt("arena.max-tracked-blocks",50000)){
            e.setCancelled(true);plugin.action(e.getPlayer(),"&cArena is at its block-change limit");
        }
    }
    void reset(Player requestedBy){
        if(restoring){if(requestedBy!=null)requestedBy.sendMessage("§cReset already running");return;}
        restoring=true;
        List<Map.Entry<Position,String>> changes=new ArrayList<>(original.entrySet());
        for(Player p:Bukkit.getOnlinePlayers())if(plugin.arena().inside(p))plugin.arena().toSpawn(p);
        new BukkitRunnable(){int index=0;
            public void run(){int limit=0;
                while(index<changes.size()&&limit++<300){
                    var entry=changes.get(index++);Position pos=entry.getKey();World world=Bukkit.getWorld(pos.world());if(world==null)continue;
                    try{world.getBlockAt(pos.x(),pos.y(),pos.z()).setBlockData(Bukkit.createBlockData(entry.getValue()),false);}
                    catch(IllegalArgumentException ex){plugin.getLogger().warning("Invalid stored block data for arena reset: "+ex.getMessage());}
                }
                if(index>=changes.size()){
                    original.clear();restoring=false;
                    resetAt=System.currentTimeMillis()+Math.max(60,plugin.getConfig().getLong("arena.reset-minutes",15)*60)*1000;
                    if(requestedBy!=null)requestedBy.sendMessage("§aArena reset completed");cancel();
                }
            }
        }.runTaskTimer(plugin,1,1);
    }
}
