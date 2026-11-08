package com.ravekits.core;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

final class CoreCommands implements CommandExecutor,TabCompleter {
    private final RaveKitsCore plugin;
    CoreCommands(RaveKitsCore p){plugin=p;}
    private void reply(CommandSender s,String m){s.sendMessage("§6RaveKits §8» §f"+m);}
    private boolean admin(CommandSender s,String node){if(!s.hasPermission("ravekits.admin."+node)){reply(s,"§cNo permission");return false;}return true;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] a){
        String root=command.getName().toLowerCase(Locale.ROOT);
        if(root.equals("spawn")){
            if(!(sender instanceof Player p)){reply(sender,"Players only");return true;}
            if(plugin.arena().tagged(p)){plugin.action(p,plugin.settings().blockedMessage());return true;}
            plugin.arena().toSpawn(p);return true;
        }
        if(root.equals("help")){if(sender instanceof Player p)plugin.gui().open(p,"help");else reply(sender,"/rk admin diagnostics");return true;}
        if(a.length==0){if(sender instanceof Player p)plugin.gui().open(p,"main");else reply(sender,"/rk admin diagnostics");return true;}
        if(a[0].equalsIgnoreCase("menu")){
            if(!(sender instanceof Player p)){reply(sender,"Players only");return true;}
            plugin.gui().open(p,a.length>=2?a[1]:"main");return true;
        }
        if(!a[0].equalsIgnoreCase("admin")){reply(sender,"Use /rk to open the main menu");return true;}
        if(a.length<2){reply(sender,"Admin: reload, diagnostics, kit, coins, xp, ores");return true;}
        String sub=a[1].toLowerCase(Locale.ROOT);
        try {
            switch(sub){
                case "reload" -> {
                    if(!admin(sender,"reload"))return true;
                    plugin.configService().load(false);plugin.kits().load();reply(sender,"Configuration and kits loaded");
                }
                case "diagnostics" -> {
                    if(!admin(sender,"diagnostics"))return true;
                    reply(sender,"Paper: "+Bukkit.getVersion());
                    reply(sender,"World: "+plugin.settings().world());
                    reply(sender,"WorldGuard: "+Bukkit.getPluginManager().isPluginEnabled("WorldGuard"));
                    reply(sender,"Kits configured: "+plugin.kits().all().size());
                    reply(sender,"SQLite: connected");
                }
                case "kit" -> {
                    if(!admin(sender,"kit"))return true;
                    if(a.length<4){reply(sender,"/rk admin kit create|delete|give|take ...");return true;}
                    String action=a[2].toLowerCase(Locale.ROOT);
                    switch(action){
                        case "edit" -> {
                            if(!(sender instanceof Player p)){reply(sender,"Use in-game");break;}
                            plugin.gui().open(p,"edit_"+a[3]);
                        }
                        case "create" -> {
                            if(!(sender instanceof Player p)){reply(sender,"Use in-game");break;}
                            reply(sender,plugin.kits().create(p,a[3])?"Created kit "+a[3]:"Invalid or duplicate kit ID");
                        }
                        case "delete" -> reply(sender,plugin.kits().delete(a[3])?"Deleted kit":"Unknown kit");
                        case "give","take" -> {
                            if(a.length<5){reply(sender,"/rk admin kit "+action+" <online-player> <kit>");break;}
                            Player target=Bukkit.getPlayerExact(a[3]);
                            if(target==null){reply(sender,"Player must be online");break;}
                            var profile=plugin.store().get(target.getUniqueId());
                            if(plugin.kits().get(a[4])==null){reply(sender,"Unknown kit");break;}
                            if(action.equals("give"))profile.ownedKits.add(a[4]);else profile.ownedKits.remove(a[4]);
                            reply(sender,"Updated kit ownership for "+target.getName());
                        }
                        default -> reply(sender,"Unknown kit action");
                    }
                }
                case "arena" -> {
                    if(!admin(sender,"arena"))return true;
                    if(a.length<3){reply(sender,"/rk admin arena save|reset");return true;}
                    if(a[2].equalsIgnoreCase("save")){plugin.reset().saveBaseline();reply(sender,"Tracked changes cleared; current terrain is new baseline for subsequent changes");}
                    else if(a[2].equalsIgnoreCase("reset")){plugin.reset().reset(sender instanceof Player p?p:null);reply(sender,"Arena reset started");}
                    else reply(sender,"Unknown arena command");
                }
                case "ores" -> {
                    if(!admin(sender,"ores"))return true;
                    if(a.length>=3&&a[2].equalsIgnoreCase("scan")&&sender instanceof Player p)plugin.mining().scan(p);
                    else reply(sender,"Use /rk admin ores scan in-game");
                }
                case "coins","xp" -> {
                    if(!admin(sender,sub))return true;
                    if(a.length<5){reply(sender,"/rk admin "+sub+" give|take|set <online-player> <amount>");return true;}
                    Player target=Bukkit.getPlayerExact(a[3]);if(target==null){reply(sender,"Player must be online");return true;}
                    long n=Long.parseLong(a[4]);if(n<0||n>10_000_000L){reply(sender,"Amount must be 0 to 10000000");return true;}
                    var profile=plugin.store().get(target.getUniqueId());
                    String action=a[2].toLowerCase(Locale.ROOT);
                    if(sub.equals("coins")){
                        profile.coins=switch(action){case "give" -> Math.addExact(profile.coins,n);case "take" -> Math.max(0,profile.coins-n);case "set" -> n;default -> throw new IllegalArgumentException("Use give/take/set");};
                    }else{
                        if(action.equals("give"))plugin.progress().grant(target,n,"Admin");
                        else if(action.equals("set"))profile.xp=n;
                        else if(action.equals("take"))profile.xp=Math.max(0,profile.xp-n);
                        else throw new IllegalArgumentException("Use give/take/set");
                    }
                    reply(sender,"Updated "+sub+" for "+target.getName());
                }
                default -> reply(sender,"Unknown command");
            }
        }catch(Exception ex){reply(sender,"§c"+ex.getMessage());}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a){
        if(a.length==1)return List.of("menu","admin");
        if(a.length==2&&a[0].equalsIgnoreCase("menu"))return List.of("main","play","kits","ore","levels","rewards","crates","cosmetics","profile","stats","settings","help","admin");
        if(a.length==2&&a[0].equalsIgnoreCase("admin")&&s.hasPermission("ravekits.admin"))return List.of("reload","diagnostics","kit","ores","arena","coins","xp");
        if(a.length==3&&a[1].equalsIgnoreCase("kit"))return List.of("create","edit","delete","give","take");
        return List.of();
    }
}
