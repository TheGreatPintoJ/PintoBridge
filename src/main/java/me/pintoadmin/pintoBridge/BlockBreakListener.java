package me.pintoadmin.pintoBridge;

import static org.bukkit.Bukkit.getServer;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;

public class BlockBreakListener implements Listener {
    private final PintoBridge pintoBridge;

    public BlockBreakListener(PintoBridge pintoBridge) {
        this.pintoBridge = pintoBridge;
        getServer().getPluginManager().registerEvents(this, pintoBridge);
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        Player player = event.getPlayer();
        Block block = event.getBlock();

        BridgeGame game = pintoBridge.getGame(worldName);
        if (game == null) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (game.playerStates
                .getOrDefault(player.getUniqueId(), new ArrayList<>())
                .contains(BridgeGame.PlayerState.NOBREAK)) {
            event.setCancelled(true);
            return;
        }
        if (game.playerStates
                .getOrDefault(player.getUniqueId(), new ArrayList<>())
                .contains(BridgeGame.PlayerState.NOBREAKRES)) {
            if (!game.getBreakables().contains(block.getType())) {
                event.setCancelled(true);
                return;
            }
        }
        game.addBlockChange(block.getState());
    }
}
