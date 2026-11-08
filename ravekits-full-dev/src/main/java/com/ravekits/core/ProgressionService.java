package com.ravekits.core;

import org.bukkit.entity.Player;

final class ProgressionService {
    private final RaveKitsCore plugin;
    ProgressionService(RaveKitsCore p){plugin=p;}
    long needed(int level){return Math.max(1L, 80L+40L*level+8L*level*level);}
    void grant(Player player,long amount,String reason){
        PlayerStore.Profile p=plugin.store().get(player.getUniqueId());if(p==null || amount<=0)return;
        p.xp=Math.min(Long.MAX_VALUE-amount,p.xp)+amount;
        int cap=plugin.getConfig().getInt("progression.max-level",100);
        while(p.level<cap && p.xp>=needed(p.level)){
            p.xp-=needed(p.level);p.level++;p.pendingRewards++;
            player.sendTitle("§6LEVEL UP!","§fLevel "+p.level+" §7| §eClaim your crate",10,60,20);
        }
        plugin.action(player,"&a+"+amount+" XP &7("+reason+")");
    }
    void kill(Player killer, Player victim){
        PlayerStore.Profile p=plugin.store().get(killer.getUniqueId());if(p==null)return;
        long xp=plugin.getConfig().getLong("combat.kill-xp",20);
        long coins=plugin.getConfig().getLong("combat.kill-coins",10);
        for(int s:new int[]{3,5,10,15,25})if(p.streak==s){xp+=s*5L;coins+=s*2L;}
        p.coins+=coins;grant(killer,xp,"Kill");
    }
    boolean purchase(Player p,long coins){
        PlayerStore.Profile profile=plugin.store().get(p.getUniqueId());
        if(profile==null||coins<0||profile.coins<coins)return false;
        profile.coins-=coins;return true;
    }
}
