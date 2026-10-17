package me.pintoadmin.pintoBridge;

import static org.bukkit.Bukkit.getServer;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class WorldChangeListener implements Listener {
    private final PintoBridge pintoBridge;

    public WorldChangeListener(PintoBridge pintoBridge) {
        this.pintoBridge = pintoBridge;
        getServer().getPluginManager().registerEvents(this, pintoBridge);
    }

    @EventHandler
    public void onPlayerChangeWorldEvent(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();
        World fromWorld = event.getFrom();

        BridgeGame game = pintoBridge.getGame(toWorld.getName());
        BridgeGame fromGame = pintoBridge.getGame(fromWorld.getName());

        if (fromGame != null && fromGame != game) {
            fromGame.leave(player, toWorld);
        }
        if (game == null) return;
        // Delegate to Game.join for all join logic
        game.join(player);
    }

    @EventHandler
    public void onPlayerEnterPortal(PlayerPortalEvent event) {
        if (pintoBridge.getGame(event.getPlayer().getWorld().getName()) == null)
            return; // Not a playing game, don't interfere
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(
                    true); // temporary until scoring established. TODO: replace with scoring logic
        }
    }
}
