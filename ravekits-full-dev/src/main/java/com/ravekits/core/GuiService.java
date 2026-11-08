package com.ravekits.core;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

final class GuiService implements Listener {
    static final class Holder implements InventoryHolder {
        final String page;
        final Map<Integer, String> actions=new HashMap<>();
        Inventory inventory;
        Holder(String page){this.page=page;}
        public Inventory getInventory(){return inventory;}
    }
    private final RaveKitsCore plugin;
    GuiService(RaveKitsCore p){plugin=p;}
    private static ItemStack item(Material material,String name,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta m=item.getItemMeta();m.setDisplayName(ChatColor.translateAlternateColorCodes('&',name));
        m.setLore(Arrays.stream(lore).map(x->ChatColor.translateAlternateColorCodes('&',x)).toList());item.setItemMeta(m);return item;
    }
    void open(Player player,String page){
        PlayerStore.Profile profile=plugin.store().get(player.getUniqueId());if(profile==null)return;
        String view=page.toLowerCase(Locale.ROOT);
        Holder h=new Holder(view);
        h.inventory=Bukkit.createInventory(h,54,ChatColor.GOLD+"RaveKits » "+view.toUpperCase(Locale.ROOT));
        switch(view){
            case "main" -> {
                put(h,10,Material.IRON_SWORD,"&6Enter the Arena","play","&7Jump into the pit to fight");
                put(h,12,Material.DIAMOND_HELMET,"&6Kits","kits","&7View owned and locked kits");
                put(h,14,Material.COAL,"&6Ore Exchange","ore","&7Exchange tagged ores for XP");
                put(h,16,Material.EXPERIENCE_BOTTLE,"&6Levels","levels","&7Claim progression rewards");
                put(h,29,Material.CHEST,"&6Crates","crates","&7Buy and open crate keys");
                put(h,31,Material.NAME_TAG,"&6Cosmetics","cosmetics","&7Manage unlocked cosmetics");
                put(h,33,Material.PAPER,"&6Profile","profile","&7View your statistics");
                put(h,40,Material.COMPARATOR,"&6Settings","settings","&7Personal preferences");
            }
            case "play" -> {
                put(h,22,Material.IRON_SWORD,"&6Enter by Jumping into the Pit","spawn","&7The arena activates on pit entry","&7Click to return to spawn");
            }
            case "kits" -> {
                int slot=9;
                for(KitService.Kit k:plugin.kits().all()){
                    if(slot>44)break;
                    boolean own=k.availability().equalsIgnoreCase("free")||profile.ownedKits.contains(k.id());
                    put(h,slot++,own?Material.IRON_SWORD:Material.BARRIER,"&6"+k.name(),"kit:"+k.id(),"&7Rarity: "+k.rarity(),"&7Status: "+(own?"Owned":"Locked"),"&7Weight: "+k.weight());
                }
            }
            case "ore", "mining" -> {
                int coal=plugin.mining().count(player,"coal"),iron=plugin.mining().count(player,"iron"),gold=plugin.mining().count(player,"gold");
                put(h,11,Material.COAL,"&fCoal: &e"+coal,"exchange:coal","&7Click to exchange coal");
                put(h,13,Material.RAW_IRON,"&fRaw Iron: &e"+iron,"exchange:iron","&7Click to exchange iron");
                put(h,15,Material.RAW_GOLD,"&fRaw Gold: &e"+gold,"exchange:gold","&7Click to exchange gold");
                put(h,31,Material.EMERALD,"&aExchange Everything","exchange:all","&7Physically consumes eligible ore items","&7Only works at the safe-zone exchange");
            }
            case "levels" -> {
                put(h,13,Material.EXPERIENCE_BOTTLE,"&6Level "+profile.level,"noop","&7XP: "+profile.xp+" / "+plugin.progress().needed(profile.level),"&7Unclaimed rewards: "+profile.pendingRewards);
                put(h,31,Material.CHEST,"&6Claim Level Rewards","rewards","&7Choose a Kit or Cosmetic crate key");
            }
            case "rewards" -> {
                put(h,20,Material.IRON_SWORD,"&6Kit Crate Key","reward:kit","&7Claim one pending level reward");
                put(h,24,Material.NAME_TAG,"&6Cosmetic Crate Key","reward:cosmetic","&7Claim one pending level reward");
                put(h,31,Material.EXPERIENCE_BOTTLE,"&fUnclaimed: &e"+profile.pendingRewards,"noop","&7Each level awards one choice");
            }
            case "crates" -> {
                put(h,11,Material.CHEST,"&6Kit Keys: &f"+profile.kitKeys,"crate:kit","&7Use a key to unlock a random eligible kit");
                put(h,15,Material.ENDER_CHEST,"&6Cosmetic Keys: &f"+profile.cosmeticKeys,"crate:cosmetic","&7Use a key to unlock a random cosmetic");
                put(h,29,Material.GOLD_INGOT,"&6Buy Kit Key: &f250 Coins","buy:kit","&7Your balance: "+profile.coins);
                put(h,33,Material.GOLD_NUGGET,"&6Buy Cosmetic Key: &f150 Coins","buy:cosmetic","&7Your balance: "+profile.coins);
            }
            case "cosmetics" -> {
                put(h,20,Material.NAME_TAG,"&6Tag: Gold","tag:gold","&7"+(profile.cosmetics.contains("gold")?"Owned":"Locked"),"&7Equip if owned");
                put(h,24,Material.NAME_TAG,"&6Tag: Champion","tag:champion","&7"+(profile.cosmetics.contains("champion")?"Owned":"Locked"),"&7Equip if owned");
                put(h,31,Material.BARRIER,"&6Unequip Tag","tag:off","&7Remove current chat tag");
            }
            case "profile", "stats" -> {
                put(h,11,Material.DIAMOND_SWORD,"&6Kills: &f"+profile.kills,"noop","&7Deaths: "+profile.deaths);
                put(h,13,Material.BLAZE_POWDER,"&6Best Streak: &f"+profile.bestStreak,"noop","&7Current: "+profile.streak);
                put(h,15,Material.GOLD_INGOT,"&6Coins: &f"+profile.coins,"noop","&7Kill players to earn coins");
                put(h,31,Material.EXPERIENCE_BOTTLE,"&6Level: &f"+profile.level,"levels","&7Current XP: "+profile.xp);
            }
            case "settings" -> {
                put(h,22,Material.NOTE_BLOCK,"&6Sounds","noop","&7Uses default vanilla click sounds");
            }
            case "help" -> {
                put(h,11,Material.IRON_SWORD,"&6PvP","play","&7Jump into the pit for a random kit");
                put(h,13,Material.COAL,"&6Mining","ore","&7Mine ores and exchange at spawn");
                put(h,15,Material.EXPERIENCE_BOTTLE,"&6Progression","levels","&7Kills and mining earn XP");
            }
            case "admin" -> {
                if(!player.hasPermission("ravekits.admin")){player.sendMessage("§cNo permission.");return;}
                put(h,22,Material.COMMAND_BLOCK,"&6Admin Commands","noop","&7/rk admin diagnostics","&7/rk admin kit create <id>","&7/rk admin ores scan");
            }
            default -> {
                if(view.startsWith("edit_")){
                    if(!player.hasPermission("ravekits.admin.kit")){player.sendMessage("§cNo permission.");return;}
                    String id=view.substring(5);KitService.Kit kit=plugin.kits().get(id);
                    if(kit==null){player.sendMessage("§cUnknown kit");return;}
                    put(h,10,Material.NAME_TAG,"&6"+kit.name(),"noop","&7ID: "+id);
                    put(h,12,Material.NETHER_STAR,"&6Rarity: "+kit.rarity(),"edit:"+id+":rarity","&7Click to cycle rarity");
                    put(h,14,Material.CHEST,"&6Availability: "+kit.availability(),"edit:"+id+":availability","&7Click to cycle free / crate / paid");
                    put(h,16,Material.LEVER,"&6Enabled: "+kit.enabled(),"edit:"+id+":enabled","&7Click to toggle");
                    put(h,28,Material.EMERALD,"&aIncrease Weight: "+kit.weight(),"edit:"+id+":weight-up");
                    put(h,30,Material.REDSTONE,"&cDecrease Weight: "+kit.weight(),"edit:"+id+":weight-down");
                    put(h,32,Material.ARMOR_STAND,"&6Snapshot Inventory","edit:"+id+":snapshot","&7Save your current equipment as the kit");
                    put(h,34,Material.BARRIER,"&cDelete Kit","edit:"+id+":delete-confirm","&7Requires another confirmation");
                }else if(view.startsWith("delete_")){
                    String id=view.substring(7);
                    if(!player.hasPermission("ravekits.admin.kit")){player.sendMessage("§cNo permission.");return;}
                    put(h,20,Material.EMERALD,"&aCancel","edit_"+id);
                    put(h,24,Material.BARRIER,"&cConfirm Delete","delete-confirm:"+id,"&7Cannot be undone");
                }else{player.sendMessage("§cUnknown menu.");return;}
            }
        }
        if(!view.equals("main"))put(h,49,Material.ARROW,"&fBack to Main Menu","main","&7Return to the hub");
        player.openInventory(h.inventory);
        player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,0.4f,1f);
    }
    private void put(Holder holder,int slot,Material icon,String name,String action,String...lore){
        holder.inventory.setItem(slot,item(icon,name,lore));holder.actions.put(slot,action);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder h))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p) || event.getRawSlot()<0 || event.getRawSlot()>=h.inventory.getSize())return;
        String action=h.actions.get(event.getRawSlot());if(action==null||action.equals("noop"))return;
        Bukkit.getScheduler().runTask(plugin,()->handle(p,action));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);
    }
    private void handle(Player p,String action){
        if(!p.isOnline())return;
        if(action.startsWith("edit:")){
            if(!p.hasPermission("ravekits.admin.kit"))return;
            String[] bits=action.split(":",3);if(bits.length!=3)return;
            if(bits[2].equals("delete-confirm")){open(p,"delete_"+bits[1]);return;}
            plugin.kits().edit(bits[1],bits[2],p);open(p,"edit_"+bits[1]);return;
        }
        if(action.startsWith("delete-confirm:")){
            if(!p.hasPermission("ravekits.admin.kit"))return;
            plugin.kits().delete(action.substring("delete-confirm:".length()));open(p,"admin");return;
        }
        if(action.startsWith("exchange:")){plugin.mining().exchange(p,action.substring(9));open(p,"ore");return;}
        if(action.startsWith("reward:")){
            PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());if(profile.pendingRewards<1){plugin.action(p,"&cNo pending rewards");return;}
            profile.pendingRewards--;if(action.equals("reward:kit"))profile.kitKeys++;else profile.cosmeticKeys++;
            plugin.action(p,"&aReward key claimed");open(p,"rewards");return;
        }
        if(action.startsWith("buy:")){
            String type=action.substring(4);long cost=type.equals("kit")?250:150;
            if(!plugin.progress().purchase(p,cost)){plugin.action(p,"&cNot enough coins");return;}
            PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());if(type.equals("kit"))profile.kitKeys++;else profile.cosmeticKeys++;
            plugin.action(p,"&aPurchased "+type+" crate key");open(p,"crates");return;
        }
        if(action.startsWith("crate:")){
            PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());String type=action.substring(6);
            if(type.equals("kit")){
                if(profile.kitKeys<1){plugin.action(p,"&cNo Kit Keys");return;}
                profile.kitKeys--;
                List<KitService.Kit> pool=plugin.kits().all().stream().filter(k->k.enabled()&&k.availability().equalsIgnoreCase("crate")&&!profile.ownedKits.contains(k.id())).toList();
                if(pool.isEmpty()) {profile.kitKeys++;plugin.action(p,"&cNo eligible kit rewards; key kept");return;}
                KitService.Kit kit=pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));profile.ownedKits.add(kit.id());p.sendTitle("§6KIT UNLOCKED","§f"+kit.name(),10,50,10);
            }else{
                if(profile.cosmeticKeys<1){plugin.action(p,"&cNo Cosmetic Keys");return;}
                profile.cosmeticKeys--;
                List<String> pool=List.of("gold","champion");String cosmetic=pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
                profile.cosmetics.add(cosmetic);p.sendTitle("§6COSMETIC CRATE","§f"+cosmetic,10,50,10);
            }
            open(p,"crates");return;
        }
        if(action.startsWith("tag:")){
            PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());String tag=action.substring(4);
            if(tag.equals("off")){profile.equippedTag="";}else if(profile.cosmetics.contains(tag)){profile.equippedTag=tag;}else{plugin.action(p,"&cTag locked");return;}
            open(p,"cosmetics");return;
        }
        if(action.startsWith("kit:")){
            KitService.Kit kit=plugin.kits().get(action.substring(4));if(kit!=null)plugin.action(p,"&6"+kit.name()+" &7("+kit.rarity()+")");return;
        }
        if(action.equals("spawn")){if(plugin.arena().tagged(p)){plugin.action(p,"&cYou are combat-tagged");return;}plugin.arena().toSpawn(p);p.closeInventory();return;}
        open(p,action);
    }
}
