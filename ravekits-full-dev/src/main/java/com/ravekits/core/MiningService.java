package com.ravekits.core;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.util.*;

final class MiningService implements Listener {
    private final RaveKitsCore plugin;
    private final NamespacedKey marker;
    private final Set<String> naturals=new HashSet<>();
    private final Set<String> mined=new HashSet<>();
    private final Map<UUID,Integer> collected=new HashMap<>();
    MiningService(RaveKitsCore p){plugin=p;marker=new NamespacedKey(p,"mined_ore");load();}
    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private static String type(Material m){return switch(m){
        case COAL_ORE,DEEPSLATE_COAL_ORE -> "coal";
        case IRON_ORE,DEEPSLATE_IRON_ORE -> "iron";
        case GOLD_ORE,DEEPSLATE_GOLD_ORE -> "gold";
        default -> null;
    };}
    private Material material(String type){return switch(type){case "coal" -> Material.COAL;case "iron" -> Material.RAW_IRON;default -> Material.RAW_GOLD;};}
    private void load(){
        File file=new File(plugin.getDataFolder(),"natural-ores.yml");if(!file.isFile())return;
        naturals.addAll(YamlConfiguration.loadConfiguration(file).getStringList("positions"));
    }
    private void save(){
        YamlConfiguration config=new YamlConfiguration();config.set("positions",new ArrayList<>(naturals));
        try {config.save(new File(plugin.getDataFolder(),"natural-ores.yml"));}
        catch(IOException e){plugin.getLogger().severe("Unable to save natural ore positions: "+e.getMessage());}
    }
    void scan(Player admin){
        World w=Bukkit.getWorld(plugin.settings().world());if(w==null){admin.sendMessage("§cWorld missing");return;}
        var manager=com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(w));
        var region=manager==null?null:manager.getRegion(plugin.settings().pitRegion());
        if(region==null){admin.sendMessage("§cPit region missing");return;}
        int minX=region.getMinimumPoint().x(),maxX=region.getMaximumPoint().x();
        int minY=Math.max(w.getMinHeight(),region.getMinimumPoint().y()),maxY=Math.min(w.getMaxHeight()-1,region.getMaximumPoint().y());
        int minZ=region.getMinimumPoint().z(),maxZ=region.getMaximumPoint().z();
        long volume=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        if(volume>5_000_000L){admin.sendMessage("§cScan area too large (limit 5 million blocks)");return;}
        admin.sendMessage("§eScanning pit ores in small batches. Keep area loaded.");
        Set<String> found=new HashSet<>();
        new org.bukkit.scheduler.BukkitRunnable(){long cursor=0;
            @Override public void run(){int examined=0;int xs=maxX-minX+1,zs=maxZ-minZ+1;
                while(cursor<volume&&examined++<2500){
                    long i=cursor++;int x=minX+(int)(i%xs), z=minZ+(int)((i/xs)%zs), y=minY+(int)(i/(xs*(long)zs));
                    Block b=w.getBlockAt(x,y,z);
                    if(type(b.getType())!=null && plugin.regions().pit(b.getLocation(),plugin.settings()))found.add(key(b));
                }
                if(cursor>=volume){naturals.clear();naturals.addAll(found);mined.clear();save();admin.sendMessage("§aNatural ore scan complete: "+found.size()+" blocks.");cancel();}
            }
        }.runTaskTimer(plugin,1L,1L);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakOre(BlockBreakEvent e){
        Block b=e.getBlock();String ore=type(b.getType());if(ore==null)return;
        if(!b.getWorld().getName().equals(plugin.settings().world()) || !plugin.regions().pit(b.getLocation(),plugin.settings()))return;
        String pos=key(b);
        if(!naturals.contains(pos) || mined.contains(pos)){e.setDropItems(false);return;}
        mined.add(pos);e.setDropItems(false);e.setExpToDrop(0);
        ItemStack drop=new ItemStack(material(ore));ItemMeta meta=drop.getItemMeta();
        meta.getPersistentDataContainer().set(marker,PersistentDataType.STRING,ore);meta.setDisplayName("§6Mining Ore §7("+ore+")");drop.setItemMeta(meta);
        b.getWorld().dropItemNaturally(b.getLocation(),drop);
        Material original=b.getType();
        long delay=Math.max(20L,plugin.getConfig().getLong("mining.ore-respawn-seconds",90)*20L);
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            mined.remove(pos);
            if(plugin.regions().pit(b.getLocation(),plugin.settings()))b.setType(original,false);
        },delay);
    }
    private String tagged(ItemStack i){
        if(i==null || !i.hasItemMeta())return null;
        return i.getItemMeta().getPersistentDataContainer().get(marker,PersistentDataType.STRING);
    }
    boolean isTagged(ItemStack item){return tagged(item)!=null;}
    int count(Player p,String ore){int total=0;
        for(ItemStack i:p.getInventory().getStorageContents())if(ore.equals(tagged(i)))total+=i.getAmount();
        return total;
    }
    void exchange(Player p,String ore){
        if(!plugin.regions().safe(p.getLocation(),plugin.settings())){plugin.action(p,"&cReturn to safe zone first");return;}
        int units=0;long xp=0;
        for(int slot=0;slot<36;slot++){
            ItemStack i=p.getInventory().getItem(slot);String kind=tagged(i);
            if(kind==null||(!ore.equals("all")&&!ore.equals(kind)))continue;
            int amount=i.getAmount();units+=amount;
            int min=plugin.getConfig().getInt("mining.xp-per-ore.min",3),max=plugin.getConfig().getInt("mining.xp-per-ore.max",7);
            for(int j=0;j<amount;j++)xp+=java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(0,min),Math.max(min,max)+1);
            p.getInventory().setItem(slot,null);
        }
        if(units==0){plugin.action(p,"&cNo eligible ore to exchange");return;}
        plugin.progress().grant(p,xp,"Exchanged "+units+" ores");
    }
    void clear(Player p){
        for(int i=0;i<36;i++)if(tagged(p.getInventory().getItem(i))!=null)p.getInventory().setItem(i,null);
        if(tagged(p.getInventory().getItemInOffHand())!=null)p.getInventory().setItemInOffHand(null);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(PlayerAttemptPickupItemEvent e){
        String ore=tagged(e.getItem().getItemStack());if(ore==null)return;
        int before=e.getItem().getItemStack().getAmount();
        Bukkit.getScheduler().runTask(plugin,()->{
            int remaining=e.getItem().isValid()?e.getItem().getItemStack().getAmount():0;
            int added=Math.max(0,before-remaining);if(added>0){
                UUID id=e.getPlayer().getUniqueId();int total=collected.merge(id,added,Integer::sum);
                plugin.action(e.getPlayer(),"&a+"+added+" "+ore+" &7| &f"+total+" ores collected");
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){
        if(tagged(e.getItemDrop().getItemStack())!=null){e.setCancelled(true);plugin.action(e.getPlayer(),"&cMining ores cannot be dropped");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player) || e.getView().getTopInventory().getType()==InventoryType.CRAFTING)return;
        if(tagged(e.getCurrentItem())!=null || tagged(e.getCursor())!=null)e.setCancelled(true);
        if(e.getAction()==InventoryAction.MOVE_TO_OTHER_INVENTORY && tagged(e.getCurrentItem())!=null)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getType()!=InventoryType.CRAFTING && tagged(e.getOldCursor())!=null)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void die(PlayerDeathEvent e){
        e.getDrops().removeIf(i->tagged(i)!=null);collected.remove(e.getEntity().getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void quit(PlayerQuitEvent e){clear(e.getPlayer());collected.remove(e.getPlayer().getUniqueId());}
}
