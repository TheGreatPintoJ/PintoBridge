package me.pintoadmin.pintoBridge;

import static me.pintoadmin.pintoBridge.BridgeGame.PlayerState.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.enchantments.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

public class BridgeGame_26_3 extends BridgeGame {
    private final PintoBridge pintoBridge;
    private final FileConfiguration config;
    private final ConfigurationSection gameCfg;
    private final String name;
    private World world;
    private boolean playing = false;
    private boolean ended = false;
    private List<Map<?, ?>> teamsMaps;
    private List<Team> teams;
    private Scoreboard scoreboard;
    private boolean enabled;
    private List<ItemStack> startingItems;
    private List<Material> breakables;
    private Vector facingLocation;
    private Vector endLocation;
    private String endWorldName;
    private int deathHeight;
    private boolean canDrop;
    private int playersPerTeam;
    private Map<String, Vector> teamSpawns = new HashMap<>();
    private Map<String, Vector[]> teamPortals = new HashMap<>();
    private Map<String, Vector[]> teamCages = new HashMap<>();
    private Set<UUID> spectators;
    private static final int MAX_BLOCK_CHANGES = 10000; // Prevent unbounded growth
    private final Map<String, Boolean> oldGamerules = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldInventories = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    private final Map<String, Integer> teamWins = new HashMap<>();

    private String boardTitle;
    private String boardIP;
    private String boardTitleColor;
    private String boardIPColor;

    public BridgeGame_26_3(PintoBridge pintoBridge, String name) {
        this.pintoBridge = pintoBridge;
        this.config = pintoBridge.getConfig();
        this.gameCfg = config.getConfigurationSection(name);
        this.name = name;
    }

    // Game functions
    public void init() {
        playing = false;
        teamsMaps = gameCfg.getMapList("teams");
        teams = new ArrayList<>();
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        enabled = gameCfg.getBoolean("enabled");
        List<String> startingItemsStr = gameCfg.getStringList("startingitems");
        List<String> breakablesStr = gameCfg.getStringList("startingitems");
        facingLocation = getFacing(5d);
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

        startingItems = new ArrayList<>();
        breakables = new ArrayList<>();
        spectators = new LinkedHashSet<>();
        teamSpawns = new HashMap<>();

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
                    // Add all wool colors
                    for (DyeColor dye : DyeColor.values()) {
                        Material woolMat = Material.getMaterial(dye.name() + "_TERRACOTTA");
                        if (woolMat != null) breakables.add(woolMat);
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
            Vector facing =
                    (getFacingLocation() != null)
                            ? getFacingLocation().toVector().clone()
                            : new Vector(0, spawn.getY(), 0);
            Vector direction = facing.clone().subtract(spawn).setY(0).normalize();
            List<Player> teamPlayerList = teamPlayers.get(teamStr);
            if (teamPlayerList == null) continue;
            for (Player thisPlayer : teamPlayerList) {
                final UUID playerId = thisPlayer.getUniqueId();
                List<PlayerState> state = playerStates.getOrDefault(playerId, new ArrayList<>());
                state.addAll(List.of(PLAYING, NOMOVE, NOBREAKRES));
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
                                    state.removeAll(List.of(NOPLACE, NOBREAK, NOMOVE));
                                    playerStates.put(playerId, state);
                                    this.cancel();
                                }
                            }
                        }.runTaskTimer(pintoBridge, 0L, 20L)); // 20L = 1 second
            }
        }
    }

    public void end(boolean forceEnd) {}

    public void join(Player player) {}

    public void leave(Player player) {}

    public void score(Player player) {}

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
    }

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
                if (i <= wins) winsStr += chatcolor + "⬤";
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

    public Location getFacingLocation() {
        if (world == null || facingLocation == null) return null;
        return facingLocation.toLocation(world);
    }

    private Vector getFacing(Double yVal) {
        ConfigurationSection facingLocation = gameCfg.getConfigurationSection("facinglocation");
        if (facingLocation == null) return null;
        double x = facingLocation.getDouble("x");
        double z = facingLocation.getDouble("z");
        return new Vector(x, yVal, z);
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

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

    public Vector getSpawnLoc(String team) {
        return teamSpawns.get(team.toLowerCase());
    }

    public boolean isPlaying() {
        return false;
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
