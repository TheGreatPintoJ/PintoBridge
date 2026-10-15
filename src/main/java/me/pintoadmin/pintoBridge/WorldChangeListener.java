package me.pintoadmin.pintoBridge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class WorldChangeListener implements Listener {
    private final PintoBridge pintoBridge;

    public WorldChangeListener(PintoBridge pintoBridge) {
        this.pintoBridge = pintoBridge;
    }

    @EventHandler
    public void onPlayerChangeWorldEvent(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();
        World fromWorld = event.getFrom();

        BridgeGame game = pintoBridge.getGame(toWorld.getName());
        BridgeGame fromGame = pintoBridge.getGame(fromWorld.getName());

        List<String> teams = (game != null) ? game.getTeams() : Collections.emptyList();
        List<String> fromTeams = (fromGame != null) ? fromGame.getTeams() : Collections.emptyList();

        if (fromGame != null && fromGame != game) {
            fromGame.leave(player);
        }
        if (game == null) return;
        // Delegate to Game.join for all join logic
        game.join(player);
    }
}
