package me.pintoadmin.pintoBridge;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;

public class BlockPlaceListener implements Listener {
    private final PintoBridge pintoBridge;

    public BlockPlaceListener(PintoBridge pintoBridge) {
        this.pintoBridge = pintoBridge;
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BridgeGame game = pintoBridge.getGame(block.getWorld().getName());

        if (game == null) return;

        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (game.playerStates
                .getOrDefault(player.getUniqueId(), new ArrayList<>())
                .contains(BridgeGame.PlayerState.NOPLACE)) {
            event.setCancelled(true);
            return;
        }

        BlockState bs = block.getState();
        bs.setType(Material.AIR);
        game.addBlockChange(bs);
    }
}
