package com.ravekits.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

final class RaveKitsPlaceholders extends PlaceholderExpansion {
    private final RaveKitsCore plugin;
    RaveKitsPlaceholders(RaveKitsCore p){plugin=p;}
    @Override public @NotNull String getIdentifier(){return "ravekits";}
    @Override public @NotNull String getAuthor(){return "RaveKits";}
    @Override public @NotNull String getVersion(){return plugin.getPluginMeta().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public String onPlaceholderRequest(Player player,@NotNull String key){
        if(player==null)return "";
        PlayerStore.Profile p=plugin.store().get(player.getUniqueId());if(p==null)return "";
        return switch(key.toLowerCase(Locale.ROOT)){
            case "level" -> Integer.toString(p.level);
            case "xp" -> Long.toString(p.xp);
            case "xp_required" -> Long.toString(plugin.progress().needed(p.level));
            case "coins" -> Long.toString(p.coins);
            case "kills" -> Integer.toString(p.kills);
            case "deaths" -> Integer.toString(p.deaths);
            case "kd" -> p.deaths==0?Integer.toString(p.kills):String.format(Locale.ROOT,"%.2f",p.kills/(double)p.deaths);
            case "killstreak" -> Integer.toString(p.streak);
            case "best_killstreak" -> Integer.toString(p.bestStreak);
            case "pending_rewards" -> Integer.toString(p.pendingRewards);
            case "ore_coal" -> Integer.toString(plugin.mining().count(player,"coal"));
            case "ore_iron" -> Integer.toString(plugin.mining().count(player,"iron"));
            case "ore_gold" -> Integer.toString(plugin.mining().count(player,"gold"));
            case "tag" -> p.equippedTag.isBlank()?"":"["+p.equippedTag+"]";
            case "kit" -> p.activeKit;
            case "in_arena" -> Boolean.toString(plugin.arena().inside(player));
            case "in_safezone" -> Boolean.toString(plugin.regions().safe(player.getLocation(),plugin.settings()));
            default -> null;
        };
    }
}
