package com.ravekits.core;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

final class KitService {
    record Kit(String id, String name, String rarity, String availability, boolean enabled, int weight, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {}
    private final RaveKitsCore plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Random random = new Random();
    KitService(RaveKitsCore p) { plugin = p; }
    void load() {
        File dir = new File(plugin.getDataFolder(), "kits"); dir.mkdirs();
        if (Objects.requireNonNull(dir.listFiles((d,n)->n.endsWith(".yml"))).length==0) {
            for (int i=1;i<=3;i++) starter(i);
        }
        Map<String, Kit> next = new LinkedHashMap<>();
        for(File file:Objects.requireNonNull(dir.listFiles((d,n)->n.endsWith(".yml")))) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().substring(0,file.getName().length()-4);
            @SuppressWarnings("unchecked") List<ItemStack> content=(List<ItemStack>)(List<?>)y.getList("inventory",List.of());
            @SuppressWarnings("unchecked") List<ItemStack> armour=(List<ItemStack>)(List<?>)y.getList("armour",List.of());
            if(content.size()!=36 || armour.size()!=4) throw new IllegalArgumentException("kits/"+file.getName()+": inventory must have 36 slots and armour 4");
            Kit kit = new Kit(id, y.getString("display-name",id),y.getString("rarity","COMMON"),y.getString("availability","free"),y.getBoolean("enabled",true),Math.max(1,y.getInt("weight",100)),content.toArray(new ItemStack[0]),armour.toArray(new ItemStack[0]),y.getItemStack("offhand"));
            next.put(id,kit);
        }
        if(next.isEmpty())throw new IllegalArgumentException("No kits configured");
        kits.clear();kits.putAll(next);
    }
    private void starter(int i) {
        ItemStack[] inv = new ItemStack[36], arm=new ItemStack[4];
        inv[0]=new ItemStack(i==3?Material.DIAMOND_SWORD:Material.IRON_SWORD);
        inv[1]=new ItemStack(Material.IRON_PICKAXE);
        inv[2]=new ItemStack(Material.COOKED_BEEF,32);
        inv[3]=new ItemStack(Material.COBBLESTONE,64);
        inv[4]=new ItemStack(Material.OAK_PLANKS,64);
        arm[0]=new ItemStack(Material.IRON_BOOTS);arm[1]=new ItemStack(Material.IRON_LEGGINGS);
        arm[2]=new ItemStack(Material.IRON_CHESTPLATE);arm[3]=new ItemStack(Material.IRON_HELMET);
        write("starter"+i,new Kit("starter"+i,"Starter "+i,"COMMON","free",true,100,inv,arm,null));
    }
    private void write(String id, Kit kit) {
        File f=new File(plugin.getDataFolder(),"kits/"+id+".yml");YamlConfiguration y=new YamlConfiguration();
        y.set("display-name",kit.name());y.set("rarity",kit.rarity());y.set("availability",kit.availability());y.set("enabled",kit.enabled());y.set("weight",kit.weight());
        y.set("inventory",Arrays.asList(kit.contents()));y.set("armour",Arrays.asList(kit.armor()));y.set("offhand",kit.offhand());
        try{y.save(f);}catch(IOException e){throw new IllegalStateException("Unable to save kit: "+id,e);}
    }
    boolean create(Player admin,String id){
        if(!id.matches("[a-z0-9_-]{2,32}") || kits.containsKey(id))return false;
        Kit kit=new Kit(id,id,"COMMON","crate",true,100,admin.getInventory().getStorageContents().clone(),admin.getInventory().getArmorContents().clone(),admin.getInventory().getItemInOffHand().clone());
        write(id,kit);load();return true;
    }
    boolean edit(String id,String action,Player editor){
        Kit old=kits.get(id);if(old==null)return false;
        String rarity=old.rarity(), availability=old.availability();
        int weight=old.weight();boolean enabled=old.enabled();
        ItemStack[] inventory=old.contents(),armor=old.armor();ItemStack offhand=old.offhand();
        switch(action){
            case "rarity" -> {
                List<String> names=List.of("COMMON","UNCOMMON","RARE","LEGENDARY","MYTHICAL","CHROMATIC","GODLY");
                int at=names.indexOf(rarity.toUpperCase(Locale.ROOT));rarity=names.get((at+1+names.size())%names.size());
            }
            case "availability" -> {availability=switch(availability){case "free" -> "crate";case "crate" -> "paid";default -> "free";};}
            case "weight-up" -> weight=Math.min(1000,weight+10);
            case "weight-down" -> weight=Math.max(1,weight-10);
            case "enabled" -> enabled=!enabled;
            case "snapshot" -> {inventory=editor.getInventory().getStorageContents().clone();armor=editor.getInventory().getArmorContents().clone();offhand=editor.getInventory().getItemInOffHand().clone();}
            default -> {return false;}
        }
        write(id,new Kit(id,old.name(),rarity,availability,enabled,weight,inventory,armor,offhand));load();return true;
    }
    boolean delete(String id) {
        if(!kits.containsKey(id))return false;
        File f=new File(plugin.getDataFolder(),"kits/"+id+".yml");if(!f.delete())return false;load();return true;
    }
    Kit choose(PlayerStore.Profile profile) {
        List<Kit> eligible=new ArrayList<>();long total=0;
        for(Kit k:kits.values())if(k.enabled() && (k.availability().equalsIgnoreCase("free")||profile.ownedKits.contains(k.id()))){eligible.add(k);total+=k.weight();}
        if(total==0)return null;
        long pick=java.util.concurrent.ThreadLocalRandom.current().nextLong(total);
        for(Kit k:eligible){pick-=k.weight();if(pick<0)return k;}
        return eligible.getLast();
    }
    void equip(Player p){
        PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());if(profile==null)return;
        Kit kit=choose(profile);if(kit==null){p.sendMessage(ChatColor.RED+"No accessible kits. Ask an admin.");return;}
        List<ItemStack> ores=new ArrayList<>();
        for(ItemStack old:p.getInventory().getStorageContents())if(plugin.mining().isTagged(old))ores.add(old.clone());
        p.getInventory().clear();p.getInventory().setStorageContents(copy(kit.contents()));
        // Preserve physical ore across a fresh kit; replace non-critical kit slots as necessary.
        for(ItemStack ore:ores){
            int slot=p.getInventory().firstEmpty();
            if(slot<0||slot>=36){for(int j=35;j>=0;j--){if(!plugin.mining().isTagged(p.getInventory().getItem(j))){slot=j;break;}}}
            if(slot>=0&&slot<36)p.getInventory().setItem(slot,ore);
        }p.getInventory().setArmorContents(copy(kit.armor()));
        if(kit.offhand()!=null)p.getInventory().setItemInOffHand(kit.offhand().clone());
        profile.activeKit=kit.id();plugin.action(p,"&6Kit: &f"+kit.name());
    }
    private ItemStack[] copy(ItemStack[] a){ItemStack[] b=a.clone();for(int i=0;i<b.length;i++)if(b[i]!=null)b[i]=b[i].clone();return b;}
    Collection<Kit> all(){return Collections.unmodifiableCollection(kits.values());}
    Kit get(String id){return kits.get(id);}
}
