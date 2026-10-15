package me.pintoadmin.pintoFBF;

import java.util.*;
import me.pintoadmin.pintoBridge.*;
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
    private Set<UUID> spectators;
    private static final int MAX_BLOCK_CHANGES = 10000; // Prevent unbounded growth
    private final Map<String, Boolean> oldGamerules = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldInventories = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();
    private final Map<String, ItemStack[]> teamInventories = new HashMap<>();

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

    @Override
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

                // Register portals too
                Map<String, Double> portalLocMap =
                        (Map<String, Double>) castedMap.get("portal");
                if (portalLocMap != null) {
                    double x1 = portalLocMap.getOrDefault("x1", 0d);
                    double y1 = portalLocMap.getOrDefault("y1", 0d);
                    double z1 = portalLocMap.getOrDefault("z1", 0d);
                    double x2 = portalLocMap.getOrDefault("x2", 0d);
                    double y2 = portalLocMap.getOrDefault("y2", 0d);
                    double z2 = portalLocMap.getOrDefault("z2", 0d);
                    teamPortals.put(teamColorName, new Vector[]{new Vector(x1, y1, z1), new Vector(x2, y2, z2)});
                }
            }
            // pintoFBF.getLogger().info("Spawns for %s: %s".formatted(name, teamSpawns));
            // pintoFBF.getLogger().warning("[DEBUG] teamsStr after init: " + teamsStr);
        }

        teams.clear();
        for (String teamStr : getTeams()) {
            Team team = scoreboard.registerNewTeam(teamStr);
            team.setColor(teamColor(teamStr));
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            teams.add(team);
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
                    item = switch (itemSplit[0].toLowerCase()) {
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

    @Override
    public void start() {}

    @Override
    public void end(boolean forceEnd) {}

    @Override
    public void join(Player player) {}

    @Override
    public void leave(Player player) {}

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

    private Vector getVector(String path) {
        ConfigurationSection section = gameCfg.getConfigurationSection(path);
        if (section == null) return null;
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        return new Vector(x, y, z);
    }

    private Vector getFacing(Double yVal) {
        ConfigurationSection facingLocation = gameCfg.getConfigurationSection("facinglocation");
        if (facingLocation == null) return null;
        double x = facingLocation.getDouble("x");
        double z = facingLocation.getDouble("z");
        return new Vector(x, yVal, z);
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

    @Override
    public boolean isPlaying() {
        return false;
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
