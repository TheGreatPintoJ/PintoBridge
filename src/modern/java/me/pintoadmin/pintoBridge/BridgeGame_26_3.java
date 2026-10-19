package me.pintoadmin.pintoBridge;

import static me.pintoadmin.pintoBridge.BridgeGame.PlayerState.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.enchantments.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

public class BridgeGame_26_3 extends BridgeGame {
    private final PintoBridge pintoBridge;
    private final FileConfiguration config;
    private final ConfigurationSection gameCfg;
    private final String name;
    private World world;
    private boolean playing = false;
    private boolean ended = false;
    private List<Map<?, ?>> teamsMaps = new ArrayList<>();
    private List<Team> teams = new ArrayList<>();
    private Scoreboard scoreboard;
    private boolean enabled;
    private List<ItemStack> startingItems = new ArrayList<>();
    private int arrowSlot;
    private List<Material> breakables = new ArrayList<>();
    private Vector facingLocation;
    private Vector endLocation;
    private String endWorldName;
    private int deathHeight;
    private boolean canDrop;
    private int playersPerTeam;
    private Map<String, Vector> teamSpawns = new HashMap<>();
    private Map<String, Vector[]> teamPortals = new HashMap<>();
    private Map<String, Vector[]> teamCages = new HashMap<>();
    private static final int MAX_BLOCK_CHANGES = 10000; // Prevent unbounded growth
    private final Map<String, Boolean> oldGamerules = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldInventories = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    private final Map<String, Integer> teamWins = new HashMap<>();

    private final List<BlockState> cageBlocks = new LinkedList<>();

    private String boardTitle;
    private String boardIP;
    private String boardTitleColor;
    private String boardIPColor;

    public BridgeGame_26_3(PintoBridge pintoBridge, String name) {
        this.pintoBridge = pintoBridge;
        this.config = pintoBridge.getConfig();
        this.gameCfg = config.getConfigurationSection(name);
        this.name = name;

        init();
    }

    // Game functions
    @Override
    public void init() {
        teamsMaps = gameCfg.getMapList("teams");
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        enabled = gameCfg.getBoolean("enabled");
        List<String> startingItemsStr = gameCfg.getStringList("startingitems");
        List<String> breakablesStr = gameCfg.getStringList("canbreak");
        arrowSlot = gameCfg.getInt("arrowslot");
        deathHeight = gameCfg.getInt("deathheight", -64);
        canDrop = gameCfg.getBoolean("candropitems", false);
        playersPerTeam = gameCfg.getInt("playersperteam", 1);

        ConfigurationSection endSection = gameCfg.getConfigurationSection("endlocation");
        endWorldName = endSection != null ? endSection.getString("world", "world") : "world";
        endLocation = getVector("endlocation");

        ConfigurationSection boardSection = gameCfg.getConfigurationSection("scoreboard");
        if (boardSection == null) {
            pintoBridge
                    .getLogger()
                    .severe("No 'scoreboard' section for game " + name + " - using defaults");
            boardSection = config.createSection(name + ".scoreboard");
        }
        boardTitle = boardSection.getString("title", "SERVER");
        boardIP = boardSection.getString("ip", "");
        boardTitleColor = boardSection.getString("titlecolor", "AQUA");
        boardIPColor = boardSection.getString("ipcolor", "YELLOW");

        if (teamsMaps != null) {
            for (Map<?, ?> map : teamsMaps) {
                Map<String, Object> castedMap = (Map<String, Object>) map;
                String teamColorName = (String) castedMap.get("color");

                // Spawn location
                Map<String, Double> spawnLocMap =
                        (Map<String, Double>) castedMap.get("spawnlocation");
                if (spawnLocMap != null) {
                    double x = spawnLocMap.getOrDefault("x", 0d);
                    double y = spawnLocMap.getOrDefault("y", 0d);
                    double z = spawnLocMap.getOrDefault("z", 0d);
                    teamSpawns.put(teamColorName, new Vector(x, y, z));
                }

                // Register portals
                Map<String, Double> portalLocMap = (Map<String, Double>) castedMap.get("portal");
                if (portalLocMap != null) {
                    double x1 = portalLocMap.getOrDefault("x1", 0d);
                    double y1 = portalLocMap.getOrDefault("y1", 0d);
                    double z1 = portalLocMap.getOrDefault("z1", 0d);
                    double x2 = portalLocMap.getOrDefault("x2", 0d);
                    double y2 = portalLocMap.getOrDefault("y2", 0d);
                    double z2 = portalLocMap.getOrDefault("z2", 0d);
                    teamPortals.put(
                            teamColorName,
                            new Vector[] {new Vector(x1, y1, z1), new Vector(x2, y2, z2)});
                }
                // Register cages
                Map<String, Double> cageLocMap = (Map<String, Double>) castedMap.get("cage");
                if (cageLocMap != null) {
                    double x1 = cageLocMap.getOrDefault("x1", 0d);
                    double y1 = cageLocMap.getOrDefault("y1", 0d);
                    double z1 = cageLocMap.getOrDefault("z1", 0d);
                    double x2 = cageLocMap.getOrDefault("x2", 0d);
                    double y2 = cageLocMap.getOrDefault("y2", 0d);
                    double z2 = cageLocMap.getOrDefault("z2", 0d);
                    teamCages.put(
                            teamColorName,
                            new Vector[] {new Vector(x1, y1, z1), new Vector(x2, y2, z2)});
                }
            }
            // pintoBridge.getLogger().info("Spawns for %s: %s".formatted(name, teamSpawns));
            // pintoBridge.getLogger().warning("[DEBUG] teamsStr after init: " + teamsStr);
        }

        teams.clear();
        for (String teamStr : getTeams()) {
            Team team = scoreboard.registerNewTeam(teamStr);
            team.setColor(teamColor(teamStr));
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            teams.add(team);
            teamWins.put(teamStr, 0);
        }

        // Setup sidebar
        scoreboard.registerNewObjective(
                "sidebar", Criteria.DUMMY, teamColor(boardTitleColor) + boardTitle);
        Objective sidebar = scoreboard.getObjective("sidebar");
        sidebar.getScore(teamColor(boardIPColor) + boardIP).setScore(-2);
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.getScore("").setScore(teams.size());
        sidebar.getScore(" ").setScore(-1);

        int loops = 0;
        for (String teamStr : getTeams()) {
            ChatColor chatcolor = teamColor(teamStr);
            sidebar.getScore(
                            chatcolor
                                    + teamStr.substring(0, 1).toUpperCase()
                                    + ChatColor.RESET
                                    + " "
                                    + capitalize(teamStr))
                    .setScore(loops++);
        }

        if (!startingItemsStr.isEmpty()) {
            for (String itemStr : startingItemsStr) {
                String[] itemSplit = itemStr.split(":");
                ItemStack item = new ItemStack(Material.AIR);
                Material mat = Material.matchMaterial(itemSplit[0]);
                if (mat != null) {
                    item = new ItemStack(mat);
                } else {
                    item =
                            switch (itemSplit[0].toLowerCase()) {
                                case "wool" -> new ItemStack(Material.WHITE_WOOL);
                                case "terracotta" -> new ItemStack(Material.TERRACOTTA);
                                default -> item;
                            };
                }
                if (itemSplit.length >= 2)
                    item = addAmountItem(item, Integer.parseInt(itemSplit[1]));
                if (itemSplit.length == 3) {
                    String enchants = itemSplit[2].replace("[", "");
                    enchants = enchants.replace("]", "");
                    String[] allEnchants = enchants.split(",");
                    for (String enchant : allEnchants) {
                        String[] splitEnchant = enchant.split("=");
                        Enchantment ench = Enchantment.getByName(splitEnchant[0]);
                        if (ench != null) {
                            item =
                                    enchantItem(
                                            item, Map.of(ench, Integer.parseInt(splitEnchant[1])));
                        }
                    }
                }
                startingItems.add(item);
            }
        }

        if (!breakablesStr.isEmpty()) {
            for (String matStr : breakablesStr) {
                if (matStr.equalsIgnoreCase("terracottas")) {
                    for (DyeColor dye : DyeColor.values()) {
                        Material mat = Material.getMaterial(dye.name() + "_TERRACOTTA");
                        if (mat != null) breakables.add(mat);
                    }
                } else {
                    Material mat = Material.matchMaterial(matStr);
                    if (mat != null) {
                        breakables.add(mat);
                    } else pintoBridge.getLogger().warning("Could not find material: " + matStr);
                }
            }
        }
    }

    @Override
    public void start() {
        playerStates.clear();

        List<Player> players = world.getPlayers();
        pintoBridge.getLogger().info("Starting game: %s".formatted(world.getName()));
        playing = true;
        // Remove all item entities
        List<Entity> entities = world.getEntities();
        for (Entity entity : entities) {
            if (entity.getType() == EntityType.ITEM) {
                entity.remove();
            }
        }

        for (Map.Entry<String, Boolean> entry :
                pintoBridge.configLoader.getGamerules(this.world.getName()).entrySet()) {
            GameRule<?> raw = GameRule.getByName(entry.getKey());
            if (raw == null || raw.getType() != Boolean.class) {
                pintoBridge
                        .getLogger()
                        .warning(
                                "Ignoring unknown/non-boolean gamerule in config: "
                                        + entry.getKey());
                continue;
            }
            @SuppressWarnings("unchecked")
            GameRule<Boolean> rule = (GameRule<Boolean>) raw;
            Boolean previous = world.getGameRuleValue(rule);
            if (previous != null) oldGamerules.put(entry.getKey(), previous);
            world.setGameRule(rule, entry.getValue());
        }
        if (oldGamerules.isEmpty()) pintoBridge.debug("oldGamerules is empty");

        List<String> teams = getTeams();

        updateScoreboard();

        // Fetch teams
        int teamIndex = 0;
        Map<String, List<Player>> teamPlayers = new HashMap<>();
        for (String teamStr : teams) {
            teamPlayers.put(teamStr, new ArrayList<>());
        }
        // Assign teams
        for (Player player : players) {
            String teamStr = teams.get(teamIndex % teams.size());
            Team team = scoreboard.getTeam(teamStr);
            if (team != null) {
                team.addEntry(player.getName());
                teamPlayers.get(teamStr).add(player);
            }
            teamIndex++;
        }

        // Teleport and give items to all players on each team
        for (String teamStr : teams) {
            Vector spawn = getSpawnLoc(teamStr);
            if (spawn == null) continue;
            Vector facing = getFacing(spawn, spawn.getY());
            Vector direction = facing.clone().subtract(spawn).setY(0).normalize();
            List<Player> teamPlayerList = teamPlayers.get(teamStr);
            if (teamPlayerList == null) continue;
            for (Player thisPlayer : teamPlayerList) {
                final UUID playerId = thisPlayer.getUniqueId();
                List<PlayerState> state = playerStates.getOrDefault(playerId, new ArrayList<>());
                state.addAll(List.of(PLAYING, NOBREAKRES));
                playerStates.put(playerId, state);

                Location tpLocation = spawn.toLocation(world);
                tpLocation.setDirection(direction);
                thisPlayer.teleport(tpLocation);

                Team team = scoreboard.getTeam(teamStr);
                if (team != null) {
                    team.addEntry(thisPlayer.getName());
                    pintoBridge
                            .getLogger()
                            .info(
                                    "%s is now on team %s"
                                            .formatted(thisPlayer.getName(), team.getName()));
                } else {
                    pintoBridge.getLogger().warning("Could not find team with name: " + teamStr);
                }

                giveItems(thisPlayer, teamStr);
                thisPlayer.setFoodLevel(20);
                thisPlayer.setSaturation(5);
                thisPlayer.setSaturatedRegenRate(30);
                thisPlayer.setGameMode(GameMode.SURVIVAL);

                if (!getScoreboard()
                        .getObjective("health")
                        .getScore(thisPlayer.getName())
                        .isScoreSet()) {
                    thisPlayer.damage(0.000001);
                }

                runningTasks.add(
                        new BukkitRunnable() {
                            int countdown = 5;

                            @Override
                            public void run() {
                                if (countdown > 0) {
                                    thisPlayer.sendTitle(
                                            ChatColor.GOLD + String.valueOf(countdown),
                                            "",
                                            0,
                                            20,
                                            0);
                                    thisPlayer.playSound(
                                            thisPlayer, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.5f);
                                    countdown--;
                                } else {
                                    thisPlayer.sendTitle("", "", 0, 1, 0); // Clear title
                                    thisPlayer.playSound(
                                            thisPlayer, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1f);
                                    List<PlayerState> state =
                                            playerStates.getOrDefault(playerId, new ArrayList<>());
                                    state.removeAll(List.of(NOPLACE, NOBREAK));
                                    playerStates.put(playerId, state);
                                    // Remove cage
                                    removeCage(thisPlayer);
                                    this.cancel();
                                }
                            }
                        }.runTaskTimer(pintoBridge, 0L, 20L)); // 20L = 1 second
            }
        }
    }

    @Override
    public void end(boolean forceEnd) {
        pintoBridge.getLogger().info("Ended game: %s".formatted(world.getName()));
        playing = false;
        // Remove all item entities
        List<Entity> entities = world.getEntities();
        for (Entity entity : entities) {
            if (entity.getType() == EntityType.ITEM) {
                entity.remove();
            }
        }
        // Find living teams. The game's scoreboard only ever holds this game's teams.
        List<Team> gameTeams = new ArrayList<>(scoreboard.getTeams());
        List<Team> existingTeams = getExistingTeams();
        // Remove duplicates
        Set<Team> existingTeamsSet = new HashSet<>(existingTeams);
        Team winningTeam = getWinningTeam();

        // Losing teams: show GAME OVER, play sound, remove from team, remove tag
        for (Team team : gameTeams) {
            if (existingTeamsSet.contains(team) && team != winningTeam) {
                for (String entry : team.getEntries()) {
                    Player thisPlayer = Bukkit.getPlayerExact(entry);
                    if (thisPlayer != null) {
                        if (!playerStates.get(thisPlayer.getUniqueId()).contains(LEFT)) {
                            thisPlayer.sendTitle(ChatColor.RED + "GAME OVER", "", 0, 60, 0);
                            thisPlayer.playSound(thisPlayer, Sound.ITEM_SHIELD_BREAK, 3f, 1f);
                        } else {
                            List<PlayerState> states = playerStates.get(thisPlayer.getUniqueId());
                            states.remove(LEFT);
                            playerStates.put(thisPlayer.getUniqueId(), states);
                        }
                        // playingPlayers.remove(thisPlayer);
                        // Clear title after a short delay to ensure it shows
                        if (!forceEnd)
                            runningTasks.add(
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            thisPlayer.sendTitle("", "", 0, 1, 0);
                                        }
                                    }.runTaskLater(pintoBridge, 65L));
                    } else pintoBridge.getLogger().warning("Loser is null");
                }
            }
        }
        // Winning teams: show VICTORY, play sound, remove from team, remove tag
        for (String entry : winningTeam.getEntries()) {
            Player thisPlayer = Bukkit.getPlayerExact(entry);
            if (thisPlayer != null) {
                if (!playerStates.get(thisPlayer.getUniqueId()).contains(LEFT)) {
                    thisPlayer.sendTitle(ChatColor.GOLD + "VICTORY", "", 0, 60, 0);
                    thisPlayer.playSound(thisPlayer, Sound.ITEM_GOAT_HORN_SOUND_1, 3f, 1f);
                } else {
                    List<PlayerState> states = playerStates.get(thisPlayer.getUniqueId());
                    states.remove(LEFT);
                    playerStates.put(thisPlayer.getUniqueId(), states);
                }
                // playingPlayers.remove(thisPlayer);
                if (!forceEnd)
                    runningTasks.add(
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    thisPlayer.sendTitle("", "", 0, 1, 0);
                                }
                            }.runTaskLater(pintoBridge, 65L));
            } else pintoBridge.getLogger().warning("Winner is null");
        }

        // All players: remove tags, clear inventory, reset health, remove from team, set gamemode,
        // clear title
        for (Player player : world.getPlayers()) {
            // playingPlayers.remove(player);
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                player.setHealth(attr.getValue());
            }
        }
        // Empty Teams
        for (Team team : teams) {
            team.getEntries().forEach(team::removeEntry);
        }

        BukkitRunnable cleanupTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player thisPlayer : world.getPlayers()) {
                            thisPlayer.getInventory().clear();
                            Location endLocation = getEndLocation();
                            thisPlayer.teleport(endLocation);
                            thisPlayer.setHealth(
                                    thisPlayer.getAttribute(Attribute.MAX_HEALTH).getValue());
                            List<PlayerState> states = playerStates.get(thisPlayer.getUniqueId());
                            states.remove(SPECTATING);
                            playerStates.put(thisPlayer.getUniqueId(), states);
                            thisPlayer.setGameMode(GameMode.SURVIVAL);

                            restoreInventory(thisPlayer);
                        }
                        undoBlockChanges();
                        replaceCages();
                        blockChanges.clear();
                        cageBlocks.clear();

                        // Clear all playerlists
                        playerStates.clear();
                        oldInventories.clear();

                        for (Entity entity : world.getEntities()) {
                            if (entity.getType() == EntityType.ITEM
                                    || entity.getType() == EntityType.TNT
                                    || entity.getType() == EntityType.FIREBALL) entity.remove();
                        }

                        ended = false;
                    }
                };

        ended = true;
        if (forceEnd) {
            cleanupTask.run();
            for (BukkitTask task : runningTasks) {
                if (task != null) task.cancel();
            }
            runningTasks.clear();
        } else runningTasks.add(cleanupTask.runTaskLater(pintoBridge, 40L));

        for (Map.Entry<String, Boolean> entry : oldGamerules.entrySet()) {
            @SuppressWarnings("unchecked")
            GameRule<Boolean> rule = (GameRule<Boolean>) GameRule.getByName(entry.getKey());
            if (rule != null) {
                world.setGameRule(rule, entry.getValue());
            }
        }
    }

    @Override
    public void join(Player player) {
        boolean rejoining = false;
        // Remove from previous teams and clear tags/inventory if not in a game
        if (world == null) {
            world = Bukkit.getWorld(name);
            if (world == null) {
                pintoBridge.getLogger().severe("World is null - tried %s".formatted(name));
            }
        }

        player.setScoreboard(scoreboard);

        // Rejoining check
        if (playing) {
            // if player in leftplayers then spawnplayer, add to playingplayers, remove from
            // leftplayers
            if (playerStates.getOrDefault(player.getUniqueId(), new ArrayList<>()).contains(LEFT)) {
                rejoining = true;
                List<PlayerState> state =
                        playerStates.getOrDefault(player.getUniqueId(), new ArrayList<>());
                state.add(PLAYING);
                state.remove(LEFT);
                playerStates.put(player.getUniqueId(), state);
            } else {
                setPlayerToSpectate(player);
                return;
            }
        }

        oldInventories.put(player.getUniqueId(), player.getInventory().getContents());
        player.getInventory().clear();

        List<String> teams = getTeams();

        List<World> worlds = Bukkit.getWorlds();
        for (World world : worlds) {
            if (world != Bukkit.getWorld(name)) {
                List<Player> players = world.getPlayers();
                for (Player thisPlayer : players) {
                    player.hidePlayer(pintoBridge, thisPlayer);
                    thisPlayer.hidePlayer(pintoBridge, player);
                }
            }
        }

        World playerWorld = player.getWorld();
        for (Player thisPlayer : playerWorld.getPlayers()) {
            player.showPlayer(pintoBridge, thisPlayer);
            thisPlayer.showPlayer(pintoBridge, player);
        }

        if (!teams.isEmpty()) {
            // Move join message logic here
            for (Player oldPlayer : player.getWorld().getPlayers()) {
                if (!rejoining)
                    oldPlayer.sendMessage(
                            player.getName()
                                    + ChatColor.YELLOW
                                    + " has joined the game "
                                    + ChatColor.AQUA
                                    + "(%s/%s)"
                                            .formatted(
                                                    player.getWorld().getPlayers().size(),
                                                    teams.size() * playersPerTeam));
                else oldPlayer.sendMessage(player.getName() + ChatColor.YELLOW + " has rejoined");
            }
            if (!rejoining) {
                List<PlayerState> state =
                        playerStates.getOrDefault(player.getUniqueId(), new ArrayList<>());
                state.addAll(List.of(NOPLACE, NOBREAK));
                playerStates.put(player.getUniqueId(), state);
            }
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        } else pintoBridge.debug("Teams is empty");

        if (world.getPlayers().size() >= teams.size() * playersPerTeam && !rejoining) {
            // All teams are full, start game
            runningTasks.add(
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            start();
                        }
                    }.runTaskLater(pintoBridge, 5L));
        } else if (rejoining) {
            // Start this player
            Team rejoiningTeam = scoreboard.getEntryTeam(player.getName());
            String teamStr = rejoiningTeam.getName();
            Vector spawn = getSpawnLoc(teamStr);
            Location tpLocation = spawn.toLocation(world);
            tpLocation.setDirection(getFacing(spawn, spawn.getY()));
            player.teleport(tpLocation);

            pintoBridge
                    .getLogger()
                    .info(
                            "%s is now on team %s"
                                    .formatted(player.getName(), rejoiningTeam.getName()));

            giveItems(player, teamStr);
            player.setFoodLevel(20);
            player.setSaturation(5);
            player.setSaturatedRegenRate(30);
            player.setGameMode(GameMode.SURVIVAL);

            if (!getScoreboard().getObjective("health").getScore(player.getName()).isScoreSet()) {
                player.damage(0.000001);
            }
        }
    }

    @Override
    public void leave(Player player, World toWorld) {
        UUID playerId = player.getUniqueId();
        player.getInventory().clear();
        // Remove from previous teams and tags, clear inventory, reset health
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());

        // Show all players in the new world to the player, and vice versa
        for (Player other : toWorld.getPlayers()) {
            player.showPlayer(pintoBridge, other);
            other.showPlayer(pintoBridge, player);
        }

        // Leave game messages
        for (Player oldPlayer : world.getPlayers()) {
            if (ended) continue;
            if (!playing) {
                oldPlayer.sendMessage(
                        player.getName()
                                + ChatColor.YELLOW
                                + " has left the game "
                                + ChatColor.AQUA
                                + String.format(
                                        "(%s/%s)", world.getPlayers().size(), teams.size()));
            } else oldPlayer.sendMessage(player.getName() + ChatColor.YELLOW + " has left");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (playing
                        && playerStates
                                .getOrDefault(playerId, new ArrayList<>())
                                .contains(PLAYING)) {
                    if (getExistingTeams().size() <= 1) end(false);
                } else {
                    restoreInventory(player);
                }

                List<PlayerState> states = playerStates.getOrDefault(playerId, new ArrayList<>());
                states.clear();
                states.add(LEFT);
                playerStates.put(playerId, states);
            }
        }.runTask(
                pintoBridge); // Run after the player moves worlds (so that getExistingTeams returns
        // correctly)
    }

    @Override
    public void score(Player player) {
        String playerTeam =
                teams.stream()
                        .filter(t -> t.getEntries().contains(player.getName()))
                        .toList()
                        .getFirst()
                        .getName();
        int newScore = teamWins.getOrDefault(playerTeam, 0) + 1;
        if (newScore >= 5) end(false);
        teamWins.put(playerTeam, newScore);
        updateScoreboard();

        // Trigger round end, next begin
        replaceCages();
        List<String> teams = getTeams();
        for (String teamStr : teams) {
            Vector spawn = getSpawnLoc(teamStr);
            if (spawn == null) continue;
            Vector facing = getFacing(spawn, spawn.getY());
            Vector direction = facing.clone().subtract(spawn).setY(0).normalize();
            List<Player> teamPlayerList =
                    scoreboard.getTeam(teamStr).getEntries().stream()
                            .map(Bukkit::getPlayerExact)
                            .toList();
            for (Player thisPlayer : teamPlayerList) {
                if (thisPlayer == null) continue;
                final UUID playerId = thisPlayer.getUniqueId();
                List<PlayerState> state = playerStates.getOrDefault(playerId, new ArrayList<>());
                state.addAll(List.of(PLAYING, NOBREAKRES));
                playerStates.put(playerId, state);

                Location tpLocation = spawn.toLocation(world);
                tpLocation.setDirection(direction);
                thisPlayer.teleport(tpLocation);

                giveItems(thisPlayer, teamStr);
                thisPlayer.setFoodLevel(20);
                thisPlayer.setSaturation(5);
                thisPlayer.setSaturatedRegenRate(30);
                thisPlayer.setGameMode(GameMode.SURVIVAL);

                runningTasks.add(
                        new BukkitRunnable() {
                            int countdown = 3;

                            @Override
                            public void run() {
                                if (countdown > 0) {
                                    thisPlayer.sendTitle(
                                            ChatColor.GOLD + String.valueOf(countdown),
                                            "",
                                            0,
                                            20,
                                            0);
                                    thisPlayer.playSound(
                                            thisPlayer, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.5f);
                                    countdown--;
                                } else {
                                    thisPlayer.sendTitle("", "", 0, 1, 0); // Clear title
                                    thisPlayer.playSound(
                                            thisPlayer, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1f);
                                    List<PlayerState> state =
                                            playerStates.getOrDefault(playerId, new ArrayList<>());
                                    state.removeAll(List.of(NOPLACE, NOBREAK));
                                    playerStates.put(playerId, state);
                                    // Remove cage
                                    removeCage(thisPlayer);
                                    this.cancel();
                                }
                            }
                        }.runTaskTimer(pintoBridge, 0L, 20L)); // 20L = 1 second
            }
        }
    }

    @Override
    public void onDeath(Player player, PlayerDeathEvent event) {
        World world = player.getWorld();

        if (!playing) {
            player.setRespawnLocation(player.getWorld().getSpawnLocation());
            event.setDeathMessage(null);
            player.spigot().respawn();
            return;
        }

        String deathMessage = event.getDeathMessage();
        event.setDeathMessage(null);
        for (Player thisPlayer : world.getPlayers()) {
            thisPlayer.sendMessage(deathMessage);
        }
        event.getDrops().clear();

        Team playerTeam = scoreboard.getEntryTeam(player.getName());
        if (playerTeam == null) {
            pintoBridge.debug("Player not on a team: " + player.getName());
            return;
        }
        String teamColor = playerTeam.getName();
        Vector teamSpawn = getSpawnLoc(teamColor);
        if (teamSpawn == null) {
            pintoBridge.getLogger().warning("Team spawn is null: " + teamColor);
            return;
        }
        Vector teamFacing = getFacing(teamSpawn, teamSpawn.getY());

        event.getDrops().clear();
        event.setDroppedExp(0);
        if (!playerStates.getOrDefault(player.getUniqueId(), new ArrayList<>()).contains(PLAYING)) {
            event.setDeathMessage(null);
            return;
        }

        Location tpLoc = teamSpawn.toLocation(world);
        tpLoc.setDirection(teamFacing);
        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
                player.teleport(tpLoc);
                giveItems(player, teamColor);
            }
        }.runTask(pintoBridge);
    }

    @Override
    public void entersPortal(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) {
            pintoBridge.getLogger().severe("Could not find team for player: " + player.getName());
            return;
        }
        Vector[] portalLocs = teamPortals.get(team.getName());
        Vector minVec =
                new Vector(
                        Math.min(portalLocs[0].getX(), portalLocs[1].getX()),
                        Math.min(portalLocs[0].getY(), portalLocs[1].getY()) - 1,
                        Math.min(portalLocs[0].getZ(), portalLocs[1].getZ()));
        Vector maxVec =
                new Vector(
                        Math.max(portalLocs[0].getX(), portalLocs[1].getX()),
                        Math.max(portalLocs[0].getY(), portalLocs[1].getY()),
                        Math.max(portalLocs[0].getZ(), portalLocs[1].getZ()));
        pintoBridge.debug(
                "Player location:",
                player.getLocation().toVector().toString(),
                "- portal min -",
                minVec.toString(),
                "- portal max -",
                maxVec.toString());
        if (!player.getLocation()
                .add(0, 0.5, 0)
                .toVector()
                .isInAABB(minVec, maxVec)) { // Not the player's portal
            score(player);
        }
    }

    @Override
    public void arrowShoot(Player player) {
        runningTasks.add(
                new BukkitRunnable() {
                    int countdown = 3;

                    @Override
                    public void run() {
                        if (countdown > 0) {
                            player.setLevel(countdown);
                            countdown--;
                        } else {
                            player.getInventory()
                                    .setItem(arrowSlot, new ItemStack(Material.ARROW, 1));
                            this.cancel();
                        }
                    }
                }.runTaskTimer(pintoBridge, 0L, 20L)); // 20L = 1 second
    }

    @Override
    public String getName() {
        return name;
    }

    private void removeCage(Player thisPlayer) {
        Vector startCorner;
        Vector endCorner;
        try {
            Team playerTeam = getPlayerTeam(thisPlayer);
            startCorner = teamCages.get(playerTeam.getName())[0];
            endCorner = teamCages.get(playerTeam.getName())[1];
        } catch (NullPointerException e) {
            pintoBridge.getLogger().severe("Could not find player's team: " + thisPlayer.getName());
            throw new RuntimeException(e);
        }
        if (startCorner != null && endCorner != null) {
            // Remove cage and add to blockStates
            int startX = (int) Math.min(startCorner.getX(), endCorner.getX());
            int endX = (int) Math.max(startCorner.getX(), endCorner.getX());
            int startY = (int) Math.min(startCorner.getY(), endCorner.getY());
            int endY = (int) Math.max(startCorner.getY(), endCorner.getY());
            int startZ = (int) Math.min(startCorner.getZ(), endCorner.getZ());
            int endZ = (int) Math.max(startCorner.getZ(), endCorner.getZ());

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    for (int z = startZ; z <= endZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType().isAir()) continue;
                        cageBlocks.add(block.getState());
                        block.setType(Material.AIR, false);
                    }
                }
            }
        } else
            pintoBridge
                    .getLogger()
                    .severe(
                            "Fix your config! Could not find cage for team: "
                                    + getPlayerTeam(thisPlayer));
    }

    private void replaceCages() {
        for (BlockState state : cageBlocks) {
            state.update(true);
        }
    }

    private void undoBlockChanges() {
        LinkedList<BlockState> changes = new LinkedList<>();
        for (int i = 1; i <= blockChanges.size(); i++) { // Workaround reverse list
            changes.add(blockChanges.get(blockChanges.size() - i));
        }

        for (BlockState entry : changes) {
            entry.update(true, false);

            blockChanges.remove(entry);
        }
    }

    @Override
    public void giveItems(Player player, String teamStr) {
        int counter = 0;
        for (ItemStack item : startingItems) {
            ItemStack toGive = item;
            if (item.getType() == Material.TERRACOTTA) {
                Color color = parseColor(teamStr);
                Material woolMat = Material.TERRACOTTA;
                if (color != null) {
                    // Map Bukkit Color to DyeColor
                    DyeColor dyeColor = getDyeColor(color);
                    woolMat = Material.getMaterial(dyeColor.name() + "_TERRACOTTA");
                    if (woolMat == null) woolMat = Material.TERRACOTTA;
                }
                toGive = new ItemStack(woolMat, item.getAmount());
            } else if (item.getType().name().contains("LEATHER_")) {
                Color color = parseColor(teamStr);
                if (color != null && item.getItemMeta() instanceof LeatherArmorMeta meta) {
                    meta.setColor(color);
                    ItemStack colored = item.clone();
                    colored.setItemMeta(meta);
                    toGive = colored;
                }
            }
            if (toGive.getType().toString().contains("HELMET")) {
                player.getInventory().setHelmet(toGive.clone());
            } else if (toGive.getType().toString().contains("CHESTPLATE")) {
                player.getInventory().setChestplate(toGive.clone());
            } else if (toGive.getType().toString().contains("LEGGINGS")) {
                player.getInventory().setLeggings(toGive.clone());
            } else if (toGive.getType().toString().contains("BOOTS")) {
                player.getInventory().setBoots(toGive.clone());
            } else {
                player.getInventory().setItem(counter, toGive.clone());
            }
            counter++;
        }
        player.getInventory().setItem(arrowSlot, new ItemStack(Material.ARROW, 1));
    }

    @Override
    public void giveItems(Team team) {
        if (team == null) return;
        for (String entry : team.getEntries()) {
            Player player = Bukkit.getPlayerExact(entry);
            if (player != null) {
                giveItems(player, team.getName());
            }
        }
    }

    @Override
    public void updateScoreboard() {
        List<String> teams = getTeams();
        Objective sidebar = scoreboard.getObjective("sidebar");
        if (sidebar != null) sidebar.unregister();
        scoreboard.registerNewObjective(
                "sidebar",
                Criteria.DUMMY,
                "%s%s".formatted(teamColor(boardTitleColor), boardTitle));
        sidebar = scoreboard.getObjective("sidebar");
        sidebar.getScore(teamColor(boardIPColor) + boardIP).setScore(-2);
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        sidebar.getScore("").setScore(teams.size());
        sidebar.getScore(" ").setScore(-1);
        int loops = 0;

        // ⬤
        for (String teamStr : teams) {
            ChatColor chatcolor = teamColor(teamStr);
            String firstLetter = String.valueOf(teamStr.charAt(0)).toUpperCase();

            String winsStr = "";
            int wins = teamWins.get(teamStr);
            for (int i = 0; i < 5; i++) {
                if (i < wins) winsStr += chatcolor + "⬤";
                else winsStr += ChatColor.RESET + "⬤";
            }

            sidebar.getScore(
                            chatcolor
                                    + firstLetter
                                    + ChatColor.RESET
                                    + " %s: %s".formatted(capitalize(teamStr), winsStr))
                    .setScore(loops);
            loops++;
        }

        Objective health = scoreboard.getObjective("health");
        if (health == null) {
            scoreboard.registerNewObjective("health", Criteria.HEALTH, ChatColor.RED + "❤");
            health = scoreboard.getObjective("health");
            health.setDisplaySlot(DisplaySlot.BELOW_NAME);
            pintoBridge.getLogger().info("Created 'health' scoreboard");
        }
        Objective healthtab = scoreboard.getObjective("healthtab");
        if (healthtab == null) {
            scoreboard.registerNewObjective("healthtab", Criteria.HEALTH, ChatColor.RED + "❤");
            healthtab = scoreboard.getObjective("healthtab");
            healthtab.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            pintoBridge.getLogger().info("Created 'healthtab' scoreboard");
        }
    }

    @Override
    public void setPlayerToSpectate(Player player) {
        // TODO: fill out
    }

    @Override
    public void addBlockChange(BlockState beforeBlockState) {
        blockChanges.add(beforeBlockState);
    }

    private void restoreInventory(Player player) {
        ItemStack[] saved = oldInventories.remove(player.getUniqueId());
        if (saved == null) return;
        player.getInventory().clear();
        player.getInventory().setContents(saved);
    }

    // Utilities
    private ItemStack enchantItem(ItemStack input, Map<Enchantment, Integer> enchantments) {
        ItemMeta meta = input.getItemMeta();
        if (meta == null) return input;
        for (Map.Entry<Enchantment, Integer> enchantment : enchantments.entrySet()) {
            Enchantment key = enchantment.getKey();
            int lvl = enchantment.getValue();
            meta.addEnchant(key, lvl, true);
        }
        input.setItemMeta(meta);
        return input;
    }

    private ItemStack addAmountItem(ItemStack input, int amount) {
        input.setAmount(amount);
        return input;
    }

    // Getters
    private Vector getVector(String path) {
        ConfigurationSection section = gameCfg.getConfigurationSection(path);
        if (section == null) return null;
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        return new Vector(x, y, z);
    }

    @Override
    public Location getEndLocation() {
        return new Location(world, endLocation.getX(), endLocation.getY(), endLocation.getZ());
    }

    private Vector getFacing(Location currentLoc, Double yVal) {
        return getFacing(currentLoc.toVector(), yVal);
    }

    private Vector getFacing(Vector currentLoc, Double yVal) {
        ConfigurationSection facingLocation = gameCfg.getConfigurationSection("facinglocation");
        if (facingLocation == null) return null;
        double x = facingLocation.getDouble("x");
        double z = facingLocation.getDouble("z");
        return new Vector(x, yVal, z).subtract(currentLoc).normalize();
    }

    @Override
    public int getDeathHeight() {
        return deathHeight;
    }

    @Override
    public boolean canDrop() {
        return canDrop;
    }

    @Override
    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    @Override
    public List<ItemStack> getStartingItems() {
        return startingItems;
    }

    @Override
    public List<Material> getBreakables() {
        return breakables;
    }

    @Override
    public List<String> getTeams() {
        List<String> finalList = new ArrayList<>();
        teamsMaps.forEach(
                map -> {
                    Map<String, Object> castedMap = (Map<String, Object>) map;
                    String teamColorName = (String) castedMap.get("color");
                    if (teamColorName != null) {
                        finalList.add(teamColorName);
                    }
                });
        return finalList;
    }

    private List<Team> getExistingTeams() {
        List<Team> livingTeams = new ArrayList<>();
        for (Team team : scoreboard.getTeams()) {
            for (String strPlayer : team.getEntries()) {
                Player player = Bukkit.getPlayerExact(strPlayer);
                if (player != null
                        && playerStates
                                .getOrDefault(player.getUniqueId(), new ArrayList<>())
                                .contains(PLAYING)) {
                    livingTeams.add(team);
                }
            }
        }
        return livingTeams;
    }

    private Team getWinningTeam() {
        Map.Entry<String, Integer> topTeam = null;
        for (Map.Entry<String, Integer> team : teamWins.entrySet()) {
            if (topTeam == null || topTeam.getValue() < team.getValue()) topTeam = team;
        }
        return scoreboard.getTeam(topTeam.getKey());
    }

    private Team getPlayerTeam(Player player) {
        for (Team team : scoreboard.getTeams())
            if (team.getEntries().contains(player.getName())) return team;
        return null;
    }

    @Override
    public List<Team> getBukkitTeams() {
        return List.of(); // TODO: fill out
    }

    @Override
    public Vector getSpawnLoc(String team) {
        return teamSpawns.get(team.toLowerCase());
    }

    @Override
    public List<Player> getSpectators() {
        List<UUID> uuids = playerStates.keySet().stream().toList();
        return uuids.stream()
                .filter(u -> !playerStates.get(u).contains(SPECTATING))
                .map(Bukkit::getPlayer)
                .toList();
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isEnded() {
        return ended;
    }

    // Misc
    private DyeColor getDyeColor(Color color) {
        DyeColor dyeColor = DyeColor.WHITE;
        if (color.equals(Color.RED)) dyeColor = DyeColor.RED;
        else if (color.equals(Color.BLUE)) dyeColor = DyeColor.BLUE;
        else if (color.equals(Color.GREEN)) dyeColor = DyeColor.GREEN;
        else if (color.equals(Color.YELLOW)) dyeColor = DyeColor.YELLOW;
        else if (color.equals(Color.PURPLE)) dyeColor = DyeColor.PURPLE;
        else if (color.equals(Color.ORANGE)) dyeColor = DyeColor.ORANGE;
        else if (color.equals(Color.BLACK)) dyeColor = DyeColor.BLACK;
        else if (color.equals(Color.AQUA)) dyeColor = DyeColor.CYAN;
        else if (color.equals(Color.FUCHSIA)) dyeColor = DyeColor.MAGENTA;
        else if (color.equals(Color.LIME)) dyeColor = DyeColor.LIME;
        else if (color.equals(Color.MAROON)) dyeColor = DyeColor.BROWN;
        else if (color.equals(Color.NAVY)) dyeColor = DyeColor.LIGHT_BLUE;
        else if (color.equals(Color.OLIVE)) dyeColor = DyeColor.GREEN;
        else if (color.equals(Color.SILVER)) dyeColor = DyeColor.LIGHT_GRAY;
        else if (color.equals(Color.TEAL)) dyeColor = DyeColor.CYAN;
        else if (color.equals(Color.GRAY)) dyeColor = DyeColor.GRAY;
        return dyeColor;
    }

    private Color parseColor(String colorName) {
        switch (colorName.toUpperCase()) {
            case "RED":
                return Color.RED;
            case "BLUE":
                return Color.BLUE;
            case "GREEN":
                return Color.GREEN;
            case "YELLOW":
                return Color.YELLOW;
            case "PURPLE":
                return Color.PURPLE;
            case "ORANGE":
                return Color.ORANGE;
            case "WHITE":
                return Color.WHITE;
            case "BLACK":
                return Color.BLACK;
            case "AQUA":
                return Color.AQUA;
            case "FUCHSIA":
                return Color.FUCHSIA;
            case "LIME":
                return Color.LIME;
            case "MAROON":
                return Color.MAROON;
            case "NAVY":
                return Color.NAVY;
            case "OLIVE":
                return Color.OLIVE;
            case "SILVER":
                return Color.SILVER;
            case "TEAL":
                return Color.TEAL;
            case "GRAY":
                return Color.GRAY;
            default:
                return Color.WHITE;
        }
    }

    private String capitalize(String input) {
        char first = input.charAt(0);
        input = input.replaceFirst(String.valueOf(first), "%s");
        input = input.formatted(String.valueOf(first).toUpperCase());
        return input;
    }
}
