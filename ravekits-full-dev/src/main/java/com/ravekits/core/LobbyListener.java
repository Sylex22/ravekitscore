package com.ravekits.core;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

final class LobbyListener implements Listener {
    private final RaveKitsCore plugin;
    LobbyListener(RaveKitsCore p){plugin=p;}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){
        Player player=e.getPlayer();
        if(plugin.arena().inside(player) || e.getItem()==null || !(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK))return;
        String name=e.getItem().hasItemMeta()?e.getItem().getItemMeta().getDisplayName():"";
        switch(name){
            case "§6RaveKits Menu" -> {e.setCancelled(true);plugin.gui().open(player,"main");}
            case "§6Play" -> {e.setCancelled(true);plugin.gui().open(player,"play");}
            case "§6Profile" -> {e.setCancelled(true);plugin.gui().open(player,"profile");}
        }
    }
}
