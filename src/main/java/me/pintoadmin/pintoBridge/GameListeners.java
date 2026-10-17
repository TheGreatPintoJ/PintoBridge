package me.pintoadmin.pintoBridge;

import static org.bukkit.Bukkit.getServer;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.*;
import org.bukkit.scoreboard.*;

public class GameListeners implements Listener {
    private final PintoBridge pintoBridge;

    public GameListeners(PintoBridge pintoBridge) {
        this.pintoBridge = pintoBridge;
        getServer().getPluginManager().registerEvents(this, pintoBridge);
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        World world = event.getEntity().getWorld();
        BridgeGame game = pintoBridge.getGame(world.getName());
        if (game == null) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE) {
                // Cancel if player shouldn't be able to take damage by gamemode
                event.setCancelled(true);
                return;
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean doFallDamage =
                    pintoBridge
                            .configLoader
                            .getGamerules(world.getName())
                            .getOrDefault("fallDamage", false);
            event.setCancelled(!doFallDamage);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            World world = player.getWorld();
            BridgeGame game = pintoBridge.getGame(world.getName());
            if (game != null) {
                event.setCancelled(true);
                player.setSaturation(1);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BridgeGame game = pintoBridge.getGame(player.getWorld().getName());

        if (game == null) return;

        game.onDeath(player, event);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        BridgeGame game = pintoBridge.getGame(player.getWorld().getName());
        if (game != null) {
            event.setCancelled(!game.canDrop());
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BridgeGame game = pintoBridge.getGame(player.getWorld().getName());
        if (game != null) {
            event.setJoinMessage(null);
            game.join(player);
        } else pintoBridge.debug("Game is null: " + player.getWorld().getName());
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        BridgeGame game = pintoBridge.getGame(world.getName());
        if (game != null) {
            try {
                game.leave(player, world);
            } catch (IllegalPluginAccessException ignored) {
            }
            pintoBridge.getLogger().warning("Player quit while in game: " + game.getName());
            event.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        BridgeGame game = pintoBridge.getGame(event.getPlayer().getWorld().getName());
        if (game == null) return;
        Player player = event.getPlayer();
        double deathHeight = game.getDeathHeight();
        if (player.getLocation().getY() <= deathHeight) {
            if (!game.isPlaying()) player.teleport(player.getWorld().getSpawnLocation());
            // Dead players are held below the death plane until they respawn; killing them
            // again there would loop the death forever.
            else if (player.getGameMode() == GameMode.SURVIVAL
                    || player.getGameMode() == GameMode.ADVENTURE) player.setHealth(0);
        }
    }

    @EventHandler
    public void onItemDamaged(PlayerItemDamageEvent event) {
        BridgeGame game = pintoBridge.getGame(event.getPlayer().getWorld().getName());
        if (game != null) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event) {
        BridgeGame game = pintoBridge.getGame(event.getEntity().getWorld().getName());
        if (game == null) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = null;

        // Check if the attacker is a player directly
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
            // Check if the attacker shot a projectile (Fireball, Arrow, Trident, etc.)
        } else if (event.getDamager() instanceof Projectile
                && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) event.getDamager()).getShooter();
        }

        if (attacker == null) return;

        // Fetch their teams from the game's scoreboard
        Scoreboard board = game.getScoreboard();
        Team victimTeam = board.getEntryTeam(victim.getName());
        Team attackerTeam = board.getEntryTeam(attacker.getName());

        // Cancel damage if they are in the same team
        if (victimTeam != null && victimTeam.equals(attackerTeam)) {
            event.setCancelled(true);
        }
    }
}
