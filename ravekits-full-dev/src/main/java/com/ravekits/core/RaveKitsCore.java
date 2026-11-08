package com.ravekits.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.logging.Level;

public final class RaveKitsCore extends JavaPlugin {
    private ConfigService config;
    private RegionService regions;
    private PlayerStore store;
    private ArenaListener arena;
    private KitService kits;
    private MiningService mining;
    private ProgressionService progression;
    private GuiService gui;
    private ArenaResetService reset;
    private int autosaveTask=-1;
    @Override public void onEnable(){
        try{
            getDataFolder().mkdirs();
            saveDefaultConfig();
            regions=new RegionService();config=new ConfigService(this);
            ConfigService.Settings s=config.load(true);
            store=new PlayerStore(this);store.open(s.sqliteFile());
            kits=new KitService(this);kits.load();
            progression=new ProgressionService(this);
            mining=new MiningService(this);
            gui=new GuiService(this);
            arena=new ArenaListener(this);
            reset=new ArenaResetService(this);
            Bukkit.getPluginManager().registerEvents(arena,this);
            Bukkit.getPluginManager().registerEvents(mining,this);
            Bukkit.getPluginManager().registerEvents(gui,this);
            Bukkit.getPluginManager().registerEvents(reset,this);
            Bukkit.getPluginManager().registerEvents(new LobbyListener(this),this);
            if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))new RaveKitsPlaceholders(this).register();
            CoreCommands cmds=new CoreCommands(this);
            PluginCommand rk=getCommand("rk"),spawn=getCommand("spawn"),help=getCommand("help");
            if(rk==null||spawn==null)throw new IllegalStateException("Missing commands from plugin.yml");
            rk.setExecutor(cmds);rk.setTabCompleter(cmds);spawn.setExecutor(cmds);if(help!=null)help.setExecutor(cmds);
            autosaveTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->store.saveAll(),s.autosaveTicks(),s.autosaveTicks());
            getLogger().info("Development build enabled: kits, menus, mining, progression. NOT production verified.");
        }catch(Exception ex){getLogger().log(Level.SEVERE,"Failed to initialise RaveKits Core",ex);Bukkit.getPluginManager().disablePlugin(this);}
    }
    @Override public void onDisable(){
        if(autosaveTask!=-1) Bukkit.getScheduler().cancelTask(autosaveTask);
        if(store!=null)store.close();
    }
    ConfigService configService(){return config;}
    ConfigService.Settings settings(){return config.get();}
    RegionService regions(){return regions;}
    PlayerStore store(){return store;}
    ArenaListener arena(){return arena;}
    KitService kits(){return kits;}
    MiningService mining(){return mining;}
    ProgressionService progress(){return progression;}
    GuiService gui(){return gui;}
    ArenaResetService reset(){return reset;}
    void lobby(Player p){
        java.util.List<org.bukkit.inventory.ItemStack> ores=new java.util.ArrayList<>();
        for(var stack:p.getInventory().getStorageContents())if(mining.isTagged(stack))ores.add(stack.clone());
        p.getInventory().clear();
        int index=0;
        for(var ore:ores){if(index<36)p.getInventory().setItem(index++,ore);}
        addLobbyItem(p,org.bukkit.Material.COMPASS,"§6RaveKits Menu",0);
        addLobbyItem(p,org.bukkit.Material.IRON_SWORD,"§6Play",4);
        addLobbyItem(p,org.bukkit.Material.PLAYER_HEAD,"§6Profile",8);
    }
    private void addLobbyItem(Player p,org.bukkit.Material material,String name,int preferred){
        int slot=preferred;
        if(p.getInventory().getItem(slot)!=null){slot=p.getInventory().firstEmpty();}
        if(slot<0 || slot>=36)return; // Never destroy a player's ore to create a menu button.
        var item=new org.bukkit.inventory.ItemStack(material);
        var meta=item.getItemMeta();meta.setDisplayName(name);item.setItemMeta(meta);
        p.getInventory().setItem(slot,item);
    }
    void action(Player player,String message){player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(message));}
}
